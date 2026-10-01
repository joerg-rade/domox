package domox.dom.fixtures;

import jakarta.inject.Named;
import org.apache.causeway.testing.fixtures.applib.fixturescripts.FixtureScriptsSpecification;
import org.apache.causeway.testing.fixtures.applib.fixturescripts.FixtureScriptsSpecificationProvider;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for how this module's fixture scripts are discovered and run.
 *
 * <p>Declares the package prefix {@code domox.dom.fixtures}, so every
 * {@link org.apache.causeway.testing.fixtures.applib.fixturescripts.FixtureScript}
 * Spring bean under that package becomes runnable from the Prototyping →
 * <em>Fixture Scripts</em> menu. {@link LoadSampleFileFixture} is marked as the default
 * script.</p>
 */
@Service
@Named("domox.FixtureScriptsSpecificationProvider")
public class DomoxFixtureScriptsSpecificationProvider implements FixtureScriptsSpecificationProvider {

    @Override
    public FixtureScriptsSpecification getSpecification() {
        return FixtureScriptsSpecification.builder(DomoxFixtureScriptsSpecificationProvider.class)
                .withRunScriptDefault(LoadSampleFileFixture.class)
                .build();
    }

}