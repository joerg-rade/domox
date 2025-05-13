package domox.dom.nlp;

import domox.DomainModule;
import domox.nlp.TokenTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Tokens")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Tokens {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;

    @ActionLayout(sequence = "1")
    public List<Token> listAll() {
        return repositoryService.allInstances(Token.class);
    }

    @Programmatic
    public Token create() {
        final Token obj = factoryService.detachedEntity(Token.class);
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public Token build(TokenTO to) {
        final Token obj = create();
        final String t = to.getWord();
        obj.setText(t);
        final String pos = to.getPos();
        final PartOfSpeechType partOfSpeechType = PartOfSpeechType.fromCode(pos);
        obj.setType(partOfSpeechType);
        return obj;
    }

}