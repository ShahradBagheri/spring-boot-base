package com.notshahrad.spring_boot_base.websocket;

import com.notshahrad.spring_boot_base.auth.LocalUserPrincipal;
import com.notshahrad.spring_boot_base.auth.token.AccessTokenIssuer;
import com.notshahrad.spring_boot_base.support.TokenTestKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "base.websocket.enabled=true")
@ActiveProfiles({"test", "auth-token"})
class WebSocketBroadcastTest {
    @LocalServerPort int port;
    @Autowired AccessTokenIssuer issuer;
    @TempDir static Path keys;

    @DynamicPropertySource
    static void configureKeys(DynamicPropertyRegistry registry) throws Exception {
        TokenTestKeys.register(registry, keys);
    }

    private URI endpoint() {
        return URI.create("ws://localhost:" + port + "/ws");
    }

    private record Account(UUID id) implements LocalUserPrincipal {
        public UUID getLocalUserId() { return id; }
        public String getName() { return id.toString(); }
    }

    private String token() {
        var account = new Account(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(account, null, List.of()));
        try {
            return issuer.issue().getTokenValue();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rejectsAnonymousAndForeignOriginHandshakes() {
        try (var client = HttpClient.newHttpClient()) {
            var anonymous = client.newWebSocketBuilder();
            var foreign = client.newWebSocketBuilder().header("Origin", "https://foreign.example")
                    .header("Authorization", "Bearer " + token());
            assertRejected(anonymous, 401);
            assertRejected(foreign, 403);
        }
    }

    private void assertRejected(WebSocket.Builder builder, int expected) {
        var failure = assertThrows(ExecutionException.class,
                () -> builder.buildAsync(endpoint(), new Frames()).get(5, SECONDS));
        assertEquals(expected, assertInstanceOf(WebSocketHandshakeException.class, failure.getCause())
                .getResponse().statusCode());
    }

    @Test
    void broadcastsToTwoSubscribersAndRejectsDirectTopicWrites() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var first = new Frames();
            var second = new Frames();
            var a = subscribe(http, first);
            try {
                var b = subscribe(http, second);
                try {
                    assertTrue(first.next().contains("\n\nready"));
                    send(a, "/app/broadcast", "hello");
                    assertTrue(first.next().contains("\n\nhello"));
                    assertTrue(second.next().contains("\n\nhello"));
                    send(b, "/topic/broadcast", "forged");
                    assertTrue(second.next().startsWith("ERROR"));
                } finally {
                    b.abort();
                }
            } finally {
                a.abort();
            }
        }
    }

    private WebSocket subscribe(HttpClient client, Frames frames) throws Exception {
        var socket = client.newWebSocketBuilder().header("Authorization", "Bearer " + token())
                .subprotocols("v12.stomp").buildAsync(endpoint(), frames).get(5, SECONDS);
        socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).join();
        assertTrue(frames.next().startsWith("CONNECTED"));
        socket.sendText("SUBSCRIBE\nid:events\ndestination:/topic/broadcast\n\n\0", true).join();
        // Receiving our own marker confirms the broker has registered this subscription.
        send(socket, "/app/broadcast", "ready");
        assertTrue(frames.next().contains("\n\nready"));
        return socket;
    }

    private static void send(WebSocket socket, String destination, String payload) {
        socket.sendText("SEND\ndestination:" + destination + "\ncontent-type:text/plain\n\n" + payload + "\0", true).join();
    }

    private static class Frames implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final StringBuilder buffer = new StringBuilder();

        public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            buffer.append(text);
            if (last) {
                messages.add(buffer.toString());
                buffer.setLength(0);
            }
            socket.request(1);
            return null;
        }

        String next() throws InterruptedException {
            String result = messages.poll(5, SECONDS);
            assertNotNull(result, "Timed out waiting for a STOMP frame");
            return result;
        }
    }
}
