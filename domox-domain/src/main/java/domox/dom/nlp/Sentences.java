package domox.dom.nlp;

import domox.DomainModule;
import domox.nlp.BasicDependencyTO;
import domox.nlp.SentenceTO;
import domox.nlp.TokenTO;
import domox.svc.SentenceAdapter;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.causeway.applib.annotation.Action;
import org.apache.causeway.applib.annotation.ActionLayout;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.SemanticsOf;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.ArrayList;
import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Sentences")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Sentences {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final Tokens tokens;

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Sentence> listAll() {
        return repositoryService.allInstances(Sentence.class);
    }

    @Programmatic
    public Sentence create() {
        final Sentence obj = factoryService.detachedEntity(Sentence.class);
        repositoryService.persist(obj);
        return obj;
    }

    @Programmatic
    public Sentence build(SentenceTO sentenceTO) {
        final Sentence sentence = create();
        final SentenceAdapter sentenceAdapter = new SentenceAdapter(sentenceTO);
        //
        final String text = sentenceAdapter.transferObjectAsString();
        sentence.setText(text);
        final List<TokenTO> tokenToList = sentenceTO.getTokens();
        for (final TokenTO tokenTO : tokenToList) {
            final Token t = tokens.build(tokenTO);
            t.setSentence(sentence);
            sentence.getTokenList().add(t);
        }
        // set TypedDependencies
        assignTypedDependencies(sentenceTO, sentence);
        return sentence;
    }

    @Programmatic
    public void initDiagram(SentenceTO sentenceTO, Sentence sentence) {
        final SentenceAdapter sentenceAdapter = new SentenceAdapter(sentenceTO);
        final byte[] diagram = sentenceAdapter.buildTypedDependencyDiagram(sentenceTO);
        final String fileName = sentence.title() + ".svg";
        sentence.updateImageFromBytes(diagram, fileName);
    }

    private void assignTypedDependencies(SentenceTO sentenceTO, Sentence sentence) {
        final List<BasicDependencyTO> rawDependencyList = sentenceTO.getEnhancedPlusPlusDependencies();

        for (final BasicDependencyTO dependency : rawDependencyList) {
            final TypedDependency td = factoryService.detachedEntity(TypedDependency.class);
            //
            final String depCode = dependency.getDep();
            td.setType(TdType.fromCode(depCode));
            //
            final int govIndex = dependency.getGovernor().intValue();
            final Token a = sentence.getToken(govIndex);
            //
            final int depIndex = dependency.getDependent().intValue();
            final Token b = sentence.getToken(depIndex);
            //
            final List<Token> tokens = new ArrayList<>(2);
            tokens.add(a);
            tokens.add(b);
            td.setTokenList(tokens);

            repositoryService.persist(td);
        }
    }

}