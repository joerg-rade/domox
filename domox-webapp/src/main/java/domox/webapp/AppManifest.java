package domox.webapp;

import domox.webapp.application.ApplicationModule;
import org.apache.causeway.applib.CausewayModuleApplibChangeAndExecutionLoggers;
import org.apache.causeway.applib.CausewayModuleApplibMixins;
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
        CausewayModuleApplibMixins.class,
        CausewayModuleApplibChangeAndExecutionLoggers.class,

        CausewayModuleCoreRuntimeServices.class,
        CausewayModuleSecurityBypass.class,
        CausewayModulePersistenceJpaEclipselink.class,
        CausewayModuleViewerRestfulObjectsJaxrsResteasy.class,

        CausewayModuleCoreConfig.class,
        CausewayModuleExtCors.class,
        CausewayModuleViewerWicketViewer.class,
        CausewayModuleTestingFixturesApplib.class,

        ApplicationModule.class,
})
@PropertySources({
        @PropertySource(CausewayPresets.DebugDiscovery),
})
public class AppManifest {
}
