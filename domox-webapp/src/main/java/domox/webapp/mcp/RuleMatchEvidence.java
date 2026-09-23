package domox.webapp.mcp;

import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.rules.RuleMatch;

/**
 * A serializable, evidence-focused view of a single {@link RuleMatch} for an LLM
 * reviewer. Exposes the matched rule plus the source sentence text so the model can
 * judge validity, without leaking the underlying JPA entities.
 *
 * @param id            the rule-match id
 * @param ruleClassName the fully-qualified rule that fired
 * @param description   human-readable description of the match
 * @param sentenceText  the source-use-case sentence that triggered the match (or null)
 * @param sentenceId    the id of the source sentence (or null)
 */
public record RuleMatchEvidence(
        long id,
        String ruleClassName,
        String description,
        String sentenceText,
        String sentenceId) {

    public static RuleMatchEvidence from(RuleMatch ruleMatch) {
        String sentenceText = null;
        String sentenceId = null;
        final TypedDependency typedDependency = ruleMatch != null ? ruleMatch.getTypedDependency() : null;
        if (typedDependency != null) {
            final Sentence sentence = typedDependency.getSentence();
            if (sentence != null) {
                sentenceText = sentence.getText();
                sentenceId = sentence.getId() != null ? String.valueOf(sentence.getId()) : null;
            }
        }
        return new RuleMatchEvidence(
                ruleMatch.getId(),
                ruleMatch.getRuleClassName(),
                ruleMatch.getDescription(),
                sentenceText,
                sentenceId);
    }
}