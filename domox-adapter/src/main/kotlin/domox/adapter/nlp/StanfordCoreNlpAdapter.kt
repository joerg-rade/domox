package domox.adapter.nlp

import domox.Constants
import domox.port.DocumentTO
import domox.port.NlpPort
import org.springframework.stereotype.Component

/**
 * Adapter implementation of [NlpPort] that uses Stanford CoreNLP via HTTP.
 * <p>
 * Reads connection parameters from system properties with fallback defaults
 * from [Constants]:
 * <ul>
 *   <li>{@code corenlp.scheme} — default {@link Constants#coreNlpScheme}</li>
 *   <li>{@code corenlp.host} — default {@link Constants#coreNlpHost}</li>
 *   <li>{@code corenlp.port} — default {@link Constants#coreNlpPort}</li>
 * </ul>
 * </p>
 */
@Component
class StanfordCoreNlpAdapter : NlpPort {

    override fun parse(rawText: String): DocumentTO {
        val scheme = getSystemProperty("corenlp.scheme", Constants.coreNlpScheme)
        val host = getSystemProperty("corenlp.host", Constants.coreNlpHost)
        val port = getSystemProperty("corenlp.port", Constants.coreNlpPort.toString()).toInt()
        val api = StanfordCoreNlpAPI(scheme, host, port)
        return api.annotate(rawText)
    }

    private fun getSystemProperty(key: String, defaultValue: String): String {
        return System.getProperty(key) ?: defaultValue
    }
}
