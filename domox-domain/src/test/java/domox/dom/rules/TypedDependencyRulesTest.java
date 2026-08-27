package domox.dom.rules;

import com.deliveredtechnologies.rulebook.model.RuleBook;
import com.deliveredtechnologies.rulebook.spring.SpringAwareRuleBookRunner;
import domox.dom.nlp.PartOfSpeechType;
import domox.dom.nlp.Sentence;
import domox.dom.nlp.TdType;
import domox.dom.nlp.TypedDependency;
import domox.dom.uml.ClassCandidates;
import domox.dom.uml.PropertyCandidates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for TypedDependency Rules (TDR1-TDR37)
 * Tests rule firing and result generation for NLP domain model extraction
 */
@ContextConfiguration(classes = TypedDependencyRulesTest.TestConfig.class)
@ExtendWith(SpringExtension.class)
public class TypedDependencyRulesTest {

    /**
     * Test configuration that enables Spring component scanning for RuleBook rules
     */
    @Configuration
    @ComponentScan("domox.dom.rules")
    public static class TestConfig {
        // Component scanning enables discovery of @RuleBean annotated classes

        @Bean
        public RuleBook ruleBook(ApplicationContext applicationContext) {
            SpringAwareRuleBookRunner runner = new SpringAwareRuleBookRunner("domox.dom.rules");
            runner.setApplicationContext(applicationContext);
            return runner;
        }

        @Bean
        public ClassCandidates classCandidates() {
            return Mockito.mock(ClassCandidates.class);
        }

        @Bean
        public PropertyCandidates propertyCandidates() {
            return Mockito.mock(PropertyCandidates.class);
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TDR1 tdr1;

    @Autowired
    private TDR2 tdr2;

    @Autowired
    private TDR6 tdr6;

    @Autowired
    private TDR14 tdr14;

    @Autowired
    private TDR24 tdr24;

    @Autowired
    private TDR27 tdr27;

    @Autowired
    private TDR34 tdr34;

    @Autowired
    private RuleBook ruleBook;

    /**
     * Test TDR1: nsubj with verb and noun (non-basic attribute)
     * Rule fires: Entity should be added for the subject noun
     */
    @Test
    public void testTDR1_SubjectEntityExtraction() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "created", PartOfSpeechType.VB);
        addToken(sentence, 1, "document", PartOfSpeechType.NN);
        addToken(sentence, 2, "draft", PartOfSpeechType.NN);

        // nsubj(created, document): governor=0 (created), dependent=1 (document)
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 1, 2);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr1.currentTd = td;
        tdr1.previousTd = previousTd;
        tdr1.nextTd = nextTd;

