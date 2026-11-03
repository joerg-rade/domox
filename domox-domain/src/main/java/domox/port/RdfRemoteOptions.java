package domox.port;

import java.time.Duration;

/**
 * Per-call options for a remote SPARQL request.
 *
 * <p>Both are optional: {@code null} means "use the adapter's default".</p>
 *
 * @param timeout   per-request timeout — without one a slow or overloaded endpoint blocks the
 *                  calling thread indefinitely
 * @param userAgent value of the {@code User-Agent} header; several public endpoints (Wikidata in
 *                  particular) reject requests that do not identify themselves
 */
public record RdfRemoteOptions(Duration timeout, String userAgent) {

    /** No timeout, no explicit user agent. */
    public static final RdfRemoteOptions DEFAULTS = new RdfRemoteOptions(null, null);

    public static RdfRemoteOptions withTimeout(final Duration timeout) {
        return new RdfRemoteOptions(timeout, null);
    }
}
