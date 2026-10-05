package domox.dom.crc;

import domox.Constants;
import domox.DomainModule;
import domox.diagram.DiagramBuilder;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.value.Blob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a candidate-centric <em>lexical dependency</em> diagram (Kroki → PDF) for a single
 * {@link Candidate}, comparable to {@link domox.dom.rqm.Documents#renderDiagram} but scoped to
 * one candidate.
 * <p>
 * The diagram is built from the typed dependencies attached to the candidate's own
 * {@link RuleMatch}es: for every such dependency the <em>other verb or noun</em> — the
 * counterpart lemma on the opposite side of the candidate's word — is looked up against the
 * candidate set, and only dependencies whose governor <em>and</em> dependent lemma both
 * correspond to a candidate word are kept.  The retained edges therefore draw the candidate
 * together with every candidate it is directly connected to in the corpus.
 * <p>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Named(DomainModule.NAMESPACE + ".CandidateDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateDiagrams {

    private static final Logger log = LoggerFactory.getLogger(CandidateDiagrams.class);

    private final DiagramBuilder diagramBuilder;
    private final ClassCandidates classCandidates;
    private final ActionCandidates actionCandidates;
    private final PropertyCandidates propertyCandidates;
    private final AssociationCandidates associationCandidates;

    @Inject
    public CandidateDiagrams(
            DiagramBuilder diagramBuilder,
            ClassCandidates classCandidates,
            ActionCandidates actionCandidates,
            PropertyCandidates propertyCandidates,
            AssociationCandidates associationCandidates) {
        this.diagramBuilder = diagramBuilder;
        this.classCandidates = classCandidates;
        this.actionCandidates = actionCandidates;
        this.propertyCandidates = propertyCandidates;
        this.associationCandidates = associationCandidates;
    }

    /**
     * Renders a freshly generated PDF {@link Blob} for the candidate-centric lexical dependency
     * graph of {@code candidate}, or {@code null} when the candidate is {@code null} or the
     * render fails (e.g. Kroki unavailable).
     */
    @Programmatic
    public Blob renderDiagram(final Candidate candidate) {
        if (candidate == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering candidate diagram for candidate #{} '{}'", id, name);
        String dotCode = null;
        try {
            final Set<String> candidateLemmas = allCandidateLemmas();
            final List<TypedDependency> dependencies = candidate.getRuleMatches().stream()
                    .map(RuleMatch::getTypedDependency)
                    .filter(Objects::nonNull)
                    .filter(td -> bothEndpointsAreCandidates(td, candidateLemmas))
                    .distinct()
                    .collect(Collectors.toList());
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies);
            final byte[] bytes = diagramBuilder.buildLexicalGraphDiagram(dotCode);
            final String fileName = candidate.getCandidateName() + "-diagram.pdf";
            return new Blob(fileName, Constants.pdfMimeType, bytes);
        } catch (Exception e) {
            log.warn("Failed to render candidate diagram for candidate #{} '{}': {}",
                    id, name, e.getMessage());
            if (dotCode != null) {
                log.debug("Candidate diagram DOT for candidate #{} '{}':\n{}", id, name, dotCode);
            }
            log.debug("Failure rendering candidate diagram for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    /**
     * The lower-cased candidate names of all candidate types currently present.  The candidate
     * whose diagram is being rendered is itself a member of one of these lists, so its own word
     * is always an allowed lemma.
     */
    private Set<String> allCandidateLemmas() {
        final Set<String> lemmas = new HashSet<>();
        classCandidates.listAll().forEach(c -> lemmas.add(lower(c.getCandidateName())));
        actionCandidates.listAll().forEach(c -> lemmas.add(lower(c.getCandidateName())));
        propertyCandidates.listAll().forEach(c -> lemmas.add(lower(c.getCandidateName())));
        associationCandidates.listAll().forEach(c -> lemmas.add(lower(c.getCandidateName())));
        return lemmas;
    }

    /**
     * Keeps only those typed dependencies whose governor <em>and</em> dependent lemma both
     * correspond to a candidate word — the candidate itself plus every related candidate looked
     * up from the candidate lists.  Mirrors the candidate-word restriction applied per document
     * by {@link domox.dom.rqm.Documents#renderDiagram}.
     */
    private static boolean bothEndpointsAreCandidates(final TypedDependency td,
                                                      final Set<String> candidateLemmas) {
        return candidateLemmas.contains(lower(td.getGovernorLemma()))
                && candidateLemmas.contains(lower(td.getDependentLemma()));
    }

    private static String lower(final String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}