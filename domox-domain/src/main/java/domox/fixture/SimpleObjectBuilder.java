package domox.fixture;

import javax.inject.Inject;

import org.apache.isis.testing.fixtures.applib.fixturescripts.BuilderScriptWithResult;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;

@Accessors(chain = true)
public class SimpleObjectBuilder extends BuilderScriptWithResult<Author> {

    @Getter @Setter
    private String name;

    @Override
    protected Author buildResult(final ExecutionContext ec) {

        checkParam("name", ec, String.class);

        return wrap(authors).create(name);
    }

    // -- DEPENDENCIES

    @Inject
    Authors authors;

}
