package domox.port;

/**
 * Port interface for Natural Language Processing (NLP) services.
 * Defined in the domain so that domain services depend only on this interface,
 * not on concrete adapter implementations.
 */
public interface NlpPort {

    /**
     * Parses raw text using an NLP service and returns a document transfer object
     * containing sentences, tokens, and dependency information.
     *
     * @param rawText the text to parse
     * @return a DocumentTO with NLP analysis results
     */
    DocumentTO parse(String rawText);
}
