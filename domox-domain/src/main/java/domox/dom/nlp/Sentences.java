package domox.dom.nlp;

import domox.DomainModule;
import domox.diagram.DiagramBuilder;
import domox.dom.rqm.Document;
import domox.nlp.ExtendedDependencyFactory;
import domox.nlp.ExtendedDependencyTO;
import domox.nlp.SentenceTO;
import domox.nlp.TokenTO;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.util.List;

@DomainService
@Named(DomainModule.NAMESPACE + ".Sentences")
@Priority(PriorityPrecedence.EARLY)
public class Sentences {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final SentenceRepository sentenceRepository;

    @Inject
    public Sentences(
            RepositoryService repositoryService,
            FactoryService factoryService,
            SentenceRepository sentenceRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.sentenceRepository = sentenceRepository;
    }

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
        final String text = transferObjectAsString(sentenceTO);
        sentence.setText(text);
        // set TypedDependencies
        assignTypedDependencies(sentenceTO, sentence);
        return sentence;
    }

    private String transferObjectAsString(SentenceTO sentenceTO) {
        final StringBuilder sb = new StringBuilder();
        final List<TokenTO> tokens = sentenceTO.getTokens();
        for (TokenTO tt : tokens) {
            sb.append(tt.getWord()).append(" ");
        }
        return sb.toString().trim();
    }

    @Programmatic
    public void initDiagram(SentenceTO sentenceTO, Sentence sentence) {
        //TODO: pull Diagram building back in, in order to avoid duplication of Dependency+POS
        final byte[] diagram = new DiagramBuilder().buildTypedDependencyDiagram(sentenceTO);
        final String fileName = sentence.title() + ".pdf";
        sentence.updateImageFromBytes(diagram, fileName);
    }

    private void assignTypedDependencies(SentenceTO sentenceTO, Sentence sentence) {
        final List<ExtendedDependencyTO> extended =
                new ExtendedDependencyFactory(sentenceTO).getDependencies();

        for (final ExtendedDependencyTO dependency : extended) {
            final TypedDependency td = factoryService.detachedEntity(TypedDependency.class);
            td.setType(TdType.fromCode(dependency.getDep()));
            td.setGovernorIndex((int) dependency.getGovernor());
            td.setDependentIndex((int) dependency.getDependent());
            td.setGovernorGloss(dependency.getGovernorGloss());
            td.setDependentGloss(dependency.getDependentGloss());
            td.setGovernorPos(PartOfSpeechType.fromCode(dependency.getGovernorPos()));
            td.setDependentPos(PartOfSpeechType.fromCode(dependency.getDependentPos()));
            td.setGovernorLemma(dependency.getGovernorLemma());
            td.setDependentLemma(dependency.getDependentLemma());
            td.setSentence(sentence);

            sentence.addTypedDependency(td);
            repositoryService.persist(td);
        }
    }

    public List<Sentence> findByDocument(Document document) {
        return sentenceRepository.findByDocument(document);
    }

    @Action()
    @ActionLayout(sequence = "6", cssClassFa = "trash")
    public void deleteAll() {
        var all = listAll();
        for (Sentence s : all) {
            repositoryService.remove(s);
        }
    }
}