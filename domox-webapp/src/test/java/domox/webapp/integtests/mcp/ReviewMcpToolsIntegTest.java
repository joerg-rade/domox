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
import domox.webapp.mcp.NextCandidateResult;
import domox.webapp.mcp.ReviewMcpTools;

/**
 * Verifies that the automated-review MCP tools are declared for auto-registration and
 * behave end-to-end against an empty database.
 * <p>
 * The {@code @McpTool}-annotated methods on {@link ReviewMcpTools} are auto-scanned and
 * exposed on the {@code /mcp} endpoint by {@code McpServerAnnotationScannerAutoConfiguration}.
 */
@Transactional
class ReviewMcpToolsIntegTest extends ApplicationIntegTestAbstract {

    @Autowired
    private ReviewMcpTools reviewMcpTools;

    @Test
    void reviewMcpTools_isManagedBean() {
        assertThat(reviewMcpTools).isNotNull();
    }

    @Test
    void mcpTool_annotations_arePresent() {
        // when
        final List<String> toolNames = Arrays.stream(reviewMcpTools.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .sorted()
                .toList();

        // then
        assertThat(toolNames)
                .containsExactly("approveCandidate", "nextCandidateForReview", "rejectCandidate");
    }

    @Test
    void nextCandidateForReview_withEmptyDatabase_returnsNoCandidates() {
        // when
        final NextCandidateResult result = reviewMcpTools.nextCandidateForReview();

        // then
        assertThat(result.candidate()).isNull();
        assertThat(result.message()).isEqualTo(NextCandidateResult.NO_CANDIDATES_MESSAGE);
    }

    @Test
    void rejectCandidate_withUnknownId_throwsIllegalArgument() {
        // when / then — a valid reason, so the id is what fails
        assertThatThrownBy(() -> reviewMcpTools.rejectCandidate(999999L, "OTHER", "not a real concept"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No candidate found with id 999999");
    }

    // --- the reason requirement: every MCP write must say why ---

    @Test
    void approveCandidate_withoutAReason_isRefused() {
        // when / then — refused before the database is touched, whatever the id
        assertThatThrownBy(() -> reviewMcpTools.approveCandidate(1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }

    @Test
    void rejectCandidate_withoutAReason_isRefused() {
        // when / then
        assertThatThrownBy(() -> reviewMcpTools.rejectCandidate(1L, "OTHER", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }

    @Test
    void approveCandidate_withoutAReasonAtAll_isRefused() {
        // when / then — a missing argument is the same as a blank one
        assertThatThrownBy(() -> reviewMcpTools.approveCandidate(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }

    }