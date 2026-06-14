package domox.svc;

import domox.nlp.DocumentTO;
import domox.nlp.StanfordCoreNlpAPI;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class DocumentAdapterTest {

    @Container
    private static final GenericContainer<?> coreNlp = new GenericContainer<>(
            DockerImageName.parse("graham3333/corenlp-complete").asCompatibleSubstituteFor("corenlp"))
            .withExposedPorts(9000)
            .withEnv("JVM_OPTS", "-Xmx4g");

    @Test
    void annotateTest() {
        //given
        val scheme = "http";
        val host = coreNlp.getHost();
        val port = coreNlp.getMappedPort(9000);
        val nlpAPI = new StanfordCoreNlpAPI(scheme, host, port);
        val qbf = "The quick brown fox jumped over the lazy dog.";
        final DocumentTO documentTO = nlpAPI.annotate(qbf);
        assertNotNull(documentTO);
        assertNotNull(documentTO.getSentences());
        assertTrue(documentTO.getSentences().size() > 0);
        assertNotNull(documentTO.getSentences().getFirst());

        val sentence = documentTO.getSentences().getFirst();
        assertNotNull(sentence);
    }

}
