package domox.webapp.integtests.metamodel;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import jakarta.inject.Inject;
import org.apache.causeway.applib.services.registry.ServiceRegistry;
import org.apache.causeway.testing.integtestsupport.applib.validate.DomainModelValidator;
import org.junit.jupiter.api.Test;

class ValidateDomainModel_IntegTest extends ApplicationIntegTestAbstract {

    @Inject
    ServiceRegistry serviceRegistry;

    @Test
    void validate() {
        new DomainModelValidator(serviceRegistry).assertValid();
    }


}
