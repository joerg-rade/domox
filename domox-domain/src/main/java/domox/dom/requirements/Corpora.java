package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.model.DomainModel;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.*;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The <b>Corpus</b> aggregate's service: list, create and find the corpus that groups a requirements
 * suite.
 *
 * <p><b>One responsibility — aggregate CRUD.</b>  It changes when the {@link Corpus} entity or its
 * persistence does, and for no other reason.  Everything else that used to live here now has its own
 * service, each with its own axis of change:</p>
 * <ul>
 *   <li>the requirements-ingestion pipeline → {@link CorpusIngestion};</li>
 *   <li>the RDF/OWL ontology diagrams → {@link OntologyDiagrams} (SPARQL text in
 *       {@code domox.dom.rdf.OntologyQueries});</li>
 *   <li>the deletion cascade → {@link CorpusDeletion}.</li>
 * </ul>
 */
@Named(DomainModule.NAMESPACE + ".Corpora")
@DomainService
@DomainServiceLayout(named= "Corpora")
@Priority(PriorityPrecedence.EARLY)
public class Corpora {

    private final RepositoryService repositoryService;
    private final FactoryService factoryService;
    private final CorpusRepository corpusRepository;

    @Inject
    public Corpora(RepositoryService repositoryService,
                   FactoryService factoryService,
                   CorpusRepository corpusRepository) {
        this.repositoryService = repositoryService;
        this.factoryService = factoryService;
        this.corpusRepository = corpusRepository;
    }

    @ActionLayout(sequence = "1")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> listAll() {
        return repositoryService.allInstances(Corpus.class);
    }

    @ActionLayout(sequence = "2")
    @Action(semantics = SemanticsOf.NON_IDEMPOTENT)
    public Corpus create(String title) {
        final Corpus obj = factoryService.detachedEntity(Corpus.class);
        obj.setTitle(title);
        obj.setAnalyzedAt(new Timestamp(System.currentTimeMillis()));
        repositoryService.persistAndFlush(obj);
        return obj;
    }

    @ActionLayout(sequence = "3")
    @Action(semantics = SemanticsOf.SAFE)
    public List<Corpus> findByTitle(final String title) {
        List<Corpus> answer = new ArrayList<>();
        for (Corpus o : listAll()) {
            if (o.getTitle().equals(title)) {
                answer.add(o);
            }
        }
        return answer;
    }

    /**
     * Title of the corpus that owns {@code domainModel}, or {@code null} when no corpus does.
     *
     * <p><b>Why here.</b>  "Which corpus owns this model" is corpus-aggregate knowledge, and the
     * owning side of the association lives on {@link Corpus}.  It is needed by
     * {@code DomainModel#title()}, which cannot look it up itself: the model has no name of its own
     * and no back-reference to its owner.</p>
     *
     * <p><b>Programmatic, not an action.</b>  A lookup for a title, not something a user invokes —
     * and {@code DomainModel#title()} calls it on <em>every</em> render, so it must not appear in the
     * UI as an action.  A model created by hand ({@code DomainModels#create()}) has no corpus, which
     * is exactly the case that {@code null} reports.</p>
     */
    @Programmatic
    public String titleOf(final DomainModel domainModel) {
        if (domainModel == null) {
            return null;
        }
        return corpusRepository.findByDomainModel(domainModel).stream()
                .map(Corpus::getTitle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
