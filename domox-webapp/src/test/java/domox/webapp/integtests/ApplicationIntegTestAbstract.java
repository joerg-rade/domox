package domox.webapp.integtests;

import org.apache.causeway.testing.integtestsupport.applib.CausewayIntegrationTestAbstract;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
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
public abstract class ApplicationIntegTestAbstract extends CausewayIntegrationTestAbstract {

    @Container
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:latest")
            .withDatabaseName("domox")
            .withUsername("domox")
            .withPassword("domox");

}
