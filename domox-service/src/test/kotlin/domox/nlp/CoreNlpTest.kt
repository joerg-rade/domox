package domox.nlp

import org.junit.jupiter.api.Disabled
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test

@Disabled("Requires Docker environment with CoreNLP container")
class CoreNLPTest {
    companion object {
        private val CORE_NLP_IMAGE = DockerImageName.parse("graham3333/corenlp-complete")
    }

    @Test
    fun `test CoreNLP container`() {
        GenericContainer(CORE_NLP_IMAGE).apply {
            withExposedPorts(9000)
            start()

            val coreNlpUrl = "http://${host}:${getMappedPort(9000)}/"
            println("CoreNLP running at: $coreNlpUrl")

            stop()
        }
    }
}
