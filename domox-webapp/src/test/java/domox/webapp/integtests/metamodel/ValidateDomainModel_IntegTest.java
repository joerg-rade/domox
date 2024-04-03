package domox.webapp.integtests.metamodel;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import org.apache.causeway.applib.services.registry.ServiceRegistry;
import org.apache.causeway.testing.integtestsupport.applib.validate.DomainModelValidator;

import domox.webapp.integtests.ApplicationIntegTestAbstract;

class ValidateDomainModel_IntegTest extends ApplicationIntegTestAbstract {

    @Inject
    ServiceRegistry serviceRegistry;

    @Test
    void validate() {
        new DomainModelValidator(serviceRegistry).assertValid();
    }


}
