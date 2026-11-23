package example.persistence;

import com.notshahrad.spring_boot_base.common.config.JpaAuditingConfiguration;
import com.notshahrad.spring_boot_base.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseDataJpaStarterTest {

    private final ApplicationContextRunner consumer = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID(),
                    "spring.jpa.hibernate.ddl-auto=create-drop"
            );

    @Test
    void leavesAuditingDisabledByDefaultAndDiscoversConsumerRepositories() {
        consumer.run(context -> assertThat(context).hasNotFailed()
                .hasSingleBean(ConsumerRecordRepository.class)
                .doesNotHaveBean("jpaAuditingHandler"));
    }

    @Test
    void persistsBaseEntityWithTimestampsAndNoActorByDefault() {
        consumer.withPropertyValues("base.data.jpa.auditing.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            var repository = context.getBean(ConsumerRecordRepository.class);
            var saved = repository.saveAndFlush(new ConsumerRecord());
            var reloaded = repository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCreatedAt()).isNotNull();
            assertThat(reloaded.getUpdatedAt()).isNotNull();
            assertThat(reloaded.getCreatedBy()).isNull();
            assertThat(reloaded.getUpdatedBy()).isNull();
        });
    }

    @Test
    void usesApplicationAuditorWithoutRequiringAConventionalBeanName() {
        consumer.withPropertyValues("base.data.jpa.auditing.enabled=true")
                .withBean("applicationAuditor", AuditorAware.class, () -> () -> Optional.of("customer-42"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var repository = context.getBean(ConsumerRecordRepository.class);
                    var saved = repository.saveAndFlush(new ConsumerRecord());
                    var reloaded = repository.findById(saved.getId()).orElseThrow();
                    assertThat(reloaded.getCreatedBy()).isEqualTo("customer-42");
                    assertThat(reloaded.getUpdatedBy()).isEqualTo("customer-42");
                });
    }

    @Test
    void backsOffWhenTheApplicationAlreadyEnablesJpaAuditing() {
        consumer.withPropertyValues("base.data.jpa.auditing.enabled=true")
                .withUserConfiguration(JpaAuditingConfiguration.class)
                .withBean("auditorAware", AuditorAware.class, () -> () -> Optional.of("existing-auditor"))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(
                            "com.notshahrad.base.data.jpa.autoconfigure.BaseJpaAuditingAutoConfiguration");
                    var saved = context.getBean(ConsumerRecordRepository.class).saveAndFlush(new ConsumerRecord());
                    assertThat(saved.getCreatedBy()).isEqualTo("existing-auditor");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

@Entity
class ConsumerRecord extends BaseEntity {
}

interface ConsumerRecordRepository extends JpaRepository<ConsumerRecord, UUID> {
}
