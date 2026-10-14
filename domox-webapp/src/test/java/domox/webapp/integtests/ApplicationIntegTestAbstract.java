package domox.webapp.integtests;

import org.apache.causeway.testing.integtestsupport.applib.CausewayIntegrationTestAbstract;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
    classes = {
            // we use a slightly different confuguration compared to the production (AppManifest/webapp)
//            SimpleWebAppTestConfiguration_usingJpa.class,
//            BddStepDefsModule.class,
//            ApplicationModule.class,
    },
    properties = {
            // "logging.level.io.cucumber.core.runner.Runner=DEBUG",
            "causeway.persistence.jpa.auto-create-schemas=simple"
    }
)
@TestPropertySource(properties = "eclipselink.ddl-generation.output-mode=database")
@ContextConfiguration(initializers = ApplicationIntegTestAbstract.Initializer.class)
public abstract class ApplicationIntegTestAbstract extends CausewayIntegrationTestAbstract {

    /**
     * One shared Postgres container per test JVM.
     * <p>
     * The container is started eagerly here, in the static initializer, instead of via
     * {@code @Testcontainers}/{@code @Container}.  With the JUnit-extension lifecycle the
     * (static) container would be stopped after <em>every</em> test class and restarted by
     * the next one on a <em>new random port</em>, while Spring Boot's context cache keeps
     * the old JDBC URL — the "HikariPool : Connection to localhost:&lt;oldPort&gt; refused"
     * failures seen with the previous per-class wiring.  Starting it once keeps the port
     * stable for the lifetime of the JVM, so a cached/deduped Spring context can never
     * point at a stopped container.  Testcontainers' resource reaper still removes the
     * container when the JVM exits.
     */
    public static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:latest")
                .withDatabaseName("domox")
                .withUsername("domox")
                .withPassword("domox");
        postgres.start();
    }

    /**
     * Points {@code spring.datasource.*} at the shared Testcontainers Postgres.
     * <p>
     * The container is guaranteed to be running (started in the static initializer above)
     * before any Spring context is created, so the properties are applied unconditionally —
     * a skipped override would silently fall back to the production datasource
     * ({@code application.yml}), i.e. the real dev database.
     */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(final ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword()
            ).applyTo(context.getEnvironment());
        }
    }

}
