package domox.dom;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared enumeration of the classpath {@code UC*.md} use-case files.
 * <p>
 * {@link domox.dom.rqm.Documents} both loads those files into the corpus and exposes a
 * {@code findByFileName} dropdown of every available use-case, so both code paths need the same
 * list.  Keeping this helper free of Spring injection avoids a circular dependency: it is a
 * plain static utility.
 */
public final class UcResources {

    private UcResources() {
    }

    /** Classpath pattern selecting every UC* markdown document in the resources. */
    public static final String UC_RESOURCE_PATTERN = "classpath*:UC*.md";

    /**
     * Discovers the individual UC* markdown files on the classpath and returns their filenames
     * ({@code Resource#getFilename()}), sorted for a deterministic processing order.
     *
     * @return every available {@code UC*.md} file name
     * @throws IllegalStateException if the classpath resources cannot be enumerated
     */
    public static List<String> listUcFilenames() {
        try {
            final Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(UC_RESOURCE_PATTERN);
            return Arrays.stream(resources)
                    .map(Resource::getFilename)
                    .filter(java.util.Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to enumerate UC* documents from '" + UC_RESOURCE_PATTERN + "'", e);
        }
    }
}