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
import domox.webapp.mcp.SubDomainMcpTools;

/**
 * Verifies that the sub-domain MCP tools are declared for auto-registration, and that every
 * <em>write</em> tool enforces the rule the MCP API is built on: <b>whenever something is changed,
 * the client must give the reason</b>.
 * <p>
 * The reason assertions need no fixture: the guard runs before the database is touched, so a blank
 * reason is refused whatever the ids — which is exactly the property being pinned.  That the reason
 * then reaches {@code Candidate.rationale} / {@code SubDomain.rationale} is covered by
 * {@code ReviewsTest} (unit) and by the {@code rationale} column itself.
 */
@Transactional
class SubDomainMcpToolsIntegTest extends ApplicationIntegTestAbstract {

    @Autowired
    private SubDomainMcpTools subDomainMcpTools;

    @Test
    void subDomainMcpTools_isManagedBean() {
        assertThat(subDomainMcpTools).isNotNull();
    }

    @Test
    void mcpTool_annotations_arePresent() {
        // when
        final List<String> toolNames = Arrays.stream(subDomainMcpTools.getClass().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpTool.class))
                .map(m -> m.getAnnotation(McpTool.class).name())
                .sorted()
                .toList();

        // then — the five read/advisory tools plus the three write tools
        assertThat(toolNames).containsExactly(
                "assignCandidatesToSubDomain",
                "clearCandidateSubDomain",
                "createSubDomain",
                "listSubDomains",
                "listUnassignedClassCandidates",
                "listUseCases",
                "subDomainEvidenceForCandidate",
                "suggestSubDomains");
    }

    @Test
    void createSubDomain_withoutAReason_isRefused() {
        // when / then
        assertThatThrownBy(() -> subDomainMcpTools.createSubDomain("Checkout", "CORE", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }

    @Test
    void assignCandidatesToSubDomain_withoutAReason_isRefused() {
        // when / then
        assertThatThrownBy(() -> subDomainMcpTools.assignCandidatesToSubDomain(
                List.of(1L), "Checkout", "CORE", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }

    @Test
    void clearCandidateSubDomain_withoutAReason_isRefused() {
        // when / then — a missing argument is the same as a blank one
        assertThatThrownBy(() -> subDomainMcpTools.clearCandidateSubDomain(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A reason is required");
    }
}
