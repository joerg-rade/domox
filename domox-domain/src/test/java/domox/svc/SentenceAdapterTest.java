package domox.svc;

import domox.FileUtil;
import domox.nlp.DocumentTO;
import domox.nlp.SentenceTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class SentenceAdapterTest {

    @Container
    private static final GenericContainer<?> coreNlp = new GenericContainer<>(
            DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g");

    @Container
    private static final GenericContainer<?> kroki = new GenericContainer<>("yuzutech/kroki:latest")
            .withExposedPorts(8000)
            .withEnv("KROKI_PLANTUML_JAVAFLAGS", "-Xmx2g");

    @BeforeAll
    static void setUpContainers() {
        // Set system properties for testcontainers' dynamic host/port
        System.setProperty("corenlp.scheme", "http");
        System.setProperty("corenlp.host", coreNlp.getHost());
        System.setProperty("corenlp.port", String.valueOf(coreNlp.getMappedPort(9000)));
        System.setProperty("kroki.host", kroki.getHost());
        System.setProperty("kroki.port", String.valueOf(kroki.getMappedPort(8000)));
    }

    @Test
    void buildTypedDependencyDiagram() {
        final String filename = "PetClinic.txt";
        final String txtContent = new FileUtil().readFileFromResources(filename);
        final DocumentTO documentTO = new DocumentAdapter().parseTextAndAmend(txtContent);
        final SentenceTO sentenceTO = documentTO.getSentences().getFirst();

        final SentenceAdapter sut = new SentenceAdapter(sentenceTO);
        // when
        final byte[] diagram = sut.buildTypedDependencyDiagram(sentenceTO);
        // then
        assertNotNull(diagram);
    }
}