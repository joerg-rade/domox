package domox.webapp.integtests.metamodel;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import lombok.val;
import org.apache.isis.applib.services.registry.ServiceRegistry;
import org.apache.isis.applib.services.swagger.Format;
import org.apache.isis.applib.services.swagger.Visibility;
import org.apache.isis.testing.integtestsupport.applib.swagger.SwaggerExporter;
import org.apache.isis.viewer.restfulobjects.jaxrsresteasy4.IsisModuleViewerRestfulObjectsJaxrsResteasy4;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import javax.inject.Inject;
import java.io.IOException;

@Import({
        IsisModuleViewerRestfulObjectsJaxrsResteasy4.class
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
