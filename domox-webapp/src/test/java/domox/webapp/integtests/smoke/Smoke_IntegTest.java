package domox.webapp.integtests.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@ContextConfiguration(initializers = Smoke_IntegTest.Initializer.class)
@Transactional
class Smoke_IntegTest extends ApplicationIntegTestAbstract {

    /**
     * Boot smoke — reaching this test means the full Spring context
     * ({@code ApplicationIntegTestAbstract}'s {@code @SpringBootTest}) booted against the
     * Testcontainers Postgres wired by {@link Initializer}. Without at least one
     * runnable test method surefire never instantiates the class, so this guard keeps the
     * class from being a silent no-op when named in CI's {@code -Dtest} list.
     */
    @Test
    void context_boots_and_db_container_is_up() {
        assertThat(postgres.isRunning()).isTrue();
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (ApplicationIntegTestAbstract.postgres != null && ApplicationIntegTestAbstract.postgres.isRunning()) {
                TestPropertyValues.of(
                        "spring.datasource.url=" + ApplicationIntegTestAbstract.postgres.getJdbcUrl(),
                        "spring.datasource.username=" + ApplicationIntegTestAbstract.postgres.getUsername(),
                        "spring.datasource.password=" + ApplicationIntegTestAbstract.postgres.getPassword()
                ).applyTo(context.getEnvironment());
            }
        }
    }

}