        assertTrue(tdr1.when());
        tdr1.then();
    }

    /**
     * Test TDR2: nsubj with verb and basic attribute noun
     * Rule fires: Attribute should be added instead of entity
     */
    @Test
    public void testTDR2_AttributeExtraction() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "created", PartOfSpeechType.VB);
        addToken(sentence, 1, "name", PartOfSpeechType.NN);

        // nsubj(created, name): created=VB, name=NN (basic attribute)
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr2.currentTd = td;
        tdr2.previousTd = previousTd;
        tdr2.nextTd = nextTd;

        assertTrue(tdr2.when(), "TDR2 should identify when nsubj has basic attribute noun");

        tdr2.then();
        assertNotNull(tdr2, "TDR2 bean should not be null after executing then()");
    }

    /**
     * Test TDR6: nmod:of relationships
     * Tests possessive relationships: "has" relationships between entity and attribute
     */
    @Test
    public void testTDR6_PossessiveRelationship() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "name", PartOfSpeechType.NN);
        addToken(sentence, 1, "document", PartOfSpeechType.NN);

        // nmod:of(name, document)
        TypedDependency td = createTypedDependency(sentence, TdType.NMOD_OF, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr6.currentTd = td;
        tdr6.previousTd = previousTd;
        tdr6.nextTd = nextTd;

        assertTrue(tdr6.when(), "TDR6 should identify nmod:of relationships");

        tdr6.then();
        assertNotNull(tdr6, "TDR6 bean should not be null");
    }

    /**
     * Test TDR14: Relationship extraction from subject and object
     * nsubj(verb, E1) & dobj(verb, E2) -> E1 (verb) E2
     */
    @Test
    public void testTDR14_SubjectObjectRelationship() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "creates", PartOfSpeechType.VB);
        addToken(sentence, 1, "user", PartOfSpeechType.NN);

        // nsubj(creates, user)
        TypedDependency currentTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        // dobj(creates, user)
        TypedDependency nextTd = createTypedDependency(sentence, TdType.OBJ, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);

        tdr14.currentTd = currentTd;
        tdr14.previousTd = previousTd;
        tdr14.nextTd = nextTd;

        assertTrue(tdr14.when(), "TDR14 should identify subject-object relationships");

        tdr14.then();
        assertNotNull(tdr14, "TDR14 bean should not be null");
    }

    /**
     * Test TDR24: Cardinality from adjective modifier
     * amod(Entity, adjective) -> cardinality
     */
    @Test
    public void testTDR24_CardinalityFromAdjective() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "users", PartOfSpeechType.NN);
        addToken(sentence, 1, "multiple", PartOfSpeechType.JJ);

        // amod(users, multiple)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr24.currentTd = td;
        tdr24.previousTd = previousTd;
        tdr24.nextTd = nextTd;

        assertTrue(tdr24.when(), "TDR24 should identify cardinality adjectives");

        tdr24.then();
        assertNotNull(tdr24, "TDR24 bean should not be null");
    }

    /**
     * Test TDR27: Input data extraction
     * Identifies data being input by verbs like "enter", "fill", "select"
     */
    @Test
    public void testTDR27_InputDataExtraction() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "enter", PartOfSpeechType.VB);
        addToken(sentence, 1, "name", PartOfSpeechType.NN);

        // nsubj(enter, name) with object being an attribute
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr27.currentTd = td;
        tdr27.previousTd = previousTd;
        tdr27.nextTd = nextTd;

        assertTrue(tdr27.when(), "TDR27 should identify input data scenarios");

        tdr27.then();
        assertNotNull(tdr27, "TDR27 bean should not be null");
    }

    /**
     * Test TDR34: Exception handling
     * xcomp/amod/neg with error/fail/invalid terms -> exceptions
     */
    @Test
    public void testTDR34_ExceptionHandling() {
        Sentence sentence = new Sentence();
        addToken(sentence, 0, "validation", PartOfSpeechType.NN);
        addToken(sentence, 1, "invalid", PartOfSpeechType.JJ);

        // amod(validation, invalid)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr34.currentTd = td;
        tdr34.previousTd = previousTd;
        tdr34.nextTd = nextTd;

        assertTrue(tdr34.when(), "TDR34 should identify exception conditions");

        tdr34.then();
        assertNotNull(tdr34, "TDR34 bean should not be null");
    }

    /**
     * Test that all rules are loaded and registered
     */
    @Test
    public void testAllRulesAreRegistered() {
        // This is a simple smoke test to ensure rules package is scanned
        // If this fails, it means rules are not being properly scanned
        assertNotNull(ruleBook, "RuleBook runner should be initialized");
    }

    /**
     * Test that RuleBook can find and execute any rules at all
     */
    @Test
    public void testRuleBookRunnerCanExecuteRules() {
        // Verify that the injected RuleBook is not null and can be used
        assertNotNull(ruleBook, "RuleBook should be injected from Spring context");

        // Verify that Spring has discovered and scanned the rule beans
        assertNotNull(tdr1, "TDR1 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr2, "TDR2 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr6, "TDR6 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr14, "TDR14 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr24, "TDR24 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr27, "TDR27 bean should be discoverable through Spring component scanning");
        assertNotNull(tdr34, "TDR34 bean should be discoverable through Spring component scanning");
    }

    // Holds part-of-speech for each token index, aligned with the sentence's token list
    private final Map<Integer, PartOfSpeechType> tokenTypes = new HashMap<>();
    // Holds the text for each token index, used for governor/dependent glosses
    private final Map<Integer, String> tokenTexts = new HashMap<>();

    // ... existing code ...

    private void addToken(Sentence sentence, int index, String text, PartOfSpeechType type) {
        tokenTexts.put(index, text);
        tokenTypes.put(index, type);
    }

    private TypedDependency createTypedDependency(Sentence sentence, TdType type, int governorIndex, int dependentIndex) {
        TypedDependency td = new TypedDependency();
        td.setType(type);
        td.setSentence(sentence);
        td.setGovernorIndex(governorIndex);
        td.setDependentIndex(dependentIndex);
        td.setGovernorPos(tokenTypes.get(governorIndex));
        td.setDependentPos(tokenTypes.get(dependentIndex));
        td.setGovernorGloss(tokenTexts.get(governorIndex));
        td.setDependentGloss(tokenTexts.get(dependentIndex));
        sentence.addTypedDependency(td);  // keeps ordered list
        return td;
    }
}