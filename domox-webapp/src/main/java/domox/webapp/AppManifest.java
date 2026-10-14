package domox.webapp;

import domox.DomainModule;
import org.apache.causeway.applib.CausewayModuleApplibChangeAndExecutionLoggers;
import org.apache.causeway.applib.CausewayModuleApplibMixins;
import org.apache.causeway.core.config.CausewayModuleCoreConfig;
import org.apache.causeway.core.config.presets.CausewayPresets;
import org.apache.causeway.core.runtimeservices.CausewayModuleCoreRuntimeServices;
import org.apache.causeway.extensions.cors.impl.CausewayModuleExtCors;
import org.apache.causeway.extensions.d3.applib.spi.D3GraphAdvisor;
import org.apache.causeway.extensions.d3.wkt.ui.CausewayModuleExtD3WicketUi;
import org.apache.causeway.extensions.pdfjs.wkt.ui.CausewayModuleExtPdfjsWicketUi;
import org.apache.causeway.persistence.jpa.eclipselink.CausewayModulePersistenceJpaEclipselink;
import org.apache.causeway.security.bypass.CausewayModuleSecurityBypass;
import org.apache.causeway.testing.fixtures.applib.CausewayModuleTestingFixturesApplib;
import org.apache.causeway.viewer.restfulobjects.viewer.CausewayModuleViewerRestfulObjectsViewer;
import org.apache.causeway.viewer.wicket.viewer.CausewayModuleViewerWicketViewer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import java.util.Collections;
import java.util.List;

@Configuration
@Import({
        CausewayModuleApplibMixins.class,
        CausewayModuleApplibChangeAndExecutionLoggers.class,

        CausewayModuleCoreRuntimeServices.class,
        CausewayModuleSecurityBypass.class,
        CausewayModulePersistenceJpaEclipselink.class,
        CausewayModuleViewerRestfulObjectsViewer.class,

        CausewayModuleCoreConfig.class,
        CausewayModuleExtCors.class,
        CausewayModuleExtD3WicketUi.class,
        CausewayModuleExtPdfjsWicketUi.class,
        CausewayModuleViewerWicketViewer.class,
        CausewayModuleTestingFixturesApplib.class,

        DomainModule.class,
})
@PropertySources({
        @PropertySource(CausewayPresets.DebugDiscovery),
})
public class AppManifest {

    /**
     * The D3 metamodel extension ({@code D3GraphFacetFromAnnotation}) injects a
     * package-level raw {@code List advisors} field via {@code @Inject}.  Spring
     * cannot infer the generic type parameter at the bytecode level, so it looks
     * for a concrete {@code List&lt;D3GraphAdvisor&gt;} bean rather than collecting
     * individual {@code D3GraphAdvisor} beans.  This bean provides the required
     * collection (empty by default; add custom advisors as needed).
     */
    @Bean
    public List<D3GraphAdvisor> d3GraphAdvisors() {
        return Collections.emptyList();
    }
}
