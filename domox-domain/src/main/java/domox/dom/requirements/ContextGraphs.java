package domox.dom.requirements;

import domox.DomainModule;
import domox.dom.crc.diagram.ContextGraphJson;
import domox.dom.crc.diagram.ContextGraphJson.Center;
import domox.dom.crc.diagram.ContextGraphJson.SentenceOccurrence;
import domox.dom.crc.model.Candidate;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependencyRepository;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.PriorityPrecedence;
import org.apache.causeway.applib.annotation.Programmatic;

import java.util.List;

/**
 * The <b>context graph</b> service: renders a {@link Candidate}'s name as the centre of a D3
 * force-layout graph, surrounded by one node per {@link Sentence} in which that word occurs.
 *
 * <p><b>What it answers.</b>  The other two per-candidate graphs answer different questions:
 * {@code CandidateD3Graph} shows <em>which other words</em> co-occur with the candidate, and the
 * ontology graph shows <em>which reference vocabulary</em> covers it.  This one shows <em>where the
 * candidate is actually talked about</em> — the sentences that back it — which is what a reviewer
 * needs when deciding whether the evidence really supports the candidate.</p>
 *
 * <p><b>How the sentences are found.</b>  Through {@link TypedDependencyRepository#findByLemma}:
 * the candidate name is split into its words ({@code "PetShop"} → {@code pet}, {@code shop}) and a
 * sentence qualifies only when <em>every</em> word occurs in it as a governor or dependent lemma.
 * Lemma matching (rather than {@code Sentence.text} containment) is what makes "occurs as a word"
 * true — the tokens were lemmatised and POS-tagged by the parser, so {@code "pet"} does not match
 * {@code "petrol"}.</p>
 *
 * <p><b>Why it is its own service.</b>  This is the corpus-evidence axis: it changes when the
 * matching rule, the sentence look-up or the diagram contract changes, and for no other reason.
 * The rule itself is pure and lives in {@link ContextGraphJson} (tested without a container); this
 * class only owns the repository and the {@link Candidate} → payload mapping.</p>
 */
@Slf4j
@Named(DomainModule.NAMESPACE + ".ContextGraphs")
@DomainService
@Priority(PriorityPrecedence.EARLY)
public class ContextGraphs {

    private final TypedDependencyRepository typedDependencyRepository;

    @Inject
    public ContextGraphs(final TypedDependencyRepository typedDependencyRepository) {
        this.typedDependencyRepository = typedDependencyRepository;
    }

    /**
     * Renders a freshly generated D3 force-layout graph JSON string of {@code candidate}'s name
     * occurrences — see the class javadoc — or {@code null} when the candidate is {@code null} or
     * has no name to search for.
     * <p>
     * The graph is regenerated on <em>every</em> call and never cached: the corpus, and therefore
     * the set of sentences that mention the candidate, grows while the application runs.  A
     * candidate whose name occurs nowhere still renders — as a lone hub node — so the reviewer can
     * tell "no evidence" from "graph failed to build".
     */
    @Programmatic
    public String renderContextGraph(final Candidate candidate) {
        if (candidate == null || candidate.getCandidateName() == null) {
            return null;
        }
        final long id = candidate.getId() != null ? candidate.getId() : -1L;
        final String name = candidate.getCandidateName();
        log.debug("Rendering context graph for candidate #{} '{}'", id, name);
        try {
            final String json = ContextGraphJson.render(center(candidate),
                    ContextGraphJson.objectTypeOf(Sentence.class), occurrencesFor(name));
            log.debug("Context graph JSON ({} chars) for candidate #{}", json != null ? json.length() : 0, id);
            return json;
        } catch (Exception e) {
            log.warn("Failed to render context graph for candidate #{} '{}'", id, name, e);
            return null;
        }
    }

    /**
     * The sentences in which {@code name} occurs, in ascending sentence-id order — the look-up
     * half of {@link #renderContextGraph(Candidate)}, exposed separately so it can be exercised
     * without building a payload.
     *
     * @param name the candidate name; {@code null} or blank yields an empty list
     */
    @Programmatic
    public List<SentenceOccurrence> occurrencesFor(final String name) {
        final List<String> parts = ContextGraphJson.wordParts(name);
        if (parts.isEmpty()) {
            return List.of();
        }
        return ContextGraphJson.occurrences(parts, typedDependencyRepository::findByLemma);
    }

    /** The hub node of {@code candidate}: its name, id, Causeway object type and review status. */
    private static Center center(final Candidate candidate) {
        return new Center(candidate.getCandidateName(), candidate.getId(), candidate.getObjectType(),
                candidate.getApprovalCount() > 0);
    }
}

