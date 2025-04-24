package domox.dom.nlp;

import domox.DomainModule;
import domox.dom.rqm.Document;
import domox.nlp.SentenceTO;
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
import org.apache.causeway.applib.value.Blob;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Sentences")
@Priority(PriorityPrecedence.EARLY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class Sentences {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;

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
    public Sentence build(SentenceTO to, Document document) {
        final Sentence sentence = create();
        sentence.setDocument(document);
        final SentenceAdapter sentenceAdapter = new SentenceAdapter(to);
        //
        final String text = sentenceAdapter.transferObjectAsString();
        sentence.setText(text);
        //FIXME
        sentence.setTypedDependencies(null);
        //
        final Blob diagram = sentenceAdapter.buildTypedDependencyDiagram();
        sentence.setDiagram(diagram);
        return sentence;
    }

}