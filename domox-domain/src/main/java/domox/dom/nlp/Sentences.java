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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@DomainService
@Named(DomainModule.NAMESPACE + ".Sentences")
@Priority(PriorityPrecedence.EARLY)
public class Sentences {

    private static final Logger log = LoggerFactory.getLogger(Sentences.class);

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final SentenceRepository sentenceRepository;
    private final DiagramBuilder diagramBuilder;

    @Inject
    public Sentences(
            RepositoryService repositoryService,
            FactoryService factoryService,
            SentenceRepository sentenceRepository,
            DiagramBuilder diagramBuilder) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.sentenceRepository = sentenceRepository;
        this.diagramBuilder = diagramBuilder;
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

    /**
     * Lazily renders the syntax diagram (Kroki → PDF) for a {@link Sentence} the first
     * time its {@code diagram} is accessed, reconstructing the dependency list from the
     * sentence's persisted {@link TypedDependency}s rather than re-running the NLP pipeline.
     * <p>
     * No-op when the sentence already has a diagram, has no typed dependencies, or the
     * render fails (e.g. Kroki unavailable) — in which case the error is logged and the
     * sentence is simply left without a diagram for this access.
     */
    @Programmatic
    public void ensureDiagram(final Sentence sentence) {
        if (sentence == null || sentence.hasDiagram()) {
            return;
        }
        final List<TypedDependency> typedDependencies = sentence.getTypedDependencies();
        if (typedDependencies == null || typedDependencies.isEmpty()) {
            return;
        }
        try {
            final List<ExtendedDependencyTO> dependencies = typedDependencies.stream()
                    .map(this::toExtendedDependency)
                    .collect(Collectors.toList());
            final byte[] diagram = diagramBuilder.buildTypedDependencyDiagram(dependencies);
            final String fileName = sentence.title() + ".pdf";
            sentence.updateImageFromBytes(diagram, fileName);
        } catch (RuntimeException e) {
            log.warn("Failed to build syntax diagram lazily for sentence #{}: {}",
                    sentence.getId(), e.getMessage());
        }
    }

    private ExtendedDependencyTO toExtendedDependency(final TypedDependency td) {
        return new ExtendedDependencyTO(
                td.getType().getCode(),
                td.getGovernorIndex(),
                td.getGovernorGloss(),
                td.getGovernorPos() != null ? td.getGovernorPos().getCode() : "",
                td.getGovernorLemma(),
                td.getDependentIndex(),
                td.getDependentGloss(),
                td.getDependentPos() != null ? td.getDependentPos().getCode() : "",
                td.getDependentLemma());
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