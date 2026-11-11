package domox.webapp.mcp;

import java.util.List;

import jakarta.inject.Inject;

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.service.ReviewAdvisor;
import domox.dom.crc.service.Reviews;
import domox.dom.rdf.OntologyLookup;
import domox.dom.rdf.OntologyTerms;

/**
 * Ontology-assisted candidate review over MCP: lets an LLM reviewer ground an approve/reject decision
 * in the reference vocabularies (Schema.org, BFO, gist, BBO, sBPMN) served by the Fuseki image, instead of
 * relying on its own priors alone.
 *
 * <p>Complements {@link ReviewMcpTools} rather than replacing it: that class owns the queue and the
 * write operations, this one supplies <em>evidence</em> and an <em>advisory</em> suggestion.</p>
 *
 * <p><b>Transaction discipline.</b>  Reading a candidate (and its lazy rule matches) needs a
 * transaction, but the ontology lookup is an HTTP call to the SPARQL endpoint — so the facts are
 * gathered inside a {@link TransactionTemplate} and the lookup happens <em>after</em> it closes.  No
 * database connection is held across the network call.</p>
 *
 * <p><b>The evidence is generic.</b>  The reference ontologies are not domain-specific: most nouns of
 * a use-case corpus will not appear in them, so "no match" must never be read as "not a real term".
 * The tools say so explicitly in their descriptions and in the returned {@code message}.</p>
 */
@Component
public class OntologyReviewMcpTools {

    private final Reviews reviews;
    private final OntologyTerms ontologyTerms;
    private final ReviewAdvisor reviewAdvisor;
    private final TransactionTemplate transactionTemplate;

    @Inject
    public OntologyReviewMcpTools(
            final Reviews reviews,
            final OntologyTerms ontologyTerms,
            final ReviewAdvisor reviewAdvisor,
            final PlatformTransactionManager transactionManager) {
        this.reviews = reviews;
        this.ontologyTerms = ontologyTerms;
        this.reviewAdvisor = reviewAdvisor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @McpTool(
            name = "ontologySearch",
            description = """
                    Look a term up in the reference ontologies (Schema.org, BFO, gist, BPMN) and report what
                    they know about it: which vocabulary it came from, its IRI, label, whether it is a
                    Class, a Property or an Individual, its super-class, the ontology's own definition,
                    and any synonyms. Use this to check whether a candidate name is a real, established
                    concept. NOTE: these vocabularies are generic, so most domain nouns will NOT be
                    found - absence of a match is not evidence that a term is invalid.""")
    public OntologySearchResult ontologySearch(
            @McpToolParam(description = "The term to look up, e.g. a candidate name such as 'Invoice'.")
            final String term) {
        return OntologySearchResult.of(ontologyTerms.lookup(term));
    }

    @McpTool(
            name = "ontologyEvidenceForCandidate",
            description = """
                    Gather ontology evidence for one candidate: look its name up in the reference
                    ontologies, report whether the ontology's kind for the term agrees with the
                    candidate's type, and list any already-approved candidates that share the name.
                    Call this before deciding, together with the rule-match evidence from
                    nextCandidateForReview. Absence of an ontology match is NOT evidence against the
                    candidate - the reference vocabularies are generic.""")
    public CandidateOntologyEvidence ontologyEvidenceForCandidate(
            @McpToolParam(description = "The numeric id of the candidate (as returned by nextCandidateForReview).")
            final long candidateId) {
        final CandidateFacts facts = transactionTemplate.execute(status -> factsFor(candidateId));
        final OntologyLookup lookup = ontologyTerms.lookup(facts.view().candidateName());
        return new CandidateOntologyEvidence(
                facts.view().id(),
                facts.view().candidateName(),
                facts.view().candidateType(),
                lookup.source(),
                String.valueOf(reviewAdvisor.agreement(facts.candidate(), lookup)),
                lookup.terms().stream().map(OntologyTermView::from).toList(),
                facts.duplicates(),
                lookup.message());
    }

    @McpTool(
            name = "suggestReview",
            description = """
                    Suggest what to do with one candidate, derived ONLY from ontology evidence and
                    duplicate detection, and explain the reasons. This is ADVISORY: it does not see the
                    sentence evidence or the review policy, and you remain free to decide the opposite.
                    Use it as one input alongside the rule-match evidence. NOTE: 'no ontology match'
                    yields UNCERTAIN, never a rejection, because the reference vocabularies are generic.""")
    public ReviewSuggestion suggestReview(
            @McpToolParam(description = "The numeric id of the candidate (as returned by nextCandidateForReview).")
            final long candidateId) {
        final CandidateFacts facts = transactionTemplate.execute(status -> factsFor(candidateId));
        final OntologyLookup lookup = ontologyTerms.lookup(facts.view().candidateName());
        final ReviewAdvisor.Suggestion suggestion =
                reviewAdvisor.suggest(facts.candidate(), lookup, facts.duplicateCandidates());
        return new ReviewSuggestion(
                facts.view().id(),
                facts.view().candidateName(),
                suggestion.decision().name(),
                suggestion.rationale() != null ? suggestion.rationale().name() : null,
                suggestion.confidence().name(),
                suggestion.reasons());
    }

    /** Everything read inside the transaction, ready to be used after it closes. */
    private record CandidateFacts(
            Candidate candidate,
            CandidateView view,
            List<Candidate> duplicateCandidates,
            List<DuplicateRef> duplicates) {
    }

    private CandidateFacts factsFor(final long candidateId) {
        final Candidate candidate = requireCandidate(candidateId);
        final List<Candidate> duplicateCandidates = reviews.findApprovedWithSameName(candidate);
        return new CandidateFacts(
                candidate,
                CandidateView.from(candidate),
                duplicateCandidates,
                duplicateCandidates.stream().map(DuplicateRef::from).toList());
    }

    private Candidate requireCandidate(final long candidateId) {
        final Candidate candidate = reviews.findByUniqueId(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("No candidate found with id " + candidateId);
        }
        return candidate;
    }
}
