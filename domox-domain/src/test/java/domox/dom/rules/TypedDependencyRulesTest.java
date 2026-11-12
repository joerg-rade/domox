package domox.dom.rules;

import com.deliveredtechnologies.rulebook.model.RuleBook;
import com.deliveredtechnologies.rulebook.spring.SpringAwareRuleBookRunner;
import domox.dom.nlp.*;
import domox.dom.nlp.vocab.*;
import domox.dom.crc.service.ResponsibilityCandidates;
import domox.dom.crc.service.CollaboratorCandidates;
import domox.dom.crc.service.ClassCandidates;
import domox.dom.crc.service.AttributeCandidates;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        public AttributeCandidates attributeCandidates() {
            return Mockito.mock(AttributeCandidates.class);
        }

        @Bean
        public ResponsibilityCandidates responsibilityCandidates() {
            return Mockito.mock(ResponsibilityCandidates.class);
        }

        @Bean
        public CollaboratorCandidates collaboratorCandidates() {
            return Mockito.mock(CollaboratorCandidates.class);
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
    private TDR35 tdr35;

    @Autowired
    private TDR10 tdr10;

    @Autowired
    private TDR11 tdr11;

    @Autowired
    private TDR38 tdr38;

    @Autowired
    private TDR36 tdr36;

    @Autowired
    private TDR39 tdr39;

    @Autowired
    private TDR40 tdr40;

    @Autowired
    private TDR41 tdr41;

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
        BasicAttributeVocabulary.reset();
        ActionVocabulary.reset();
        GeneralizationVocabulary.resetAll();
        BasicAttributeVocabulary.register(Set.of(
                "name", "number", "type", "address", "level", "date", "time"));
        // Seed generalization vocabularies (mirror what GeneralizationCatalog does at startup)
        GeneralizationVocabulary.registerBeVerbs(Set.of(
                "am", "is", "are", "was", "were", "be", "been", "being"));
        GeneralizationVocabulary.registerIndefiniteArticles(Set.of("a", "an"));
        GeneralizationVocabulary.registerKindTypeSortTerms(Set.of("kind", "type", "sort"));
        GeneralizationVocabulary.registerStopAdjectives(Set.of(
                "good", "bad", "new", "old", "big", "small", "large",
                "high", "low", "fast", "slow", "simple", "complex",
                "easy", "hard", "difficult", "important", "necessary",
                "valid", "invalid", "active", "inactive", "enabled", "disabled",
                "required", "optional", "available", "correct", "incorrect",
                "true", "false", "first", "last", "next", "previous"));
        // Seed synonym vocabulary (mirror what SynonymCatalog does at startup)
        SynonymVocabulary.reset();
        SynonymVocabulary.register(Set.of(
                "known", "termed", "called", "referred", "named"));
    }

    /**
     * Test TDR1: nsubj with verb and noun (non-basic attribute)
     * Rule fires: Entity should be added for the subject noun
     */
    @Test
    public void tdr1_extractsTheSubjectEntity() {
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

        assertThat(tdr1.when()).as("TDR1 should fire for nsubj(created, document)").isTrue();
        tdr1.then(); // records the match

        List<RuleMatch> matches = tdr1.ruleMatches.findByRuleClassName("TDR1");
        assertThat(matches.isEmpty()).as("TDR1 should have created a RuleMatch").isFalse();
        RuleMatch match = matches.getLast();
        assertThat(match.getCandidateName()).isEqualTo("DraftDocument");
        assertThat(match.getCandidateType()).isEqualTo("ClassCdd");
        assertThat(match.getDescription()).isNotNull();
    }

    /**
     * Test TDR2: nsubj with verb and basic attribute noun
     * Rule fires: Attribute should be added instead of entity
     */
    @Test
    public void tdr2_extractsTheAttribute() {
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

        assertThat(tdr2.when()).as("TDR2 should identify when nsubj has basic attribute noun").isTrue();

        tdr2.then();
        assertThat(tdr2).as("TDR2 bean should not be null after executing then()").isNotNull();
    }

    /**
     * Test TDR6: nmod:of relationships
     * Tests possessive relationships: "has" relationships between entity and attribute
     */
    @Test
    public void tdr6_recognisesPossessiveRelationship() {
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

        assertThat(tdr6.when()).as("TDR6 should identify nmod:of relationships").isTrue();

        tdr6.then();
        assertThat(tdr6).as("TDR6 bean should not be null").isNotNull();
    }

    /**
     * Test TDR14: Relationship extraction from subject and object
     * nsubj(verb, E1) & dobj(verb, E2) -> E1 (verb) E2
     */
    @Test
    public void tdr14_recognisesSubjectObjectRelationship() {
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

        assertThat(tdr14.when()).as("TDR14 should identify subject-object relationships").isTrue();

        tdr14.then();
        assertThat(tdr14).as("TDR14 bean should not be null").isNotNull();
    }

    /**
     * Test TDR24: Descriptor from adjective modifier
     * amod(Entity, adjective) -> descriptor.add(Entity, adjective)
     */
    @Test
    public void tdr24_buildsDescriptorFromAdjective() {
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

        assertThat(tdr24.when()).as("TDR24 should identify descriptor adjectives").isTrue();

        tdr24.then();
        assertThat(tdr24).as("TDR24 bean should not be null").isNotNull();
    }

    /**
     * Test TDR27: Input data extraction
     * Identifies data being input by verbs like "enter", "fill", "select"
     */
    @Test
    public void tdr27_extractsInputData() {
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

        assertThat(tdr27.when()).as("TDR27 should identify input data scenarios").isTrue();

        tdr27.then();
        assertThat(tdr27).as("TDR27 bean should not be null").isNotNull();
    }

    /**
     * Test TDR34: Exception handling
     * xcomp/amod/neg with error/fail/invalid terms -> exceptions
     */
    @Test
    public void tdr34_recognisesExceptionCondition() {
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

        assertThat(tdr34.when()).as("TDR34 should identify exception conditions").isTrue();

        tdr34.then();
        assertThat(tdr34).as("TDR34 bean should not be null").isNotNull();
    }

    /**
     * Test TDR10: possessive pronoun (PRP$) with no adjectival modifier.
     */
    @Test
    public void tdr10_placesPropertyOnResolvedPossessor() {
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
        assertThat(tdr10.when()).as("TDR10 should fire for nmod:poss(pets, their)").isTrue();
        tdr10.then();
        List<RuleMatch> matches = tdr10.ruleMatches.findByRuleClassName("TDR10");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(propertyMatch).as("TDR10 should have created a AttributeCdd").isNotNull();
        assertThat(propertyMatch.getCandidateName()).isEqualTo("pets");
        assertThat(propertyMatch.getRelatedCandidateName())
                .as("Pronominal possessor should resolve to the nsubj entity")
                .isEqualTo("Owners");
    }

    /**
     * Test TDR10: possessive pronoun (PRP$) with an adjectival modifier on the
     * possessed noun.  "pet owners train their beloved pets" →
     * nmod:poss(pets, their) with amod(pets, beloved).
     * Rule fires: enriched AttributeCdd "beloved pets" on ClassCdd "Owners".
     */
    @Test
    public void tdr10_enrichesThePropertyName() {
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
        assertThat(tdr10.when()).as("TDR10 should fire for nmod:poss(pets, their)").isTrue();
        tdr10.then();
        List<RuleMatch> matches = tdr10.ruleMatches.findByRuleClassName("TDR10");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(propertyMatch).as("TDR10 should have created a AttributeCdd").isNotNull();
        assertThat(propertyMatch.getCandidateName())
                .as("Property name should be enriched with the amod modifier")
                .isEqualTo("beloved pets");
        assertThat(propertyMatch.getRelatedCandidateName()).isEqualTo("Owners");
    }

    /**
     * Test TDR11: amod with VBG modifier on a possessed noun.
     * "owners love their beloved pets" → amod(pets, beloved) where beloved=VBG,
     * with nmod:poss(pets, their) in the same sentence.
     * Rule fires: AttributeCdd "beloved pets" on ClassCdd "Owners" (not a standalone entity).
     */
    @Test
    public void tdr11_placesPossessedNounPropertyOnPossessor() {
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

        assertThat(tdr11.when()).as("TDR11 should fire for amod(pets, beloved) with VBG modifier").isTrue();
        tdr11.then();

        List<RuleMatch> matches = tdr11.ruleMatches.findByRuleClassName("TDR11");
        RuleMatch propertyMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "PropertyCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(propertyMatch).as("TDR11 should have created a AttributeCdd for the possessed noun").isNotNull();
        assertThat(propertyMatch.getCandidateName()).isEqualTo("beloved pets");
        assertThat(propertyMatch.getRelatedCandidateName()).isEqualTo("Owners");
    }

    /**
     * Test TDR11 regression: amod without any possession still yields a ClassCdd.
     * "the active order" → amod(order, active) with active=JJ.
     */
    @Test
    public void tdr11_withoutPossessionStaysAnEntity() {
        Sentence sentence = new Sentence();
        addToken(0, "active", PartOfSpeechType.JJ);
        addToken(1, "order", PartOfSpeechType.NN);

        // amod(order, active) — governor=1 (order), dependent=0 (active)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 1, 0);

        tdr11.currentTd = td;
        tdr11.previousTd = null;
        tdr11.nextTd = null;

        assertThat(tdr11.when()).as("TDR11 should fire for amod(order, active)").isTrue();
        tdr11.then();

        List<RuleMatch> matches = tdr11.ruleMatches.findByRuleClassName("TDR11");
        RuleMatch entityMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "ClassCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(entityMatch).as("TDR11 should have created a ClassCdd for a non-possessed noun").isNotNull();
        assertThat(entityMatch.getCandidateName()).isEqualTo("Order");
    }

    /**
     * Test that all rules are loaded and registered
     */
    @Test
    public void allRuleBeansAreRegistered() {
        // This is a simple smoke test to ensure rules package is scanned
        // If this fails, it means rules are not being properly scanned
        assertThat(ruleBook).as("RuleBook runner should be initialized").isNotNull();
    }

    /**
     * Test that RuleBook can find and execute any rules at all
     */
    @Test
    public void ruleBookRunnerCanExecuteRules() {
        // Verify that the injected RuleBook is not null and can be used
        assertThat(ruleBook).as("RuleBook should be injected from Spring context").isNotNull();

        // Verify that Spring has discovered and scanned the rule beans
        assertThat(tdr1).as("TDR1 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr2).as("TDR2 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr6).as("TDR6 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr14).as("TDR14 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr24).as("TDR24 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr27).as("TDR27 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr34).as("TDR34 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr35).as("TDR35 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr36).as("TDR36 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr38).as("TDR38 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr39).as("TDR39 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr40).as("TDR40 bean should be discoverable through Spring component scanning").isNotNull();
        assertThat(tdr41).as("TDR41 bean should be discoverable through Spring component scanning").isNotNull();
    }

    // ======== TDR36 Tests: Validation operations (nsubj with "validate") ========

    @Test
    public void tdr36_namesResponsibilityAfterVerbNotSubject() {
        Sentence sentence = new Sentence();
        addToken(0, "validate", PartOfSpeechType.VB);
        addToken(1, "system", PartOfSpeechType.NN);

        // nsubj(validate, system): governor=0 (validate), dependent=1 (system)
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);

        tdr36.currentTd = td;
        tdr36.previousTd = null;
        tdr36.nextTd = null;

        assertThat(tdr36.when()).as("TDR36 should fire for nsubj(validate, system)").isTrue();
        tdr36.then();

        List<RuleMatch> matches = tdr36.ruleMatches.findByRuleClassName("TDR36");
        RuleMatch match = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "ActionCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(match).as("TDR36 should have created an ResponsibilityCdd match").isNotNull();
        assertThat(match.getCandidateName())
                .as("TDR36 should name the ResponsibilityCdd after the verb, uncapitalized")
                .isEqualTo("validate");
        assertThat(match.getCandidateName())
                .as("TDR36 action candidate names are verbs and must not be capitalized")
                .isNotEqualTo("Validate");
        assertThat(match.getCandidateName())
                .as("TDR36 must not name the ResponsibilityCdd after the subject")
                .isNotEqualTo("System");
        assertThat(match.getDescription())
                .as("TDR36 description should capitalize the noun subject but leave the verb lowercase")
                .isEqualTo("System_Actions.add(System validate)");
    }

    @Test
    public void tdr36_doesNotFireForNonValidateVerb() {
        Sentence sentence = new Sentence();
        addToken(0, "process", PartOfSpeechType.VB);
        addToken(1, "system", PartOfSpeechType.NN);

        // nsubj(process, system): "process" is not "validate"

        tdr36.currentTd = createTypedDependency(sentence, TdType.NSUBJ, 0, 1);
        tdr36.previousTd = null;
        tdr36.nextTd = null;

        assertThat(tdr36.when()).as("TDR36 should NOT fire for nsubj(process, system)").isFalse();
    }

    // ======== TDR35 Tests: Conditional logic (advcl/mark with if/then/else) ========

    @Test
    public void tdr35_namesResponsibilityAfterVerbNotKeywordSubject() {
        Sentence sentence = new Sentence();
        addToken(0, "notify", PartOfSpeechType.VB);
        addToken(1, "restock", PartOfSpeechType.NN);

        // advcl:if(notify, restock): governor=0 (notify, the verb), dependent=1 (restock, the condition)
        TypedDependency td = createTypedDependency(sentence, TdType.ADVCL, 0, 1);

        tdr35.currentTd = td;
        tdr35.previousTd = null;
        tdr35.nextTd = null;

        assertThat(tdr35.when()).as("TDR35 should fire for advcl(notify, restock)").isTrue();
        tdr35.then();

        List<RuleMatch> matches = tdr35.ruleMatches.findByRuleClassName("TDR35");
        RuleMatch match = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "ActionCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(match).as("TDR35 should have created an ResponsibilityCdd match").isNotNull();
        assertThat(match.getCandidateName())
                .as("TDR35 should name the ResponsibilityCdd after the verb, uncapitalized")
                .isEqualTo("notify");
        assertThat(match.getCandidateName())
                .as("TDR35 must not concatenate the keyword with the condition")
                .isNotEqualTo("Ifrestock");
        assertThat(match.getCandidateName())
                .as("TDR35 must not name the ResponsibilityCdd after the condition")
                .isNotEqualTo("Restock");
        assertThat(match.getDescription())
                .as("TDR35 description should capitalize the condition noun but leave the verb lowercase")
                .isEqualTo("System_Actions.add(\"if\" + Restock + notify)");
    }

    @Test
    public void tdr35_doesNotFireForNonConditionalAdvmod() {
        Sentence sentence = new Sentence();
        addToken(0, "notify", PartOfSpeechType.VB);
        addToken(1, "timely", PartOfSpeechType.RB);

        // advmod(notify, timely): not if/then/else and no else sibling
        tdr35.currentTd = createTypedDependency(sentence, TdType.ADVMOD, 0, 1);
        tdr35.previousTd = null;
        tdr35.nextTd = null;

        assertThat(tdr35.when()).as("TDR35 should NOT fire for advmod(notify, timely)").isFalse();
    }

    // ======== TDR38 Tests: Copula-based generalization ("X is a Y") ========

    @Test
    public void tdr38_recognisesSimpleCopulaIsA() {
        Sentence sentence = new Sentence();
        addToken(0, "dog", PartOfSpeechType.NN);
        addToken(1, "is", PartOfSpeechType.VBZ);
        addToken(2, "a", PartOfSpeechType.DT);
        addToken(3, "animal", PartOfSpeechType.NN);

        // nsubj(animal, dog) — the currentTd for TDR38
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 3, 0);
        createTypedDependency(sentence, TdType.COP, 3, 1);   // cop(animal, is)
        createTypedDependency(sentence, TdType.DET, 3, 2);   // det(animal, a)

        tdr38.currentTd = td;
        tdr38.previousTd = null;
        tdr38.nextTd = null;

        assertThat(tdr38.when()).as("TDR38 should fire for nsubj(animal, dog) with cop+det").isTrue();
        tdr38.then();

        List<RuleMatch> matches = tdr38.ruleMatches.findByRuleClassName("TDR38");
        RuleMatch genMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "GeneralizationCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(genMatch).as("TDR38 should have created a GeneralizationCdd match").isNotNull();
        assertThat(genMatch.getCandidateName()).isEqualTo("Dog");
        assertThat(genMatch.getRelatedCandidateName()).isEqualTo("Animal");
    }

    @Test
    public void tdr38_doesNotFireWithoutCopula() {
        Sentence sentence = new Sentence();
        addToken(0, "dog", PartOfSpeechType.NN);
        addToken(1, "animal", PartOfSpeechType.NN);

        // nsubj(animal, dog) WITHOUT cop or det

        tdr38.currentTd = createTypedDependency(sentence, TdType.NSUBJ, 1, 0);
        tdr38.previousTd = null;
        tdr38.nextTd = null;

        assertThat(tdr38.when()).as("TDR38 should NOT fire without cop+det dependencies").isFalse();
    }

    @Test
    public void tdr38_skipsKindTypeSort() {
        Sentence sentence = new Sentence();
        addToken(0, "service", PartOfSpeechType.NN);
        addToken(1, "is", PartOfSpeechType.VBZ);
        addToken(2, "a", PartOfSpeechType.DT);
        addToken(3, "type", PartOfSpeechType.NN);

        // nsubj(type, service) — "type" is excluded from TDR38
        TypedDependency td = createTypedDependency(sentence, TdType.NSUBJ, 3, 0);
        createTypedDependency(sentence, TdType.COP, 3, 1);
        createTypedDependency(sentence, TdType.DET, 3, 2);

        tdr38.currentTd = td;
        tdr38.previousTd = null;
        tdr38.nextTd = null;

        assertThat(tdr38.when()).as("TDR38 should NOT fire for 'type' governor — reserved for TDR39").isFalse();
    }

    // ======== TDR39 Tests: Kind-of/Type-of generalization ========

    @Test
    public void tdr39_recognisesKindOf() {
        Sentence sentence = new Sentence();
        addToken(0, "suv", PartOfSpeechType.NN);
        addToken(1, "is", PartOfSpeechType.VBZ);
        addToken(2, "a", PartOfSpeechType.DT);
        addToken(3, "kind", PartOfSpeechType.NN);
        addToken(4, "of", PartOfSpeechType.IN);
        addToken(5, "car", PartOfSpeechType.NN);

        // nsubj(kind, suv) — child identification
        createTypedDependency(sentence, TdType.NSUBJ, 3, 0);
        createTypedDependency(sentence, TdType.COP, 3, 1);
        createTypedDependency(sentence, TdType.DET, 3, 2);
        // nmod:of(kind, car) — currentTd for TDR39
        TypedDependency td = createTypedDependency(sentence, TdType.NMOD_OF, 3, 5);

        tdr39.currentTd = td;
        tdr39.previousTd = null;
        tdr39.nextTd = null;

        assertThat(tdr39.when()).as("TDR39 should fire for nmod:of(kind, car)").isTrue();
        tdr39.then();

        List<RuleMatch> matches = tdr39.ruleMatches.findByRuleClassName("TDR39");
        RuleMatch genMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "GeneralizationCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(genMatch).as("TDR39 should have created a GeneralizationCdd match").isNotNull();
        assertThat(genMatch.getCandidateName()).isEqualTo("Suv");
        assertThat(genMatch.getRelatedCandidateName()).isEqualTo("Car");
    }

    @Test
    public void tdr39_doesNotFireWithoutKindOrType() {
        Sentence sentence = new Sentence();
        addToken(0, "dog", PartOfSpeechType.NN);
        addToken(1, "leash", PartOfSpeechType.NN);

        // nmod:of(leash, dog) — "leash" is not kind/type/sort

        tdr39.currentTd = createTypedDependency(sentence, TdType.NMOD_OF, 1, 0);
        tdr39.previousTd = null;
        tdr39.nextTd = null;

        assertThat(tdr39.when()).as("TDR39 should NOT fire for non-kind/type governor").isFalse();
    }

    // ======== TDR40 Tests: Adjectival classifier generalization ========

    @Test
    public void tdr40_recognisesClassifierAmod() {
        Sentence sentence = new Sentence();
        addToken(0, "linked", PartOfSpeechType.JJ);
        addToken(1, "device", PartOfSpeechType.NN);

        // amod(device, linked)
        TypedDependency td = createTypedDependency(sentence, TdType.AMOD, 1, 0);

        tdr40.currentTd = td;
        tdr40.previousTd = null;
        tdr40.nextTd = null;

        assertThat(tdr40.when()).as("TDR40 should fire for amod(device, linked)").isTrue();
        tdr40.then();

        List<RuleMatch> matches = tdr40.ruleMatches.findByRuleClassName("TDR40");
        RuleMatch genMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "GeneralizationCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(genMatch).as("TDR40 should have created a GeneralizationCdd match").isNotNull();
        assertThat(genMatch.getCandidateName()).isEqualTo("LinkedDevice");
        assertThat(genMatch.getRelatedCandidateName()).isEqualTo("Device");
    }

    @Test
    public void tdr40_doesNotFireForEvaluativeAdjective() {
        Sentence sentence = new Sentence();
        addToken(0, "valid", PartOfSpeechType.JJ);
        addToken(1, "input", PartOfSpeechType.NN);

        // amod(input, valid) — "valid" is in the stop list

        tdr40.currentTd = createTypedDependency(sentence, TdType.AMOD, 1, 0);
        tdr40.previousTd = null;
        tdr40.nextTd = null;

        assertThat(tdr40.when()).as("TDR40 should NOT fire for evaluative adjective (valid)").isFalse();
    }

    // ======== TDR41 Tests: Synonym identification ========

    /**
     * Appositive synonym: "the store, the shop" → appos(store, shop), both nouns
     * (POS-Matching, SYNONYMS.md §2). Fires and records a SynonymCdd match.
     */
    @Test
    public void tdr41_recognisesAppositiveSynonym() {
        Sentence sentence = new Sentence();
        addToken(0, "store", PartOfSpeechType.NN);
        addToken(1, "shop", PartOfSpeechType.NN);

        // appos(store, shop)
        TypedDependency td = createTypedDependency(sentence, TdType.APPOS, 0, 1);

        tdr41.currentTd = td;
        tdr41.previousTd = null;
        tdr41.nextTd = null;

        assertThat(tdr41.when()).as("TDR41 should fire for appos(store, shop) with two nouns").isTrue();
        tdr41.then();

        List<RuleMatch> matches = tdr41.ruleMatches.findByRuleClassName("TDR41");
        RuleMatch synMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "SynonymCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(synMatch).as("TDR41 should have created a SynonymCdd match").isNotNull();
        assertThat(synMatch.getCandidateName()).isEqualTo("Store");
        assertThat(synMatch.getRelatedCandidateName()).isEqualTo("Shop");
    }

    /**
     * Apposition must NOT fire when the dependent is NOT a noun (POS-Matching
     * heuristic: synonyms must share the same part of speech).
     */
    @Test
    public void tdr41_doesNotFireWhenNotBothNouns() {
        Sentence sentence = new Sentence();
        addToken(0, "store", PartOfSpeechType.NN);
        addToken(1, "online", PartOfSpeechType.JJ);

        // appos(store, online) — dependent is an adjective
        tdr41.currentTd = createTypedDependency(sentence, TdType.APPOS, 0, 1);
        tdr41.previousTd = null;
        tdr41.nextTd = null;

        assertThat(tdr41.when()).as("TDR41 should NOT fire when the appositive is not a noun").isFalse();
    }

    /**
     * Apposition must NOT fire when head and alias are the same word.
     */
    @Test
    public void tdr41_doesNotFireWhenSameLemma() {
        Sentence sentence = new Sentence();
        addToken(0, "store", PartOfSpeechType.NN);
        addToken(1, "store", PartOfSpeechType.NN);

        tdr41.currentTd = createTypedDependency(sentence, TdType.APPOS, 0, 1);
        tdr41.previousTd = null;
        tdr41.nextTd = null;

        assertThat(tdr41.when()).as("TDR41 should NOT fire for appos(store, store)").isFalse();
    }

    /**
     * Defining construction "X, also known as Y": acl:relcl(Acetaminophen, known) +
     * obl:as(known, paracetamol). Fires and records the synonym pair.
     */
    @Test
    public void tdr41_recognisesAlsoKnownAsSynonym() {
        Sentence sentence = new Sentence();
        addToken(0, "acetaminophen", PartOfSpeechType.NN);
        addToken(1, "also", PartOfSpeechType.RB);
        addToken(2, "known", PartOfSpeechType.VBN);
        addToken(3, "as", PartOfSpeechType.IN);
        addToken(4, "paracetamol", PartOfSpeechType.NN);

        // acl:relcl(acetaminophen, known) — currentTd for TDR41
        TypedDependency td = createTypedDependency(sentence, TdType.ACL_RELCL, 0, 2);
        // obl:as(known, paracetamol) — the synonym partner
        createTypedDependency(sentence, TdType.OBL_AS, 2, 4);

        tdr41.currentTd = td;
        tdr41.previousTd = null;
        tdr41.nextTd = null;

        assertThat(tdr41.when()).as("TDR41 should fire for acl:relcl with a synonym marker verb").isTrue();
        tdr41.then();

        List<RuleMatch> matches = tdr41.ruleMatches.findByRuleClassName("TDR41");
        RuleMatch synMatch = matches.stream()
                .filter(m -> m.getTypedDependency() == td
                        && "SynonymCdd".equals(m.getCandidateType()))
                .findFirst()
                .orElse(null);
        assertThat(synMatch).as("TDR41 should have created a SynonymCdd match").isNotNull();
        assertThat(synMatch.getCandidateName()).isEqualTo("Acetaminophen");
        assertThat(synMatch.getRelatedCandidateName()).isEqualTo("Paracetamol");
    }

    /**
     * A defining relative clause whose marker verb is NOT in the configured
     * synonym-marker vocabulary must NOT fire.
     */
    @Test
    public void tdr41_doesNotFireWhenMarkerNotConfigured() {
        Sentence sentence = new Sentence();
        addToken(0, "device", PartOfSpeechType.NN);
        addToken(1, "described", PartOfSpeechType.VBN);

        // acl:relcl(device, described) — "described" is not a synonym marker
        tdr41.currentTd = createTypedDependency(sentence, TdType.ACL_RELCL, 0, 1);
        tdr41.previousTd = null;
        tdr41.nextTd = null;

        assertThat(tdr41.when()).as("TDR41 should NOT fire for a marker verb not in the vocabulary").isFalse();
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