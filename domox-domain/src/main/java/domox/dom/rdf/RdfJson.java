package domox.dom.rdf;

/**
 * Minimal JSON string escaping, shared by the diagram writers in this package
 * ({@link RdfDiagramJson} and {@link DiagramJsonWriter}).
 * <p>
 * Split out of {@code RdfGraphDiagrams} so the UML layout writer does not have to reach into the RDF
 * diagram class for a one-method utility.
 */
public final class RdfJson {

    private RdfJson() {
    }

    /**
     * Escapes {@code value} for use inside a JSON string literal: backslash, double quote and the
     * control characters JSON requires to be escaped.  {@code null} becomes the empty string, so a
     * caller can append the result unconditionally.
     */
    public static String escape(final String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
