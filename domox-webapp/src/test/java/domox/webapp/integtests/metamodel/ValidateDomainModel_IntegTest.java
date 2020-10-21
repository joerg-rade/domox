package domox.webapp.integtests.metamodel;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import org.apache.isis.applib.services.registry.ServiceRegistry;
import org.apache.isis.testing.integtestsupport.applib.validate.DomainModelValidator;

import domox.webapp.integtests.ApplicationIntegTestAbstract;

class ValidateDomainModel_IntegTest extends ApplicationIntegTestAbstract {

    @Inject
    ServiceRegistry serviceRegistry;

    @Test
    void validate() {
        new DomainModelValidator(serviceRegistry).assertValid();
    }


}
