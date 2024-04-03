package domox.webapp;

import domox.webapp.application.ApplicationModule;
import domox.webapp.custom.CustomModule;
import org.apache.causeway.core.config.presets.CausewayPresets;
import org.apache.causeway.core.runtimeservices.CausewayModuleCoreRuntimeServices;
import org.apache.causeway.extensions.cors.impl.CausewayModuleExtCors;
import org.apache.causeway.extensions.flyway.impl.CausewayModuleExtFlywayImpl;
import org.apache.causeway.persistence.jpa.eclipselink.CausewayModulePersistenceJpaEclipselink;
import org.apache.causeway.security.shiro.CausewayModuleSecurityShiro;
import org.apache.causeway.testing.fixtures.applib.CausewayModuleTestingFixturesApplib;
import org.apache.causeway.testing.h2console.ui.CausewayModuleTestingH2ConsoleUi;
import org.apache.causeway.viewer.restfulobjects.jaxrsresteasy.CausewayModuleViewerRestfulObjectsJaxrsResteasy;
import org.apache.causeway.viewer.wicket.viewer.CausewayModuleViewerWicketViewer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@Import({
        CausewayModuleCoreRuntimeServices.class,
        CausewayModuleSecurityShiro.class,
        CausewayModulePersistenceJpaEclipselink.class,
        CausewayModuleViewerRestfulObjectsJaxrsResteasy.class,
        CausewayModuleViewerWicketViewer.class,

        CausewayModuleTestingFixturesApplib.class,
        CausewayModuleTestingH2ConsoleUi.class,

        CausewayModuleExtFlywayImpl.class,

        // CORS
        CausewayModuleExtCors.class,

        ApplicationModule.class,
        CustomModule.class,
//        QuartzModule.class,

        // discoverable fixtures
})
@PropertySources({
        @PropertySource(CausewayPresets.DebugDiscovery),
})
public class AppManifest {
}
