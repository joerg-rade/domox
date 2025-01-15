package domox.webapp;

import domox.webapp.application.ApplicationModule;
import domox.webapp.custom.CustomModule;
import org.apache.causeway.core.config.CausewayModuleCoreConfig;
import org.apache.causeway.core.config.presets.CausewayPresets;
import org.apache.causeway.core.runtimeservices.CausewayModuleCoreRuntimeServices;
import org.apache.causeway.extensions.cors.impl.CausewayModuleExtCors;
import org.apache.causeway.persistence.jpa.eclipselink.CausewayModulePersistenceJpaEclipselink;
import org.apache.causeway.security.bypass.CausewayModuleSecurityBypass;
import org.apache.causeway.testing.fixtures.applib.CausewayModuleTestingFixturesApplib;
import org.apache.causeway.viewer.restfulobjects.jaxrsresteasy.CausewayModuleViewerRestfulObjectsJaxrsResteasy;
import org.apache.causeway.viewer.wicket.viewer.CausewayModuleViewerWicketViewer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

@Configuration
@Import({
        CausewayModuleCoreRuntimeServices.class,
        //CausewayModuleExtSpringSecurityOAuth2.class, // Spring Security OAuth2 support
        CausewayModuleSecurityBypass.class,
//        AuthorizorShiro.class,
//        LoginController.class,

        CausewayModulePersistenceJpaEclipselink.class,
        CausewayModuleViewerRestfulObjectsJaxrsResteasy.class,

        CausewayModuleCoreConfig.class,
        CausewayModuleExtCors.class,
        CausewayModuleViewerWicketViewer.class,
        CausewayModuleTestingFixturesApplib.class,

        ApplicationModule.class,
        CustomModule.class,
})
@PropertySources({
        @PropertySource(CausewayPresets.DebugDiscovery),
})
public class AppManifest {
}
