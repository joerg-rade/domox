package domox.webapp.integtests;

import domox.webapp.application.ApplicationModule;
import org.apache.isis.testing.integtestsupport.applib.IsisIntegrationTestAbstract;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
            // we use a slightly different confuguration compared to the production (AppManifest/webapp)
//            SimpleWebAppTestConfiguration_usingJpa.class,
//            BddStepDefsModule.class,
            ApplicationModule.class,
    },
    properties = {
            // "logging.level.io.cucumber.core.runner.Runner=DEBUG",
            "isis.persistence.jpa.auto-create-schemas=simple"
    }
)
public abstract class ApplicationIntegTestAbstract extends IsisIntegrationTestAbstract {

}
