package domox.webapp.unittests;

import domox.dom.rules.NlpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the production {@code domox.nlp.*} vocabulary loaded from
 * {@code application.yml}.  This test catches unintended changes to the NLP
 * configuration that would affect domain-action detection (TDR38) and other
 * vocabulary-driven rules.
 *
 * <p>Because the webapp module owns {@code application.yml}, this test lives
 * here rather than in {@code domox-domain}, where the rule tests use minimal
 * controlled fixtures.</p>
 */
class NlpConfigBindingTest {

    @Test
    void actionVerbs_shouldNotContainDomainVerbs() throws Exception {
        var props = loadNlpProperties();

        assertFalse(props.getActionVerbs().contains("include"),
                "'include' in action-verbs would blacklist it as a domain action");
    }

    @Test
    void receiveVerbs_shouldNotContainDomainVerbs() throws Exception {
        var props = loadNlpProperties();

        assertFalse(props.getReceiveVerbs().contains("capture"),
                "'capture' in receive-verbs would blacklist it as a domain action");
    }

    @Test
    void allVocabularyLists_areNonEmpty() throws Exception {
        var props = loadNlpProperties();

        assertNotNull(props.getUserInputVerbs());
        assertFalse(props.getUserInputVerbs().isEmpty());

        assertNotNull(props.getSystemOutputVerbs());
        assertFalse(props.getSystemOutputVerbs().isEmpty());

        assertNotNull(props.getActionVerbs());
        assertFalse(props.getActionVerbs().isEmpty());

//        assertNotNull(props.getInputPastVerbs());
//        assertFalse(props.getInputPastVerbs().isEmpty());

//        assertNotNull(props.getOutputPastVerbs());
//        assertFalse(props.getOutputPastVerbs().isEmpty());

        assertNotNull(props.getReceiveVerbs());
        assertFalse(props.getReceiveVerbs().isEmpty());

        assertNotNull(props.getExceptionTerms());
        assertFalse(props.getExceptionTerms().isEmpty());
    }

    @Test
    void useCaseBlockedNouns_containsStructuralUseCaseTerms_only() throws Exception {
        var props = loadNlpProperties();

        assertNotNull(props.getUseCaseBlockedNouns());
        assertFalse(props.getUseCaseBlockedNouns().isEmpty());

        // Structural/meta nouns of use-case documents that must never become classes
        assertTrue(props.getUseCaseBlockedNouns().contains("requirement"));
        assertTrue(props.getUseCaseBlockedNouns().contains("flow"));
        assertTrue(props.getUseCaseBlockedNouns().contains("step"));
        assertTrue(props.getUseCaseBlockedNouns().contains("condition"));

        // Real domain nouns must NOT be blocked from becoming classes
        assertFalse(props.getUseCaseBlockedNouns().contains("product"),
                "'product' in use-case-blocked-nouns would prevent a real domain entity");
        assertFalse(props.getUseCaseBlockedNouns().contains("order"),
                "'order' in use-case-blocked-nouns would prevent a real domain entity");
        assertFalse(props.getUseCaseBlockedNouns().contains("customer"),
                "'customer' in use-case-blocked-nouns would prevent a real domain entity");
    }

    @Test
    void synonymMarkers_areConfiguredForTDR41() throws Exception {
        var props = loadNlpProperties();

        assertNotNull(props.getSynonymMarkers());
        assertFalse(props.getSynonymMarkers().isEmpty());

        // The core defining-construction marker verbs TDR41 relies on
        assertTrue(props.getSynonymMarkers().contains("known"));
        assertTrue(props.getSynonymMarkers().contains("termed"));
        assertTrue(props.getSynonymMarkers().contains("called"));
    }

    // ----------------------------------------------------------------

    private static NlpProperties loadNlpProperties() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySources = loader.load("domox-nlp", new ClassPathResource("application.yml"));
        var sources = propertySources.stream()
                .map(ConfigurationPropertySource::from)
                .toList();
        var binder = new Binder(sources);
        return binder.bind("domox.nlp", NlpProperties.class).get();
    }
}