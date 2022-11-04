package domox.webapp.integtests.metamodel;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import lombok.val;
import org.apache.causeway.applib.services.registry.ServiceRegistry;
import org.apache.causeway.applib.services.swagger.Format;
import org.apache.causeway.applib.services.swagger.Visibility;
import org.apache.causeway.testing.integtestsupport.applib.swagger.SwaggerExporter;
import org.apache.causeway.viewer.restfulobjects.jaxrsresteasy4.CausewayModuleViewerRestfulObjectsJaxrsResteasy4;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.inject.Inject;
import java.io.IOException;

@Import({
        CausewayModuleViewerRestfulObjectsJaxrsResteasy4.class
})
class SwaggerExport_IntegTest extends ApplicationIntegTestAbstract {

    @Inject
    ServiceRegistry serviceRegistry;

    @Test
    void export() throws IOException {
        val swaggerExporter = new SwaggerExporter(serviceRegistry);
        swaggerExporter.export(Visibility.PRIVATE, Format.JSON);
    }
}
