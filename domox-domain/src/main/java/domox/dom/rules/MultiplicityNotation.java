package domox.dom.rules;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the raw determiner / number token a multiplicity rule fired on as <b>UML multiplicity
 * notation</b> (UML 2.5 §7.5.3: a multiplicity is either a single count, a {@code lower..upper}
 * range, or {@code *} for {@code 0..*}).
 * <p>
 * The rules see whatever the dependency parser produced — {@code nummod(pet, 01)},
 * {@code nummod(pet, one)}, {@code nummod(pet, more)}, {@code det(pet, many)} — and that token is
 * <em>not</em> a multiplicity: {@code 01} is a zero-padded number, {@code one} is a word for a
 * count, and {@code many} is a quantifier, not a number. Recording them verbatim made the evidence
 * read {@code multiplicity.add(pet, 01)} / {@code multiplicity.add(pet, N)}, mixing three
 * conventions (zero-padded digits, number words, and the literal placeholder {@code N}) and never
 * producing a value a UML tool could consume. This class is the single translation point.
 *
 * <h2>Mapping</h2>
 * <table>
 *   <caption>raw token to UML notation</caption>
 *   <tr><th>token</th><th>UML</th><th>rationale</th></tr>
 *   <tr><td>{@code a}, {@code an}, {@code one}</td><td>{@code 1}</td><td>exactly one</td></tr>
 *   <tr><td>{@code zero}, {@code no}, {@code none}</td><td>{@code 0}</td><td>none</td></tr>
 *   <tr><td>{@code two} … {@code twenty}</td><td>{@code 2} … {@code 20}</td><td>exact count</td></tr>
 *   <tr><td>{@code 1}, {@code 02}, {@code 10}</td><td>{@code 1}, {@code 2}, {@code 10}</td>
 *       <td>exact count, leading zeros dropped</td></tr>
 *   <tr><td>{@code each}, {@code every}, {@code all}, {@code any}, {@code many},
 *       {@code multiple}, {@code several}, {@code more}, {@code most}</td><td>{@code *}</td>
 *       <td>plural / universal — {@code 0..*}</td></tr>
 *   <tr><td>{@code some}</td><td>{@code 1..*}</td><td>“some” asserts at least one</td></tr>
 *   <tr><td>{@code 2..4}, {@code 2-4}, {@code 2 to 4}, {@code between 2 and 4}</td>
 *       <td>{@code 2..4}</td><td>range</td></tr>
 *   <tr><td>{@code 2+}, {@code at least 2}</td><td>{@code 2..*}</td><td>lower bound only</td></tr>
 *   <tr><td>{@code at most 2}, {@code up to 2}</td><td>{@code 0..2}</td><td>upper bound only</td></tr>
 *   <tr><td>anything else, {@code null}, blank</td><td>{@code *}</td>
 *       <td>unbounded — never assert a count the text does not state</td></tr>
 * </table>
 * <p>
 * The function is <b>idempotent</b>: a value already in UML notation ({@code *}, {@code 0..1},
 * {@code 1..*}) is returned unchanged, so a normalised value can be re-normalised safely. A range
 * whose bounds are equal collapses to the single count ({@code 2..2} → {@code 2}) and an inverted
 * range is written the right way round ({@code 4..2} → {@code 2..4}), because UML requires
 * {@code lower <= upper}.
 * <p>
 * A utility rather than a {@code @Service} on purpose — same shape as {@link UseCaseBlockedNouns},
 * and the rules stay constructible in a plain unit test.
 */
public final class MultiplicityNotation {

    /** UML notation for {@code 0..*}: zero or more. */
    public static final String UNBOUNDED = "*";

    /** UML range separator (never a hyphen, never {@code ...}). */
    public static final String RANGE_SEPARATOR = "..";

    /**
     * Word tokens whose multiplicity is fixed by the word itself. Digits are handled by
     * {@link #EXACT} so that {@code 02} and {@code 2} land on the same value.
     */
    private static final Map<String, String> TOKEN_VALUES = Map.ofEntries(
            Map.entry("a", "1"),
            Map.entry("an", "1"),
            Map.entry("one", "1"),
            Map.entry("zero", "0"),
            Map.entry("no", "0"),
            Map.entry("none", "0"),
            Map.entry("two", "2"),
            Map.entry("three", "3"),
            Map.entry("four", "4"),
            Map.entry("five", "5"),
            Map.entry("six", "6"),
            Map.entry("seven", "7"),
            Map.entry("eight", "8"),
            Map.entry("nine", "9"),
            Map.entry("ten", "10"),
            Map.entry("eleven", "11"),
            Map.entry("twelve", "12"),
            Map.entry("thirteen", "13"),
            Map.entry("fourteen", "14"),
            Map.entry("fifteen", "15"),
            Map.entry("sixteen", "16"),
            Map.entry("seventeen", "17"),
            Map.entry("eighteen", "18"),
            Map.entry("nineteen", "19"),
            Map.entry("twenty", "20"),
            Map.entry("each", UNBOUNDED),
            Map.entry("every", UNBOUNDED),
            Map.entry("all", UNBOUNDED),
            Map.entry("any", UNBOUNDED),
            Map.entry("many", UNBOUNDED),
            Map.entry("multiple", UNBOUNDED),
            Map.entry("several", UNBOUNDED),
            Map.entry("more", UNBOUNDED),
            Map.entry("most", UNBOUNDED),
            Map.entry("some", "1" + RANGE_SEPARATOR + UNBOUNDED));

