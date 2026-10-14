package domox.webapp.integtests.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class Smoke_IntegTest extends ApplicationIntegTestAbstract {

    /**
     * Boot smoke — reaching this test means the full Spring context
     * ({@code ApplicationIntegTestAbstract}'s {@code @SpringBootTest}) booted against the
     * Testcontainers Postgres wired by {@link ApplicationIntegTestAbstract.Initializer}. Without at least one
     * runnable test method surefire never instantiates the class, so this guard keeps the
     * class from being a silent no-op when named in CI's {@code -Dtest} list.
     */
    @Test
    void context_boots_and_db_container_is_up() {
        assertThat(postgres.isRunning()).isTrue();
    }

    }
