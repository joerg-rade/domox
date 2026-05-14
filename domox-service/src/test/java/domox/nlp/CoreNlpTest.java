import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class CoreNlpTest {

    @Container
    private static final GenericContainer<?> coreNlp = new GenericContainer<>("corenlp:latest")
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g");

    @Test
    void testCoreNlp() {
        // Your test logic here, e.g., connect to coreNlp.getMappedPort(9000)
        String host = coreNlp.getHost();
        Integer port = coreNlp.getMappedPort(9000);
        // Use host and port to interact with CoreNLP
    }
}
