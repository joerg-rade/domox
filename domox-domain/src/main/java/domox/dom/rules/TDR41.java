package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import static domox.dom.nlp.TypedDependencyPredicates.*;

/**
 * TDR41 — Synonym identification via apposition and defining constructions.
 * <p>
 * Rule-based synonym detection drawn from {@code .clinerules/memory-bank/SYNONYMS.md}:
 * two terms that stand in a defining/equivalence construction are flagged as synonyms
 * so that downstream modeling can treat them as the same concept rather than two
 * unrelated entities.
 * <p>
 * Two lexico-syntactic triggers are recognised (both require the governor and
 * partner to share the same grammatical category — the POS-Matching heuristic,
 * SYNONYMS.md §2):
 * <ul>
 *   <li><b>Apposition</b> — {@code appos(Head, Alias)} where both governor and
 *       dependent are nouns.  Direct appositive naming ("the store, the shop") is
 *       the dependency-grammar counterpart of the parenthetical /
 *       "also known as" constructions (SYNONYMS.md §1).</li>
 *   <li><b>Defining relative clause</b> — {@code acl:relcl(Head, Marker)} /
 *       {@code acl(Head, Marker)} where the marker verb is a configured
 *       synonym marker ("known", "termed", "called", "referred to as", …).
 *       The partner term Y is resolved by scanning the sentence for a noun
 *       object of that marker verb ("X, also known as Y").</li>
 * </ul>
 * <p>
 * On a match the rule records a {@code SynonymCdd} RuleMatch; Phase 2 creates two
 * {@code ClassCdd} entities linked by an {@code AssociationCdd} of type
 * {@code SYNONYM}.
 */
@RuleBean
@Rule(order = 41)
public class TDR41 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null || currentTd.getSentence() == null) {
            return false;
        }
        // Pattern 1 — apposition: appos(Head, Alias) with both nouns (POS-Matching).
        if (appos(currentTd)) {
            if (!isNounA(currentTd) || !isNounB(currentTd)) {
                return false;
            }
            final String head = currentTd.getA();
            final String alias = currentTd.getB();
            return head != null && alias != null && !head.equalsIgnoreCase(alias);
        }
        // Pattern 2 — defining relative clause: acl:relcl(Head, MarkerVerb).
        if (aclRelcl(currentTd)) {
            if (!isNounA(currentTd)) {
                return false;
            }
            final String marker = currentTd.getB();
            if (marker == null || !isSynonymMarker(marker)) {
                return false;
            }
            final String head = currentTd.getA();
            return resolveSynonymPartner(currentTd, head) != null;
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        String head;
        String alias;
        if (appos(currentTd)) {
            // head = currentTd.getA() (governor), alias = currentTd.getB() (dependent)
            head = currentTd.getA();
            alias = currentTd.getB();
        } else {
            // head = currentTd.getA() (the noun modified by the defining clause),
            // alias = the noun object of the synonym-marker verb.
            head = currentTd.getA();
            alias = resolveSynonymPartner(currentTd, head);
        }

        result = "synonym(" + capitalizeFirstLetter(head) + " == "
                + capitalizeFirstLetter(alias) + ")";

        // Phase 1: record the match
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "SynonymCdd",
                    capitalizeFirstLetter(head),
                    "SynonymPartner",
                    capitalizeFirstLetter(alias),
                    result);
        }
    }

    /**
     * Resolves the synonym partner Y of a defining clause {@code "X, also known as Y"}.
     * <p>
     * Scans the sentence for a dependency whose governor (A) is the marker verb
     * ({@link #currentTd currentTd.getB()}) and whose dependent (B) is a noun —
     * the object of "known/termed/…".  The subject of the clause (which may be the
     * same noun X) and the relative-clause head itself are excluded.
     *
     * @param relcl the {@code acl:relcl}/{@code acl} dependency currently being evaluated
     * @param head  the head noun X (governor of {@code relcl})
     * @return the partner term lemma, or {@code null} if none can be resolved
     */
    private static String resolveSynonymPartner(TypedDependency relcl, String head) {
        if (relcl == null || relcl.getSentence() == null) {
            return null;
        }
        final String marker = relcl.getB();
        for (TypedDependency td : relcl.getSentence().getTypedDependencies()) {
            if (td == relcl) {
                continue;
            }
            if (marker.equals(td.getA())
                    && isNounB(td)
                    && !head.equalsIgnoreCase(td.getB())
                    && !isNsubj(td) && !isNsubjPass(td)) {
                return td.getB();
            }
        }
        return null;
    }
}