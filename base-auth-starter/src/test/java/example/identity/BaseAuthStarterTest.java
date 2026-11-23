package example.identity;

import com.notshahrad.spring_boot_base.auth.AppUserRepository;
import com.notshahrad.spring_boot_base.auth.ExternalIdentityRepository;
import com.notshahrad.spring_boot_base.auth.OidcIdentityService;
import com.notshahrad.spring_boot_base.auth.VerifiedProviderIdentity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseAuthStarterTest {

    private final ApplicationContextRunner consumer = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID(),
                    "spring.jpa.hibernate.ddl-auto=create-drop"
            );

    @Test
    void disabledLocalAuthKeepsConsumerRepositoriesOnly() {
        consumer.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(ConsumerRecordRepository.class)
                .doesNotHaveBean(AppUserRepository.class)
                .doesNotHaveBean(OidcIdentityService.class));
    }

    @Test
    void addsLocalIdentityPersistenceAlongsideConsumerEntitiesAndRepositories() {
        consumer.withPropertyValues("base.auth.local.enabled=true", "base.data.jpa.auditing.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConsumerRecordRepository.class)
                            .hasSingleBean(ExternalIdentityRepository.class);
                    var identity = new VerifiedProviderIdentity(
                            "https://issuer.example.test", "subject-1", "user@example.test", true, "User");
                    var service = context.getBean(OidcIdentityService.class);
                    var user = service.resolveOrCreate(identity);
                    assertThat(service.resolveOrCreate(identity)).isEqualTo(user);
                    assertThat(context.getBean(AppUserRepository.class).findById(user)).isPresent();
                    assertThatThrownBy(() -> service.linkToAuthenticatedUser(user, identity))
                            .isInstanceOf(SecurityException.class)
                            .hasMessageContaining("approval");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }
}

@Entity
class ConsumerRecord {
    @Id
    private Long id;

    protected ConsumerRecord() {
    }
}

interface ConsumerRecordRepository extends JpaRepository<ConsumerRecord, Long> {
}
