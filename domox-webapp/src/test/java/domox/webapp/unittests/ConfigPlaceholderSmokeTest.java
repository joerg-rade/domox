package domox.webapp.unittests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Resolves every <em>no-default</em> {@code ${causeway.*}} placeholder on the classpath against the
 * committed {@code application.yml}.
 *
 * <h2>Why this exists</h2>
 * A {@code @Value("${causeway.some.key}")} whose default lives only in a Causeway
 * {@code @ConfigurationProperties} class is <strong>invisible to {@code Environment}</strong>: the
 * binder sets a field default, it does not register a property source.  Such a placeholder therefore
 * has to be supplied by {@code application.yml}, and if it is missing (or misspelled) the failure
 * arrives at <em>bean creation</em> — the most expensive place to find it.  That is exactly what
 * happened on the Boot 4 upgrade:
 * <pre>Could not resolve placeholder 'causeway.viewer.restfulobjects.base-path'</pre>
 * (see {@code docs/scratch_pad/retro-boot4-upgrade-2026-10-14.md} candidate 3).  The
 * {@code integration-test} CI job does boot the full context and would catch it — but only there,
 * and only after the {@code test} job, so the failure is slow and its cause is buried in a stack
 * trace.  This test is the cheap, local, self-contained half.
 *
 * <h2>How it works — the required keys are DERIVED, not listed</h2>
 * The placeholder names are scraped from the compiled class constant pools on the classpath (an
 * {@code @Value} string is stored verbatim), so a Causeway upgrade that starts requiring a
 * <em>new</em> placeholder is caught without editing this test.  A placeholder that carries a
 * default ({@code ${causeway.x:y}}) is deliberately not matched: it cannot fail to resolve, and
 * flagging it would be noise.
 *
 * <h2>Deliberate limits</h2>
 * <ul>
 *   <li>Only the {@code causeway.} namespace is scraped.  A bare {@code ${name}} is not a config
 *       placeholder in this codebase — {@code CandidateVegaGraph} builds Vega JSON with
 *       {@code ${title}} / {@code ${nodes}} tokens, and a general scrape would flag those.</li>
 *   <li>Only {@code application.yml} may satisfy a placeholder: the ambient system properties and
 *       environment variables are removed first, so a key that only happens to be exported in a
 *       developer's shell cannot mask a missing committed key.</li>
 *   <li>If the scan ever finds nothing (Causeway giving every placeholder a default would do it),
 *       the first assertion fails on purpose: a guardrail that silently checks nothing is worse
 *       than no guardrail.  The fix then is to delete this test, deliberately.</li>
 * </ul>
 */
class ConfigPlaceholderSmokeTest {

    /**
     * A no-default placeholder in the {@code causeway} namespace, e.g.
     * {@code causeway.viewer.restfulobjects.base-path}.  Two deliberate restrictions:
     * <ul>
     *   <li>{@code [A-Za-z0-9_-]+} — at least one full name segment after {@code causeway.}, so an
     *       ellipsis (as in prose about the pattern) cannot match.</li>
     *   <li>no {@code :} — {@code [^}:]*} stops at a colon, so a placeholder that carries a default
     *       does not match at all; it cannot fail to resolve, and flagging it would be noise.</li>
     * </ul>
     * <p>Note the self-reference hazard this creates: a literal placeholder written inside a string
     * constant of THIS class lands in this class's own constant pool, which the scan then reads.  So
     * keep messages and javadoc free of the literal pattern — prose here says "a
     * {@code causeway.*} placeholder" for exactly that reason.</p>
     */
    private static final Pattern CAUSEWAY_PLACEHOLDER =
            Pattern.compile("\\$\\{(causeway\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*)}");

    /** Read size for the constant-pool scan; a class file is a few KB, so one chunk usually does. */
    private static final int CHUNK = 64 * 1024;

    /**
     * Bytes kept from the previous chunk so a placeholder straddling a chunk boundary is still
     * matched — comfortably longer than any placeholder name.
     */
    private static final int OVERLAP = 64;

    @Test
    void everyCausewayPlaceholderOnTheClasspathResolvesFromApplicationYml() throws Exception {
        var scan = scanClasspath();

        assertThat(scan.placeholders())
                .as("the classpath scan found no no-default placeholder in the causeway namespace "
                        + "at all — either Causeway has since given them all a default (then delete "
                        + "this test) or the scan is broken (then fix it); a vacuous guardrail is "
                        + "worse than none. Classpath roots scanned: %s", scan.roots())
                .isNotEmpty();

        var environment = environmentFromApplicationYml();

        for (var placeholder : scan.placeholders()) {
            assertThatCode(() -> environment.resolveRequiredPlaceholders("${" + placeholder + "}"))
                    .as("application.yml must supply %s: a @Value placeholder with no default "
                            + "fails at bean creation, not at build time", placeholder)
                    .doesNotThrowAnyException();
        }
    }

