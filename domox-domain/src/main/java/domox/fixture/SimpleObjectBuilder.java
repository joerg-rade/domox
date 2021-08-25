package domox.fixture;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.isis.testing.fixtures.applib.personas.BuilderScriptWithResult;

import javax.inject.Inject;

@Accessors(chain = true)
public class SimpleObjectBuilder extends BuilderScriptWithResult<Author> {

    @Getter
    @Setter
    private String name;

    @Override
    protected Author buildResult(final ExecutionContext ec) {
        checkParam("name", ec, String.class);
        return wrap(authors).create(name);
    }

    @Inject
    Authors authors;

}
