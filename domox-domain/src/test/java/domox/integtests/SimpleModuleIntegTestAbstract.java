package domox.integtests;

import domox.DomainModule;
import org.apache.causeway.testing.fixtures.applib.CausewayModuleTestingFixturesApplib;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(
        classes = SimpleModuleIntegTestAbstract.AppManifest.class
)
@TestPropertySource({
//        CausewayPresets.H2InMemory_withUniqueSchema,
//        CausewayPresets.DataNucleusAutoCreate,
//        CausewayPresets.UseLog4j2Test,
})
public abstract class SimpleModuleIntegTestAbstract {

    @Configuration
    @Import({
//            CausewayModuleCoreRuntimeServices.class,
//            CausewayModuleSecurityBypass.class,
//        CausewayModuleJdoDataNucleus5.class,
            CausewayModuleTestingFixturesApplib.class,

            DomainModule.class
    })
    public static class AppManifest {
    }
}
