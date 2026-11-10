package domox.webapp.integtests.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import domox.webapp.integtests.ApplicationIntegTestAbstract;
import domox.webapp.mcp.OntologyReviewMcpTools;
import domox.webapp.mcp.OntologySearchResult;

/**
 * Verifies the ontology-assisted review MCP tools are declared for auto-registration and behave
 * end-to-end.
 * <p>
 * The ontology assertions run in <b>local</b> mode — {@link ApplicationIntegTestAbstract} pins
 * {@code domox.rdf.endpoint-url} blank — so they query the ontology bundled in {@code domox-domain}
 * and stay offline and deterministic, exactly like {@code CorpusOntologyDiagramIntegTest}.
 */
@Transactional
class OntologyReviewMcpToolsIntegTest extends ApplicationIntegTestAbstract {

    @Autowired
    private OntologyReviewMcpTools ontologyReviewMcpTools;

    @Test
    void ontologyReviewMcpTools_isManagedBean() {
        assertThat(ontologyReviewMcpTools).isNotNull();
    }

    @Test
    void mcpTool_annotations_arePresent() {
        // when
        final List<String> toolNames = Arrays.stream(ontologyReviewMcpTools.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .sorted()
                .toList();

        // then
        assertThat(toolNames)
                .containsExactly("ontologyEvidenceForCandidate", "ontologySearch", "suggestReview");
    }

    @Test
    void ontologySearch_findsATermInTheBundledOntology() {
        // when — "Claim" is a class of the bundled OntoUML model
        final OntologySearchResult result = ontologyReviewMcpTools.ontologySearch("Claim");

        // then
        assertThat(result.matchCount()).isGreaterThan(0);
        assertThat(result.matches()).anySatisfy(match ->
                assertThat(match.kind()).isEqualTo("CLASS"));
    }

    @Test
    void ontologySearch_withNoMatch_reportsAvailableButEmpty() {
        // when
        final OntologySearchResult result = ontologyReviewMcpTools.ontologySearch("ZzzNoSuchTermZzz");

        // then — "no match" is reported as such, never as a failure
        assertThat(result.matchCount()).isZero();
        assertThat(result.matches()).isEmpty();
        assertThat(result.message()).contains("No ontology term matches");
    }

    @Test
    void ontologyEvidenceForCandidate_withUnknownId_throwsIllegalArgument() {
        assertThatThrownBy(() -> ontologyReviewMcpTools.ontologyEvidenceForCandidate(999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No candidate found with id 999999");
    }

    @Test
    void suggestReview_withUnknownId_throwsIllegalArgument() {
        assertThatThrownBy(() -> ontologyReviewMcpTools.suggestReview(999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No candidate found with id 999999");
    }
}
