package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;

import static domox.dom.nlp.TypedDependencyPredicates.*;

@RuleBean
@Rule(order = 34)
public class TDR34 extends TypedDependencyRule {

    public TDR34(NlpProperties nlpProperties) {
        super(nlpProperties);
    }

    @Override
    @When
    public boolean when() {
        if (currentTd == null) {
            return false;
        }
        if (xcomp(currentTd) || amod(currentTd) || neg(currentTd)) {
            String a = currentTd.getA();
            String b = currentTd.getB();
            return isExceptionTerm(a) || isExceptionTerm(b);
        }
        return false;
    }

    @Override
    @Then
    public void then() {
        String a = currentTd.getA();
        String b = currentTd.getB();
        result = "Exceptions.add(" + b + " " + a + ")";

        if (ruleMatches != null && currentTd != null) {
            ruleMatches.create(
                    currentTd,
                    getRuleName(),
                    "Exceptions",
                    capitalizeFirstLetter(a != null ? a : b),
                    null,
                    null,
                    result);
        }
    }

    private boolean isExceptionTerm(String term) {
        return term != null && nlpProperties.getExceptionTerms().contains(term.toLowerCase());
    }
}