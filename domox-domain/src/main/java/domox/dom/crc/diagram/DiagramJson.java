package domox.dom.crc.diagram;

/**
 * Minimal JSON string escaping shared by every lexical diagram renderer.
 * <p>
 * The renderers hand-roll their JSON (there is no JSON library on the domain classpath), so the
 * escaping must be exact and identical everywhere: a lemma or a display name is an arbitrary
 * ontology string and may contain a quote, a backslash or a control character.
 */
public final class DiagramJson {

    private DiagramJson() {
    }

    /**
     * Escapes backslash, double-quote and control characters, mapping {@code null}/empty to the
     * empty string.
     */
    public static String escape(final String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
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
