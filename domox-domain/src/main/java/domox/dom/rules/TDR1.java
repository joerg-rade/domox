package domox.dom.rules;

import com.deliveredtechnologies.rulebook.annotation.Result;
import com.deliveredtechnologies.rulebook.annotation.Rule;
import com.deliveredtechnologies.rulebook.annotation.Then;
import com.deliveredtechnologies.rulebook.annotation.When;
import com.deliveredtechnologies.rulebook.spring.RuleBean;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TypedDependency;
import domox.dom.uml.Candidate;
import domox.dom.uml.ClassCandidates;
import domox.dom.uml.ClassCdd;
import domox.dom.uml.ClassType;
import jakarta.inject.Inject;

@RuleBean
@Rule(order = 1)
/**
 * Purpose: Identifies entities (classes) from nsubj or nsubjpass dependencies where the verb is the governor and the noun is the dependent.
 * Example: "The customer places an order." → customer and order are identified as classes.
 * Candidate Type: ClassCdd.
 */
public class TDR1 extends TypedDependencyRuleWithPreviousAndNext {

    @Inject
    private ClassCandidates classCandidates;

    @Result
    private String result;

    @Override
    public boolean when() {
        if (currentTd == null) {
            return false;
        }

        boolean answer = false;
        if (currentTd.nsubj() || currentTd.nsubjpass()) {
            if (currentTd.isVerbA() && currentTd.isNounB() && !currentTd.isBasicAttributeB()) {
                answer = true;
            }
        }
        return answer;
    }

    @Then
    public void then() {
        if (previousTd.compound()) {
            result = "compound(" + currentTd.getB() + ") + Compound(" + currentTd.getA() + ")";
        } else {
            result = "nsubj(" + currentTd.getB() + ")";
        }
    }

    @Override
    protected Candidate createNewCandidate(TypedDependency dependency, Sentence sentence) {
        // Create a ClassCdd candidate for this rule
        ClassCdd candidate = classCandidates.findOrCreate(className());
        //FIXME: sentence, dependency need to be taken into consideration
        candidate.setName(currentTd.getB());
        //TODO can we assume, all class candidates are entities?
        //candidate.setClassType(ClassType.ENTITY);
        return candidate;
    }

    @Override
    protected String getResult() {
        return result;
    }

    private String className() {
        if (previousTd.compound()) {
            return currentTd.getB() + capitalizeFirstLetter(currentTd.getA());
        } else {
            return currentTd.getB();
        }
    }

}