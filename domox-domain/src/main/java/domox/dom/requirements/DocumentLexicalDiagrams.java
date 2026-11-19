package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.Sentences;
import domox.dom.nlp.TypedDependency;
import domox.port.DiagramPort;
import domox.port.DomainConstants;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.value.Blob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the document-wide <em>lexical dependency</em> diagram (Kroki → PDF) of a use-case
 * {@link Document}, directly from its current persisted state.
 * <p>
 * This is the diagram half of the {@link Documents} menu.  It used to live on {@code Documents}
 * itself, which made that service responsible for two unrelated things at once — the document
 * lifecycle (create, list, load a {@code UC*.md} file into the corpus) and the rendering of one
 * derived view of a document — and left it holding a {@link DiagramPort} and the whole
 * candidate-vocabulary machinery only for the sake of the render.  The two change for different
 * reasons: the lifecycle changes when the corpus does, the render changes when the diagram does.
 * <p>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Slf4j
@Named(DomainModule.NAMESPACE + ".DocumentLexicalDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class DocumentLexicalDiagrams {

    /** Default rule-match threshold used when lazily building a document's lexical diagram. */
    private static final int DEFAULT_LEXICAL_THRESHOLD = 1;

    private final Sentences sentences;
    private final DiagramPort diagramPort;

    @Inject
    public DocumentLexicalDiagrams(final Sentences sentences, final DiagramPort diagramPort) {
        this.sentences = sentences;
        this.diagramPort = diagramPort;
    }

    /**
     * Renders the document-wide lexical dependency diagram for {@code document}.
     *
     * @param document the document whose sentences are to be diagrammed
     * @return a freshly rendered PDF {@link Blob}, or {@code null} if the document is null, has no
     *         domain model, or the render fails (e.g. Kroki unavailable)
     */
    @Programmatic
    public Blob renderDiagram(final Document document) {
        if (document == null || document.getDomainModel() == null) {
            return null;
        }
        final long id = document.getId();
        final String title = document.getTitle();
        log.debug("Rendering lexical diagram for use-case document #{} '{}'", id, title);
        String dotCode = null;
        try {
            final Set<String> allowedLemmas = candidateLemmas(document.getDomainModel(),
                    DEFAULT_LEXICAL_THRESHOLD);
            final List<TypedDependency> dependencies = sentences.findByDocument(document).stream()
                    .flatMap(sentence -> sentence.getTypedDependencies() != null
                            ? sentence.getTypedDependencies().stream()
                            : java.util.stream.Stream.empty())
                    .filter(td -> isCandidateDependency(td, allowedLemmas))
                    .collect(Collectors.toList());
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies,
                    candidates(document.getDomainModel()));
            final byte[] bytes = diagramPort.buildLexicalGraphDiagram(dotCode);
            final String fileName = document.getTitle() + "-lexical.pdf";
            return new Blob(fileName, DomainConstants.PDF_MIME_TYPE, bytes);
        } catch (Exception e) {
            // Catch Exception (not just RuntimeException): Fuel's HTTP errors (FuelError) are a checked
            // Exception, so one unrenderable document must not tear down the whole list page.
            log.warn("Failed to render lexical diagram for use-case document #{} '{}': {}",
                    id, title, e.getMessage());
            if (dotCode != null) {
                log.debug("Lexical diagram DOT for use-case document #{} '{}':\n{}", id, title, dotCode);
            }
            log.debug("Failure rendering lexical diagram for use-case document #{} '{}'", id, title, e);
            return null;
        }
    }

    /**
     * The lower-cased candidate names of the analysis run.  Class candidates are only
     * eligible when their rule-match count meets {@code threshold}; all candidate types
     * that were actually created contribute their name.
     */
    private Set<String> candidateLemmas(final DomainModel model, final int threshold) {
        final Set<String> lemmas = new HashSet<>();
        if (model.classList != null) {
            model.classList.forEach(cc -> {
                if (cc.getRuleMatchCount() >= threshold) {
                    lemmas.add(lower(cc.getCandidateName()));
                }
            });
        }
        if (model.responsibilityList != null) {
            model.responsibilityList.stream().map(ResponsibilityCdd::getCandidateName).forEach(a -> lemmas.add(lower(a)));
        }
        if (model.attributeList != null) {
            model.attributeList.stream().map(AttributeCdd::getCandidateName).forEach(p -> lemmas.add(lower(p)));
        }
        if (model.collaboratorList != null) {
            model.collaboratorList.stream().map(CollaboratorCdd::getCandidateName).forEach(a -> lemmas.add(lower(a)));
        }
        return lemmas;
    }

    /**
     * The candidate snapshot of the analysis run — every candidate of every type owned by the
     * model.  Passed to {@link LexicalGraphGenerator} so node borders can reflect approval status
     * and synonym membership.
     */
    private List<Candidate> candidates(final DomainModel model) {
        final List<Candidate> candidates = new ArrayList<>();
        if (model.classList != null) {
            candidates.addAll(model.classList);
        }
        if (model.responsibilityList != null) {
            candidates.addAll(model.responsibilityList);
        }
        if (model.attributeList != null) {
            candidates.addAll(model.attributeList);
        }
        if (model.collaboratorList != null) {
            candidates.addAll(model.collaboratorList);
        }
        return candidates;
    }

    /**
     * Keeps only those typed dependencies whose governor <em>and</em> dependent lemma both
     * correspond to a created candidate word — mirroring the previous token-restriction
     * step (an edge is only drawn between two retained words).
     */
    private static boolean isCandidateDependency(final TypedDependency td, final Set<String> allowedLemmas) {
        return allowedLemmas.contains(lower(td.getGovernorLemma()))
                && allowedLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
