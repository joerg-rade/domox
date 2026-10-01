package domox.dom.fixtures;

import domox.dom.rqm.Corpora;
import jakarta.inject.Inject;
import org.apache.causeway.testing.fixtures.applib.fixturescripts.FixtureScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fixture script that loads every {@code UC*.md} sample use-case document into the
 * "Pet Shop Use Cases" corpus by delegating to {@link Corpora#loadSampleFiles()}.
 *
 * <p>Runs the full analysis pipeline (match → candidate creation → archetype
 * classification → late-binding resolution). Re-running is safe: the duplicate-content
 * guard inside {@link Corpora} skips documents that have already been analysed.
 * Discovered and run from the Prototyping → <em>Fixture Scripts</em> menu (or as the
 * app's {@code initial-script}) via {@link DomoxFixtureScriptsSpecificationProvider}.</p>
 */
@Component
public class LoadSampleFileFixture extends FixtureScript {

    private static final Logger log = LoggerFactory.getLogger(LoadSampleFileFixture.class);

    @Inject
    private Corpora corpora;

    @Override
    protected void execute(final ExecutionContext executionContext) {
        final int loaded = corpora.loadSampleFiles();
        log.info("Loaded {} new UC* sample documents via fixture script '{}'.",
                loaded, getFriendlyName());
    }

}