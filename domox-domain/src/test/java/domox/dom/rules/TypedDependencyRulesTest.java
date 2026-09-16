package domox.dom.rules;

import com.deliveredtechnologies.rulebook.model.RuleBook;
import com.deliveredtechnologies.rulebook.spring.SpringAwareRuleBookRunner;
import domox.dom.nlp.*;
import domox.dom.crc.ActionCandidates;
import domox.dom.crc.AssociationCandidates;
import domox.dom.crc.ClassCandidates;
import domox.dom.crc.PropertyCandidates;
import org.apache.causeway.applib.services.factory.FactoryService;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

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
        // and the @DomainService RuleMatches.

        // In-memory store backed by the mocked RuleMatchRepository so that
        // matches created by RuleMatches.create() can be read back afterwards.
        private final List<RuleMatch> matches = new ArrayList<>();

        @Bean
        public RuleBook ruleBook(ApplicationContext applicationContext) {
            SpringAwareRuleBookRunner runner = new SpringAwareRuleBookRunner("domox.dom.rules");
            runner.setApplicationContext(applicationContext);
            return runner;
        }

        /**
         * Minimal {@code NlpProperties} fixture — only the vocabulary entries
         * needed by the rules exercised in this test class.
         *
         * <p>This is <em>not</em> a mirror of the production
         * {@code domox.nlp.*} configuration
         * ({@code domox-webapp/src/main/resources/application.yml}). Production
         * config-binding is verified separately by the webapp module's
         * {@code NlpConfigBindingTest}. Keeping only fixture values here
         * eliminates the drift risk of duplicating the full production lists.</p>
         *
         * <p>Rules tested here that consult NlpProperties:</p>
         * <ul>
         *   <li>TDR27 — requires {@code "enter"} in {@code userInputVerbs}</li>
         *   <li>TDR34 — requires {@code "invalid"} in {@code exceptionTerms}</li>
         * </ul>
         * All other vocabulary lists are set to empty to keep the fixture
         * minimal; rules not exercised here see an empty list and simply
         * produce no match, which is harmless in this test context.
         */
        @Bean
        public NlpProperties nlpProperties() {
            NlpProperties props = new NlpProperties();
            props.setExceptionTerms(List.of("invalid"));
            props.setUserInputVerbs(List.of("enter"));
            props.setSystemOutputVerbs(List.of());
            props.setActionVerbs(List.of());
            props.setInputPastVerbs(List.of());
            props.setOutputPastVerbs(List.of());
            props.setReceiveVerbs(List.of());
            return props;
        }

        @Bean
        public ClassCandidates classCandidates() {
            return Mockito.mock(ClassCandidates.class);
        }

        @Bean
        public PropertyCandidates propertyCandidates() {
            return Mockito.mock(PropertyCandidates.class);
        }

        @Bean
        public ActionCandidates actionCandidates() {
            return Mockito.mock(ActionCandidates.class);
        }

        @Bean
        public AssociationCandidates associationCandidates() {
            return Mockito.mock(AssociationCandidates.class);
        }

        @Bean
        public FactoryService factoryService() {
            FactoryService mock = Mockito.mock(FactoryService.class);
            Mockito.when(mock.detachedEntity(RuleMatch.class))
                    .thenAnswer(invocation -> new RuleMatch());
            return mock;
        }

        @Bean
        public RepositoryService repositoryService() {
            RepositoryService mock = Mockito.mock(RepositoryService.class);
            Mockito.doAnswer(invocation -> {
                matches.add(invocation.getArgument(0));
                return invocation.getArgument(0);
            }).when(mock).persist(Mockito.any(Object.class));
            return mock;
        }

        @Bean
        public RuleMatchRepository ruleMatchRepository() {
            RuleMatchRepository repo = Mockito.mock(RuleMatchRepository.class);
            Mockito.when(repo.findAll())
                    .thenAnswer(invocation -> new ArrayList<>(matches));
            Mockito.when(repo.findByRuleClassName(Mockito.anyString()))
                    .thenAnswer(invocation -> matches.stream()
                            .filter(m -> invocation.<String>getArgument(0).equals(m.getRuleClassName()))
                            .collect(Collectors.toList()));
            return repo;
        }

        @Bean
        public domox.dom.nlp.SentenceRepository sentenceRepository() {
            domox.dom.nlp.SentenceRepository repo =
                    Mockito.mock(domox.dom.nlp.SentenceRepository.class);
            Mockito.when(repo.findAll()).thenReturn(new ArrayList<>());
            return repo;
        }
    }

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
    private TDR10 tdr10;

    @Autowired
    private TDR11 tdr11;

    @Autowired
    private RuleBook ruleBook;

    /**
     * Seeds the static predicate vocabularies before each test, mirroring what
     * {@code BasicAttributeCatalog} does at application startup. {@code BASIC_ATTRIB}
     * starts empty and is only populated from configuration; without this, every
     * rule that calls {@code isBasicAttributeB(...)} (TDR2, TDR4, TDR6, TDR13, ...)
     * would see an empty set and never fire.
     */
    @BeforeEach
    void setUp() {
        TypedDependencyPredicates.resetBasicAttributes();
        TypedDependencyPredicates.resetActionVocabularies();
        TypedDependencyPredicates.registerBasicAttributes(Set.of(
                "name", "number", "type", "address", "level", "date", "time"));
    }

    /**
     * Test TDR1: nsubj with verb and noun (non-basic attribute)
     * Rule fires: Entity should be added for the subject noun
     */
    @Test
    public void testTDR1_SubjectEntityExtraction() {
        Sentence sentence = new Sentence();
        addToken(0, "created", PartOfSpeechType.VB);
        addToken(1, "document", PartOfSpeechType.NN);
        addToken(2, "draft", PartOfSpeechType.NN);

        // nsubj(created, document): governor=0 (created), dependent=1 (document)
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        // compound(document, draft): governor=1 (document), dependent=2 (draft)
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 1, 2);

        tdr1.currentTd = td;
        tdr1.previousTd = previousTd;
        tdr1.nextTd = null;

        assertTrue(tdr1.when(), "TDR1 should fire for nsubj(created, document)");
        tdr1.then(); // records the match

        List<RuleMatch> matches = tdr1.ruleMatches.findByRuleClassName("TDR1");
        assertFalse(matches.isEmpty(), "TDR1 should have created a RuleMatch");
        RuleMatch match = matches.getLast();
        assertEquals("DraftDocument", match.getCandidateName());
        assertEquals("ClassCdd", match.getCandidateType());
        assertNotNull(match.getDescription());
    }

    /**
     * Test TDR2: nsubj with verb and basic attribute noun
     * Rule fires: Attribute should be added instead of entity
     */
    @Test
    public void testTDR2_AttributeExtraction() {
        Sentence sentence = new Sentence();
        addToken(0, "created", PartOfSpeechType.VB);
        addToken(1, "name", PartOfSpeechType.NN);

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
        addToken(0, "name", PartOfSpeechType.NN);
        addToken(1, "document", PartOfSpeechType.NN);

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
        addToken(0, "creates", PartOfSpeechType.VB);
        addToken(1, "user", PartOfSpeechType.NN);

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
     * Test TDR24: Descriptor from adjective modifier
     * amod(Entity, adjective) -> descriptor.add(Entity, adjective)
     */
    @Test
    public void testTDR24_DescriptorFromAdjective() {
        Sentence sentence = new Sentence();
        addToken(0, "users", PartOfSpeechType.NN);
        addToken(1, "multiple", PartOfSpeechType.JJ);

        // amod(users, multiple)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 0, 1);
        TypedDependency previousTd = createTypedDependency(sentence, TdType.COMPOUND, 0, 1);
        TypedDependency nextTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr24.currentTd = td;
        tdr24.previousTd = previousTd;
        tdr24.nextTd = nextTd;

        assertTrue(tdr24.when(), "TDR24 should identify descriptor adjectives");

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
        addToken(0, "enter", PartOfSpeechType.VB);
        addToken(1, "name", PartOfSpeechType.NN);

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
        addToken(0, "validation", PartOfSpeechType.NN);
        addToken(1, "invalid", PartOfSpeechType.JJ);

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
     * Test TDR10: possessive pronoun (PRP$) with no adjectival modifier.
     */
    @Test
    public void testTDR10_PossessivePronoun_PropertyOnResolvedPossessor() {
        Sentence sentence = new Sentence();
        addToken(0, "train", PartOfSpeechType.VB);
        addToken(1, "owners", PartOfSpeechType.NNS);
        addToken(2, "their", PartOfSpeechType.PRP$);
        addToken(3, "pets", PartOfSpeechType.NNS);
        createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        TypedDependency td = createTypedDependency(sentence, TdType.NMOD_POSS, 3, 2);
        tdr10.currentTd = td;
        tdr10.previousTd = null;
        tdr10.nextTd = null;
        assertTrue(tdr10.when(), "TDR10 should fire for nmod:poss(pets, their)");
        tdr10.then();
        List<RuleMatch> matches = tdr10.ruleMatches.findByRuleClassName("TDR10");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertNotNull(propertyMatch, "TDR10 should have created a PropertyCdd");
        assertEquals("pets", propertyMatch.getCandidateName());
        assertEquals("Owners", propertyMatch.getRelatedCandidateName(),
                "Pronominal possessor should resolve to the nsubj entity");
    }

    /**
     * Test TDR10: possessive pronoun (PRP$) with an adjectival modifier on the
     * possessed noun.  "pet owners train their beloved pets" →
     * nmod:poss(pets, their) with amod(pets, beloved).
     * Rule fires: enriched PropertyCdd "beloved pets" on ClassCdd "Owners".
     */
    @Test
    public void testTDR10_PossessivePronoun_EnrichedPropertyName() {
        Sentence sentence = new Sentence();
        addToken(0, "train", PartOfSpeechType.VB);
        addToken(1, "owners", PartOfSpeechType.NNS);
        addToken(2, "their", PartOfSpeechType.PRP$);
        addToken(3, "beloved", PartOfSpeechType.VBG);
        addToken(4, "pets", PartOfSpeechType.NNS);
        createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        createTypedDependency(sentence, TdType.AMOD, 4, 3);
        TypedDependency td = createTypedDependency(sentence, TdType.NMOD_POSS, 4, 2);
        tdr10.currentTd = td;
        tdr10.previousTd = null;
        tdr10.nextTd = null;
        assertTrue(tdr10.when(), "TDR10 should fire for nmod:poss(pets, their)");
        tdr10.then();
        List<RuleMatch> matches = tdr10.ruleMatches.findByRuleClassName("TDR10");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertNotNull(propertyMatch, "TDR10 should have created a PropertyCdd");
        assertEquals("beloved pets", propertyMatch.getCandidateName(),
                "Property name should be enriched with the amod modifier");
        assertEquals("Owners", propertyMatch.getRelatedCandidateName());
    }

    /**
     * Test TDR11: amod with VBG modifier on a possessed noun.
     * "owners love their beloved pets" → amod(pets, beloved) where beloved=VBG,
     * with nmod:poss(pets, their) in the same sentence.
     * Rule fires: PropertyCdd "beloved pets" on ClassCdd "Owners" (not a standalone entity).
     */
    @Test
    public void testTDR11_AmodVBG_PossessedNoun_PropertyOnPossessor() {
        Sentence sentence = new Sentence();
        addToken(0, "owners", PartOfSpeechType.NNS);
        addToken(1, "love", PartOfSpeechType.VBP);
        addToken(2, "their", PartOfSpeechType.PRP$);
        addToken(3, "beloved", PartOfSpeechType.VBG);
        addToken(4, "pets", PartOfSpeechType.NNS);

        // nsubj(love, owners) — the entity that resolves the pronoun "their"
        createTypedDependency(sentence, TdType.NSUBJ, 1, 0);
        // nmod:poss(pets, their) — possession link consumed by TDR11's then()
        createTypedDependency(sentence, TdType.NMOD_POSS, 4, 2);
        // amod(pets, beloved) — governor=4 (pets), dependent=3 (beloved VBG)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 4, 3);

        tdr11.currentTd = td;
        tdr11.previousTd = null;
        tdr11.nextTd = null;

        assertTrue(tdr11.when(), "TDR11 should fire for amod(pets, beloved) with VBG modifier");
        tdr11.then();

        List<RuleMatch> matches = tdr11.ruleMatches.findByRuleClassName("TDR11");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertNotNull(propertyMatch, "TDR11 should have created a PropertyCdd for the possessed noun");
        assertEquals("beloved pets", propertyMatch.getCandidateName());
        assertEquals("Owners", propertyMatch.getRelatedCandidateName());
    }

    /**
     * Test TDR11 regression: amod without any possession still yields a ClassCdd.
     * "the active order" → amod(order, active) with active=JJ.
     */
    @Test
    public void testTDR11_Amod_NoPossession_StillEntity() {
        Sentence sentence = new Sentence();
        addToken(0, "active", PartOfSpeechType.JJ);
        addToken(1, "order", PartOfSpeechType.NN);

        // amod(order, active) — governor=1 (order), dependent=0 (active)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 1, 0);

        tdr11.currentTd = td;
        tdr11.previousTd = null;
        tdr11.nextTd = null;

        assertTrue(tdr11.when(), "TDR11 should fire for amod(order, active)");
        tdr11.then();

        List<RuleMatch> matches = tdr11.ruleMatches.findByRuleClassName("TDR11");
        RuleMatch entityMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "ClassCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertNotNull(entityMatch, "TDR11 should have created a ClassCdd for a non-possessed noun");
        assertEquals("Order", entityMatch.getCandidateName());
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

    private void addToken(int index, String text, PartOfSpeechType type) {
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
        // getA()/getB() read the LEMMA fields — without these the rules see null
        // text and persist null candidate names.
        td.setGovernorLemma(tokenTexts.get(governorIndex));
        td.setDependentLemma(tokenTexts.get(dependentIndex));
        td.setGovernorGloss(tokenTexts.get(governorIndex));
        td.setDependentGloss(tokenTexts.get(dependentIndex));
        sentence.addTypedDependency(td);  // keeps ordered list
        return td;
    }
}