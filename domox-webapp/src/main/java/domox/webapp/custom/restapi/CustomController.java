package domox.webapp.custom.restapi;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import lombok.RequiredArgsConstructor;
import org.apache.isis.applib.services.xactn.TransactionService;
import org.apache.isis.core.runtime.iactn.IsisInteractionFactory;
import org.apache.isis.core.security.authentication.standard.SimpleSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
class CustomController {

    private final IsisInteractionFactory isisInteractionFactory;
    private final TransactionService transactionService;
    private final Authors repository;

    @GetMapping("/custom/simpleObjects")
    List<Author> all() {
        return callAuthenticated(newSession(), () -> repository.listAll());
    }

    private SimpleSession newSession() {
        return new SimpleSession("sven", Collections.emptyList());
    }

    private <T> T callAuthenticated(
            final SimpleSession session,
            final Supplier<T> task) {
        return isisInteractionFactory.callAuthenticated(
                session,
                () -> transactionService.executeWithinTransaction(task)
        );
    }

}
