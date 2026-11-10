package domox.adapter.nlp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class CoreNLPTest {
    companion object {
        private val CORE_NLP_IMAGE = DockerImageName.parse("graham3333/corenlp-complete")

        @Container
        @JvmStatic
        val coreNlpContainer: GenericContainer<*> = GenericContainer(CORE_NLP_IMAGE)
            .withExposedPorts(9000)
    }

    @Test
    fun `starts the CoreNLP container`() {
        assertThat(coreNlpContainer.isRunning).isTrue()
        assertThat(coreNlpContainer.getMappedPort(9000)).isPositive()
    }
}
