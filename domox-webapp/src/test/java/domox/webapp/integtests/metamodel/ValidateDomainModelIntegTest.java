package domox.webapp.integtests.metamodel;

import jakarta.inject.Inject;
import org.apache.causeway.applib.services.registry.ServiceRegistry;
import org.apache.causeway.testing.integtestsupport.applib.validate.DomainModelValidator;
import org.junit.jupiter.api.Test;

import domox.webapp.integtests.ApplicationIntegTestAbstract;


class ValidateDomainModelIntegTest extends ApplicationIntegTestAbstract {

    @Inject
    ServiceRegistry serviceRegistry;

    @Test
    void validate() {
        new DomainModelValidator(serviceRegistry).assertValid();
    }

    }