    /**
     * Determiners that mark an unbounded/plural occurrence — the fire vocabulary of {@code TDR26}.
     * Kept here so the rule's <em>fire</em> set and this class's <em>value</em> set cannot drift:
     * every token in this set maps to a UML value.
     */
    private static final Set<String> QUANTIFYING_DETERMINERS = Set.of(
            "each", "every", "all", "any", "many", "multiple", "some");

    /** Determiners that mark exactly one occurrence — the singular half of {@code TDR26}'s guard. */
    private static final Set<String> SINGULAR_DETERMINERS = Set.of("a", "an");

    private static final Pattern EXACT = Pattern.compile("^(\\d+)$");
    private static final Pattern RANGE = Pattern.compile(
            "^(\\d+|\\*)\\s*(?:\\.\\.+|-|to)\\s*(\\d+|\\*)$");
    private static final Pattern PLUS = Pattern.compile("^(\\d+)\\s*\\+$");
    private static final Pattern AT_LEAST = Pattern.compile(
            "^(?:at\\s+least|minimum(?:\\s+of)?|min)\\s+(\\d+)$");
    private static final Pattern AT_MOST = Pattern.compile(
            "^(?:at\\s+most|up\\s+to|no\\s+more\\s+than|maximum(?:\\s+of)?|max)\\s+(\\d+)$");
    private static final Pattern BETWEEN = Pattern.compile(
            "^between\\s+(\\d+)\\s+and\\s+(\\d+)$");

    private MultiplicityNotation() {
    }

    /**
     * Translates a raw determiner / number token into UML multiplicity notation.
     *
     * @param rawToken the token the rule fired on; may be {@code null} or blank
     * @return a UML-conformant multiplicity, never {@code null} — {@link #UNBOUNDED} when the token
     *         carries no usable count
     */
    public static String umlNotation(final String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return UNBOUNDED;
        }
        final String token = rawToken.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (UNBOUNDED.equals(token)) {
            return UNBOUNDED;
        }
        Matcher matcher = BETWEEN.matcher(token);
        if (matcher.matches()) {
            return range(matcher.group(1), matcher.group(2));
        }
        matcher = AT_LEAST.matcher(token);
        if (matcher.matches()) {
            return lowerBound(matcher.group(1));
        }
        matcher = AT_MOST.matcher(token);
        if (matcher.matches()) {
            return upperBound(matcher.group(1));
        }
        matcher = RANGE.matcher(token);
        if (matcher.matches()) {
            return range(matcher.group(1), matcher.group(2));
        }
        matcher = PLUS.matcher(token);
        if (matcher.matches()) {
            return lowerBound(matcher.group(1));
        }
        matcher = EXACT.matcher(token);
        if (matcher.matches()) {
            return stripLeadingZeros(matcher.group(1));
        }
        final String known = TOKEN_VALUES.get(token);
        return known != null ? known : UNBOUNDED;
    }

    /** Whether {@code token} is a determiner marking an unbounded/plural occurrence. */
    public static boolean isQuantifyingDeterminer(final String token) {
        return token != null && QUANTIFYING_DETERMINERS.contains(token.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code token} is a determiner marking exactly one occurrence ({@code a} / {@code an}). */
    public static boolean isSingularDeterminer(final String token) {
        return token != null && SINGULAR_DETERMINERS.contains(token.toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- helpers

    private static String range(final String lower, final String upper) {
        if (UNBOUNDED.equals(lower)) {
            // "*..n" is not a UML multiplicity — an unbounded lower bound has no range to state.
            return UNBOUNDED;
        }
        final String low = stripLeadingZeros(lower);
        if (UNBOUNDED.equals(upper)) {
            return low + RANGE_SEPARATOR + UNBOUNDED;
        }
        final String high = stripLeadingZeros(upper);
        if (low.equals(high)) {
            return low; // "2..2" is exactly 2 — the single count is the canonical form
        }
        // UML requires lower <= upper; write an inverted range the right way round
        return compareDigits(low, high) < 0
                ? low + RANGE_SEPARATOR + high
                : high + RANGE_SEPARATOR + low;
    }

    private static String lowerBound(final String digits) {
        return stripLeadingZeros(digits) + RANGE_SEPARATOR + UNBOUNDED;
    }

    private static String upperBound(final String digits) {
        return "0" + RANGE_SEPARATOR + stripLeadingZeros(digits);
    }

    /** {@code 01} → {@code 1}, {@code 00} → {@code 0}, {@code 10} → {@code 10}. */
    private static String stripLeadingZeros(final String digits) {
        int first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        return digits.substring(first);
    }

    /** Compares two leading-zero-free digit strings of any length without parsing them. */
    private static int compareDigits(final String left, final String right) {
        if (left.length() != right.length()) {
            return Integer.compare(left.length(), right.length());
        }
        return left.compareTo(right);
    }
}
