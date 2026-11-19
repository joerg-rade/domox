package domox.dom.crc.diagram;
import domox.DomainModule;
import domox.dom.crc.model.Candidate;
import domox.dom.crc.service.AttributeCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.nlp.LexicalGraphGenerator;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;
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

import java.util.*;

/**
 * Renders a candidate-centric <em>lexical dependency</em> diagram (Kroki → PDF) for a single
 * {@link Candidate}, comparable to
 * {@link domox.dom.requirements.DocumentLexicalDiagrams#renderDiagram} but scoped to one candidate.
 * <p>
 * The diagram is built by a breadth-first expansion of the candidate-connection graph starting
 * from the candidate, {@link Candidate#getHopDepth()} hops deep (default {@code 1}): from the
 * candidate's own {@link RuleMatch}es, only dependencies whose governor <em>and</em> dependent
 * lemma both correspond to a candidate word are kept.  The retained edges therefore draw the
 * candidate together with every candidate directly connected to it.
 * <p>
 * The diagram is regenerated on <em>every</em> call — it is never cached — because its content
 * reflects the current candidate set, which changes as candidates are reviewed, approved, or
 * rejected.
 */
@Slf4j
@Named(DomainModule.NAMESPACE + ".CandidateDiagrams")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class CandidateDiagrams {

    private final DiagramPort diagramPort;
    private final ClassCandidates classCandidates;
    private final ResponsibilityCandidates responsibilityCandidates;
    private final AttributeCandidates attributeCandidates;
    private final CollaboratorCandidates collaboratorCandidates;

    @Inject
    public CandidateDiagrams(
            DiagramPort diagramPort,
            ClassCandidates classCandidates,
            ResponsibilityCandidates responsibilityCandidates,
            AttributeCandidates attributeCandidates,
            CollaboratorCandidates collaboratorCandidates) {
        this.diagramPort = diagramPort;
        this.classCandidates = classCandidates;
        this.responsibilityCandidates = responsibilityCandidates;
        this.attributeCandidates = attributeCandidates;
        this.collaboratorCandidates = collaboratorCandidates;
    }

    /**
     * Renders a freshly generated PDF {@link Blob} for the candidate-centric lexical dependency
     * graph of {@code candidate} — the candidate and every candidate directly connected to it
     * (see {@link Candidate#getHopDepth()}) — or {@code null} when the candidate is
     * {@code null} or the render fails (e.g. Kroki unavailable).
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
            // Snapshot the current candidate set once per render: the lemma whitelist and the
            // per-lemma candidate lookups are derived from the same lists, so the graph is built
            // against a consistent candidate set.
            final List<Candidate> allCandidates = CandidateGraphModel.snapshot(
                    classCandidates, responsibilityCandidates, attributeCandidates, collaboratorCandidates);
            final Set<String> candidateLemmas = CandidateNeighbourhood.candidateLemmas(allCandidates);
            final Map<String, List<Candidate>> candidatesByLemma = CandidateNeighbourhood.candidatesByLemma(allCandidates);
            final List<TypedDependency> dependencies = CandidateNeighbourhood.collectDependencies(
                    List.of(candidate), candidateLemmas, candidatesByLemma, candidate.getHopDepth());
            dotCode = new LexicalGraphGenerator().generateGraphvizGraph(dependencies, allCandidates);
            final byte[] bytes = diagramPort.buildLexicalGraphDiagram(dotCode);
            final String fileName = candidate.getCandidateName() + "-diagram.pdf";
            return new Blob(fileName, DomainConstants.PDF_MIME_TYPE, bytes);
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

}
