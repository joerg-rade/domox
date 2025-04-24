package domox.integtests;

import domox.DomainModule;
import org.apache.causeway.testing.fixtures.applib.CausewayModuleTestingFixturesApplib;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        classes = SimpleModuleIntegTestAbstract.AppManifest.class
)
public abstract class SimpleModuleIntegTestAbstract {

    @Configuration
    @Import({
            CausewayModuleTestingFixturesApplib.class,
            DomainModule.class
    })
    public static class AppManifest {
    }
}
