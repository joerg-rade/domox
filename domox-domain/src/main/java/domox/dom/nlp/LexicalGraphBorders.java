package domox.dom.nlp;

import domox.dom.crc.model.Candidate;
import domox.dom.crc.model.ReviewStatus;
import domox.dom.rules.RuleMatch;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The <b>candidate-derived border styling of the lexical (DOT) diagram</b>: which lemmas are drawn
 * with an emphasised (bold) border, which with a dashed one, and the DOT attributes that express it.
 * <p>
 * Extracted from {@code LexicalGraphGenerator}: it is the only part of that class that reads the
 * {@link Candidate} snapshot, so keeping it apart keeps the graph algorithm free of the candidate
 * model — and gives the two rules a place to be unit-tested without building a whole graph.
 */
final class LexicalGraphBorders {

    /**
     * Candidate-type marker of the synonym rule match produced by {@code TDR41}.  A candidate that
     * carries a match of this type (either as its {@link RuleMatch#getCandidateName()} or as its
     * {@link RuleMatch#getRelatedCandidateName()}) is part of a synonym pair.
     */
    private static final String SYNONYM_CANDIDATE_TYPE = "SynonymCdd";

    /**
     * Node border style for a term that is part of a synonym: the global {@code rounded,filled}
     * style extended with a dashed border so it reads as a semantic variant of another term.
     */
    static final String SYNONYM_BORDER_STYLE = "rounded,filled,dashed";

    /** Node border width (penwidth) for an approved candidate's emphasised (bold) border. */
    static final double APPROVED_BORDER_PENWIDTH = 3.0;

    private LexicalGraphBorders() {
    }

    /**
     * Lower-cased lemmas of every candidate in the snapshot that is currently approved, i.e. that
     * carries at least one {@link domox.dom.crc.model.Review} whose status is
     * {@link ReviewStatus#APPROVED}.  These lemmas are drawn with a bold border.  A
     * {@code null}/{@code null}-entry free snapshot or one with no approved candidate yields an
     * empty set.
     */
    static Set<String> approvedLemmas(final Collection<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate candidate : candidates) {
            if (candidate == null || candidate.getCandidateName() == null) {
                continue;
            }
            final boolean approved = candidate.getReviews() != null
                    && candidate.getReviews().stream()
                            .anyMatch(review -> review != null && review.getStatus() == ReviewStatus.APPROVED);
            if (approved) {
                lemmas.add(LexicalGraphNodes.lower(candidate.getCandidateName()));
            }
        }
        return lemmas;
    }

    /**
     * Lower-cased lemmas of every term participating in a synonym pair, collected from the
     * {@code SynonymCdd} rule matches carried by the candidate snapshot.  Each such match names the
     * two equivalent terms ({@link RuleMatch#getCandidateName()} and
     * {@link RuleMatch#getRelatedCandidateName()}), both of which are returned.  These lemmas are
     * drawn with a dashed border.
     */
    static Set<String> synonymLemmas(final Collection<Candidate> candidates) {
        final Set<String> lemmas = new HashSet<>();
        for (final Candidate candidate : candidates) {
            if (candidate == null || candidate.getRuleMatches() == null) {
                continue;
            }
            for (final RuleMatch match : candidate.getRuleMatches()) {
                if (match != null && SYNONYM_CANDIDATE_TYPE.equals(match.getCandidateType())) {
                    lemmas.add(LexicalGraphNodes.lower(match.getCandidateName()));
                    lemmas.add(LexicalGraphNodes.lower(match.getRelatedCandidateName()));
                }
            }
        }
        return lemmas;
    }
}
