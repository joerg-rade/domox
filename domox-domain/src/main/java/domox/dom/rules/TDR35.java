package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;

@RuleBean
@Rule(order = 35)
public class TDR35 extends TypedDependencyRule {

    @Override
    @When
    public boolean when() {
        // Guard against null currentTd when not in FactMap
        if (currentTd == null) {
            return false;
        }
        // Branch 1: Dependencies = advcl:if(A,B) OR mark(A,if)
        if (currentTd.advcl() || (currentTd.mark() && isIf(currentTd.getB()))) {
            return true;
        }
        // Branch 2: Dependencies = advmod(A,then) AND advmod(A,else)
        if (currentTd.advmod() && isThen(currentTd.getB()) && hasElseAdvmod(currentTd)) {
            return true;
        }
        // Branch 3: Dependencies = advmod(A,else)
        return currentTd.advmod() && isElse(currentTd.getB());
    }

    @Override
    @Then
    public void then() {
        // Determine which branch of the spec fired
        String keyword;
        boolean skipAdvmod;
        if (currentTd.advcl() || (currentTd.mark() && isIf(currentTd.getB()))) {
            keyword = "if";
            skipAdvmod = true;      // while (TD≠advmod)
        } else if (currentTd.advmod() && isThen(currentTd.getB()) && hasElseAdvmod(currentTd)) {
            keyword = "then";
            skipAdvmod = true;      // while (TD≠advmod)
        } else {
            keyword = "else";
            skipAdvmod = false;     // while (TD≠NULL)
        }

        // System_Actions.add("keyword" + dobj.B + dobj.A)
        String a = currentTd.getA();
        String b = currentTd.getB();
        String keywordAction = "System_Actions.add(\"" + keyword + "\" + " + (b != null ? b : "") + " + " + (a != null ? a : "") + ")";

        // while (TD≠advmod | NULL)
        //   if (TD.B == attributes) System_Actions.add(B)
        List<String> attributeNames = new ArrayList<>();
        List<String> attributeActions = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (skipAdvmod && td.advmod()) {
                    continue; // while loop condition
                }
                if (td.isBasicAttributeB()) {
                    attributeNames.add(td.getB());
                    attributeActions.add("System_Actions.add(" + td.getB() + ")");
                }
            }
        }

        result = keywordAction;
        if (!attributeActions.isEmpty()) {
            result += ", " + String.join(", ", attributeActions);
        }

        // Phase 1: record the matches; dependency and sentence come from the @Given fields
        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "System_Actions",
                    capitalizeFirstLetter(keyword + (b != null ? b : "")),
                    null,
                    null,
                    keywordAction);
            for (String name : attributeNames) {
                ruleMatches.create(
                        currentTd,
                        getRuleName(),
                        "System_Actions",
                        capitalizeFirstLetter(name),
                        null,
                        null,
                        "System_Actions.add(" + name + ")");
            }
        }
    }

    private boolean isIf(String term) {
        return term != null && term.equalsIgnoreCase("if");
    }

    private boolean isThen(String term) {
        return term != null && term.equalsIgnoreCase("then");
    }

    private boolean isElse(String term) {
        return term != null && term.equalsIgnoreCase("else");
    }

    /**
     * Checks whether the sentence contains an advmod dependency whose dependent
     * is "else" (used by the "then ... and ... else" branch of the spec).
     */
    private boolean hasElseAdvmod(TypedDependency current) {
        if (current == null || current.getSentence() == null) {
            return false;
        }
        for (TypedDependency td : current.getSentence().getTypedDependencies()) {
            if (td.advmod() && isElse(td.getB())) {
                return true;
            }
        }
        return false;
    }

}