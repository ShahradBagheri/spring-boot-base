package com.notshahrad.spring_boot_base.auth.email;

import com.notshahrad.spring_boot_base.auth.AccountRefreshSessionRevoker;
import com.notshahrad.spring_boot_base.auth.AccountSessionValidator;
import com.notshahrad.spring_boot_base.auth.AppUser;
import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.notshahrad.spring_boot_base.auth.email.EmailPasswordStore.Purpose.RESET;
import static com.notshahrad.spring_boot_base.auth.email.EmailPasswordStore.Purpose.SIGNUP;

@Service
@ConditionalOnProperty(prefix = "base.auth.email", name = {"enabled", "password-enabled"}, havingValue = "true")
public class EmailPasswordService implements AccountSessionValidator {

    private static final Duration SIGNUP_TTL = Duration.ofMinutes(30);
    private static final Duration RESET_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND = Duration.ofMinutes(1);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "123456789012345", "1234567890123456", "12345678901234567890", "0123456789012345",
            "passwordpassword", "passwordpasswordpassword", "password1234567890", "password12345678",
            "qwertyuiopasdfgh", "qwerty1234567890", "letmeinletmein123",
            "adminadminadmin", "welcome123456789", "correcthorsebatterystaple");

    private final SecureRandom random = new SecureRandom();
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());
    private final AppUserRepository users;
    private final EmailIdentityRepository identities;
    private final EmailPasswordStore store;
    private final ObjectProvider<PasswordBlocklist> blocklists;
    private final ObjectProvider<AccountRefreshSessionRevoker> revokers;

    public EmailPasswordService(AppUserRepository users, EmailIdentityRepository identities, JdbcTemplate jdbc,
                                ObjectProvider<PasswordBlocklist> blocklists,
                                ObjectProvider<AccountRefreshSessionRevoker> revokers) {
        this.users = users;
        this.identities = identities;
        this.store = new EmailPasswordStore(jdbc);
        this.blocklists = blocklists;
        this.revokers = revokers;
    }

    @Transactional
    public Optional<Delivery> requestSignup(LoginEmail address) {
        if (store.isAddressReserved(address)) {
            return Optional.empty();
        }
        return issue(SIGNUP, address, null, SIGNUP_TTL);
    }

    @Transactional
    public Optional<Delivery> requestReset(LoginEmail address) {
        return store.findCredential(address).flatMap(owner -> issue(RESET, address, owner, RESET_TTL));
    }

    @Transactional
    public boolean completeSignup(String token, String password) {
        var proof = proof(SIGNUP, token);
        if (proof.isEmpty() || store.isAddressReserved(proof.get().address()) || !passwordAllowed(password)) {
            return false;
        }

        LoginEmail address = proof.get().address();
        UUID userId = users.saveAndFlush(new AppUser(null, false, null)).getId();
        if (!identities.stagePending(userId, address) || !identities.markVerified(userId, address, Instant.now())) {
            throw new IllegalStateException("Unable to activate the new email identity");
        }
        store.insertPassword(userId, encoder.encode(password));
        store.consumeChallenge(digest(token));
        return true;
    }

    @Transactional
    public boolean completeReset(String token, String password) {
        var proof = proof(RESET, token);
        if (proof.isEmpty() || !passwordAllowed(password) || !store.lockActiveAccount(proof.get().ownerId())) {
            return false;
        }

        var challenge = proof.get();
        var current = store.findCredential(challenge.address());
        if (current.isEmpty() || !current.get().userId().equals(challenge.ownerId())
                || current.get().version() != challenge.version()) {
            return false;
        }

        store.replacePassword(challenge.ownerId(), encoder.encode(password));
        store.consumeChallenge(digest(token));
        revokers.orderedStream().forEach(revoker -> revoker.revokeForAccount(challenge.ownerId()));
        return true;
    }

    public Optional<EmailPasswordPrincipal> authenticate(LoginEmail address, String password) {
        if (!validLength(password)) {
            return Optional.empty();
        }
        var credential = store.findCredential(address);
        boolean matched = encoder.matches(password, credential.map(EmailPasswordStore.Credential::passwordHash).orElse(dummyHash));
        return matched ? credential.map(c -> new EmailPasswordPrincipal(c.userId(), c.version())) : Optional.empty();
    }

    @Override
    public boolean isCurrent(LocalUserPrincipal principal) {
        return !(principal instanceof EmailPasswordPrincipal email) || store.isCurrent(email);
    }

    public int purgeExpiredChallenges() {
        var cutoff = Instant.now();
        int total = 0;
        int removed;
        do {
            removed = store.purgeExpired(cutoff, 500);
            total += removed;
        } while (removed == 500);
        return total;
    }

    public int purgeExpiredChallenges(int batchSize) {
        return store.purgeExpired(Instant.now(), batchSize);
    }

    private Optional<Delivery> issue(EmailPasswordStore.Purpose purpose, LoginEmail address,
                                     EmailPasswordStore.Credential owner, Duration lifetime) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        return store.issueChallenge(purpose, address, digest(token), owner, now, now.plus(RESEND), now.plus(lifetime))
                ? Optional.of(new Delivery(token)) : Optional.empty();
    }

    private Optional<EmailPasswordStore.Challenge> proof(EmailPasswordStore.Purpose purpose, String token) {
        return token != null && TOKEN.matcher(token).matches()
                ? store.lockChallenge(purpose, digest(token), Instant.now()) : Optional.empty();
    }

    private boolean passwordAllowed(String password) {
        return validLength(password) && !COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))
                && blocklists.orderedStream().noneMatch(blocklist -> blocklist.isBlocked(password));
    }

    private static boolean validLength(String password) {
        if (password == null || password.length() > 256) {
            return false;
        }
        int length = password.codePointCount(0, password.length());
        return length >= 15 && length <= 128
                && password.codePoints().noneMatch(codePoint ->
                        codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record Delivery(String token) {
        @Override
        public String toString() {
            return "Delivery[redacted]";
        }
    }
}
