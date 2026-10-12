package domox.port;

/**
 * Domain-level constants.
 * <p>
 * Only constants that the domain core needs are declared here.
 * Infrastructure-specific constants (host, port, scheme) live in the adapter module.
 * </p>
 */
public final class DomainConstants {

    private DomainConstants() {
        // utility class
    }

    public static final String PDF_MIME_TYPE = "application/pdf";
    public static final String SVG_MIME_TYPE = "image/svg+xml";
    public static final String JSON_MIME_TYPE = "application/json";
}
