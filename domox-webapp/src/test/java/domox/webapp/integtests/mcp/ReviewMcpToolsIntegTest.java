package domox.webapp.integtests.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
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
@ContextConfiguration(initializers = ReviewMcpToolsIntegTest.Initializer.class)
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
        // when / then
        assertThatThrownBy(() -> reviewMcpTools.rejectCandidate(999999L, "OTHER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No candidate found with id 999999");
    }

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            if (ApplicationIntegTestAbstract.postgres != null && ApplicationIntegTestAbstract.postgres.isRunning()) {
                TestPropertyValues.of(
                        "spring.datasource.url=" + ApplicationIntegTestAbstract.postgres.getJdbcUrl(),
                        "spring.datasource.username=" + ApplicationIntegTestAbstract.postgres.getUsername(),
                        "spring.datasource.password=" + ApplicationIntegTestAbstract.postgres.getPassword()
                ).applyTo(context.getEnvironment());
            }
        }
    }
}