    // ------------------------------------------------------------------ the environment under test

    /**
     * The real {@code application.yml} (filtered into {@code target/classes} by the build), and
     * nothing else — the ambient system properties / environment variables are dropped so that only
     * the committed file can satisfy a placeholder.
     */
    private static StandardEnvironment environmentFromApplicationYml() throws IOException {
        var environment = new StandardEnvironment();
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    // ------------------------------------------------------------------ the constant-pool scrape

    /**
     * Every classpath entry we can discover, plus the placeholders scraped out of them.  The roots
     * come back too, so a scan that finds nothing can say what it actually looked at.
     */
    private record ScanResult(Set<String> placeholders, List<Path> roots) {}

    /**
     * Surefire forks the test JVM with a <strong>manifest-only booter jar</strong>: {@code
     * java.class.path} then holds the module's own directories plus that jar, and the dependency jars
     * are named only in the booter jar's {@code Class-Path} manifest attribute.  A
     * {@code getResources("")} enumeration sees the directories but <em>none</em> of the jars — which
     * is precisely the part the guardrail needs, because Causeway ships the placeholders — so the
     * manifest entries are expanded here explicitly.
     */
    private static List<Path> classpathRoots() {
        var roots = new LinkedHashSet<Path>();
        for (var entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                addRootAndManifestEntries(Paths.get(entry), roots);
            }
        }
        return List.copyOf(roots);
    }

    /**
     * Adds one classpath entry, and — for a jar — every entry its {@code Class-Path} manifest
     * attribute names (recursively, so a chain of booter jars works).  The visited set also breaks
     * cycles.
     */
    private static void addRootAndManifestEntries(final Path entry, final Set<Path> roots) {
        if (!roots.add(entry)
                || !Files.isRegularFile(entry)
                || !entry.toString().endsWith(".jar")) {
            return;
        }
        try (var jar = new JarFile(entry.toFile())) {
            var manifest = jar.getManifest();
            var classPath = manifest == null
                    ? null
                    : manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null) {
                return;
            }
            var base = entry.getParent();
            for (var reference : classPath.trim().split("\\s+")) {
                if (reference.isBlank()) {
                    continue;
                }
                if (reference.startsWith("file:")) {
                    addRootAndManifestEntries(Paths.get(URI.create(reference)), roots);
                } else if (base != null) {
                    addRootAndManifestEntries(base.resolve(reference).normalize(), roots);
                }
            }
        } catch (IOException ignored) {
            // An unreadable jar contributes nothing; if EVERY root is unreadable the scan finds no
            // placeholder and the non-empty assertion below reports it, roots and all.
        }
    }

    private static ScanResult scanClasspath() {
        var roots = classpathRoots();
        var found = new TreeSet<String>();
        for (var root : roots) {
            try {
                if (Files.isDirectory(root)) {
                    scanDirectory(root, found);
                } else if (Files.isRegularFile(root) && root.toString().endsWith(".jar")) {
                    try (var jar = new JarFile(root.toFile())) {
                        scanJar(jar, found);
                    }
                }
            } catch (IOException ignored) {
                // One unreadable root must not hide the others.
            }
        }
        return new ScanResult(found, roots);
    }

    private static void scanDirectory(final Path root, final Set<String> found) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (var classFile : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                try (InputStream in = Files.newInputStream(classFile)) {
                    scan(in, found);
                }
            }
        }
    }

    private static void scanJar(final JarFile jar, final Set<String> found) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;   // skip resources outright: reading them is the expensive part
            }
            try (InputStream in = jar.getInputStream(entry)) {
                scan(in, found);
            }
        }
    }

    /** Scans a class file's bytes for the placeholder strings its constant pool holds. */
    private static void scan(final InputStream in, final Set<String> found) throws IOException {
        var buffer = new byte[CHUNK];
        var carry = new byte[0];
        int read;
        while ((read = in.read(buffer)) > 0) {
            var window = Arrays.copyOf(carry, carry.length + read);
            System.arraycopy(buffer, 0, window, carry.length, read);
            var matcher = CAUSEWAY_PLACEHOLDER.matcher(
                    new String(window, StandardCharsets.ISO_8859_1));
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            carry = Arrays.copyOfRange(window, Math.max(0, window.length - OVERLAP), window.length);
        }
    }
}
