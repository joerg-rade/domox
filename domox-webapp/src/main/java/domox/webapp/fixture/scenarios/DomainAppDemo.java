package domox.webapp.fixture.scenarios;

import javax.inject.Inject;

import org.apache.isis.testing.fixtures.applib.fixturescripts.FixtureScript;
import org.apache.isis.testing.fixtures.applib.modules.ModuleWithFixturesService;

import domox.fixture.SimpleObject_persona;

public class DomainAppDemo extends FixtureScript {

    @Inject
    ModuleWithFixturesService moduleWithFixturesService;

    @Override
    protected void execute(final ExecutionContext ec) {
        ec.executeChildren(this, moduleWithFixturesService.getTeardownFixture());
        ec.executeChild(this, new SimpleObject_persona.PersistAll());
    }

}
