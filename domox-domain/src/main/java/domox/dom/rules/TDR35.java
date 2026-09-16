package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.TypedDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 35)
public class TDR35 extends TypedDependencyRule {

    private static final Set<String> STOPWORDS = Set.of(
            "be", "been", "being", "am", "is", "are", "was", "were",
            "have", "has", "had",
            "do", "does", "did",
            "will", "would", "shall", "should", "can", "could", "may", "might", "must",
            "able", "unable"
    );

    @Override
    @When
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        // Branch 1: Dependencies = advcl:if(A,B) OR mark(A,if)
        if (advcl(currentTd) || (mark(currentTd) && isIf(currentTd.getB()))) {
            return true;
        }
        // Branch 2: Dependencies = advmod(A,then) AND advmod(A,else)
        if (advmod(currentTd) && isThen(currentTd.getB()) && hasElseAdvmod(currentTd)) {
            return true;
        }
        // Branch 3: Dependencies = advmod(A,else)
        return advmod(currentTd) && isElse(currentTd.getB());
    }

    @Override
    @Then
    public void then() {
        String keyword;
        boolean skipAdvmod;
        if (advcl(currentTd) || (mark(currentTd) && isIf(currentTd.getB()))) {
            keyword = "if";
            skipAdvmod = true;
        } else if (advmod(currentTd) && isThen(currentTd.getB()) && hasElseAdvmod(currentTd)) {
            keyword = "then";
            skipAdvmod = true;
        } else {
            keyword = "else";
            skipAdvmod = false;
        }

        String a = currentTd.getA();
        String b = currentTd.getB();
        String keywordAction = "System_Actions.add(\"" + keyword + "\" + " + (b != null ? b : "") + " + " + (a != null ? a : "") + ")";

        List<String> attributeNames = new ArrayList<>();
        List<String> attributeActions = new ArrayList<>();
        if (currentTd.getSentence() != null) {
            for (TypedDependency td : currentTd.getSentence().getTypedDependencies()) {
                if (skipAdvmod && advmod(td)) {
                    continue;
                }
                if (isBasicAttributeB(td) && !isStopword(td.getB())) {
                    attributeNames.add(td.getB());
                    attributeActions.add("System_Actions.add(" + td.getB() + ")");
                }
            }
        }

        result = keywordAction;
        if (!attributeActions.isEmpty()) {
            result += ", " + String.join(", ", attributeActions);
        }

        if (ruleMatches != null && currentTd != null) {
            if (b != null && isStopword(b)) {
                return;  // Skip — concatenation with an auxiliary/copular verb produces a meaningless candidate name (e.g. "Ifbe")
            }
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "ActionCdd",
                    capitalizeFirstLetter(keyword + (b != null ? b : "")),
                    null,
                    null,
                    keywordAction);
            for (String name : attributeNames) {
                ruleMatches.create(
                        currentTd,
                        getRuleName(),
                        "ActionCdd",
                        capitalizeFirstLetter(name),
                        null,
                        null,
                        "System_Actions.add(" + name + ")");
            }
        }
    }

    private static boolean isStopword(String term) {
        return term != null && STOPWORDS.contains(term.toLowerCase());
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

    private boolean hasElseAdvmod(TypedDependency current) {
        if (current == null || current.getSentence() == null) {
            return false;
        }
        for (TypedDependency td : current.getSentence().getTypedDependencies()) {
            if (advmod(td) && isElse(td.getB())) {
                return true;
            }
        }
        return false;
    }
}