package domox;

import org.apache.causeway.extensions.d3.applib.CausewayModuleExtD3Applib;
import org.apache.causeway.extensions.pdfjs.applib.CausewayModuleExtPdfjsApplib;
import org.apache.causeway.persistence.jpa.applib.CausewayModulePersistenceJpaApplib;
import org.apache.causeway.valuetypes.vega.applib.CausewayModuleValVegaApplib;
import org.apache.causeway.testing.fixtures.applib.modules.ModuleWithFixtures;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Import({
        CausewayModuleExtPdfjsApplib.class,
        CausewayModuleExtD3Applib.class,
        CausewayModuleValVegaApplib.class,
        CausewayModulePersistenceJpaApplib.class,
})
@ComponentScan
@EnableJpaRepositories
@EntityScan(basePackageClasses = {
        DomainModule.class
})
public class DomainModule implements
        ModuleWithFixtures {

    public static final String NAMESPACE = DomainModule.SCHEMA;
    public static final String SCHEMA = "domox";

}
