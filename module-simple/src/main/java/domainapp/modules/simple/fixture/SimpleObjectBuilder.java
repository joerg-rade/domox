package domainapp.modules.simple.fixture;

import javax.inject.Inject;

import org.apache.isis.testing.fixtures.applib.fixturescripts.BuilderScriptWithResult;

import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject;
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObjects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain = true)
public class SimpleObjectBuilder extends BuilderScriptWithResult<SimpleObject> {

    @Getter @Setter
    private String name;

    @Override
    protected SimpleObject buildResult(final ExecutionContext ec) {

        checkParam("name", ec, String.class);

        return wrap(simpleObjects).create(name);
    }

    // -- DEPENDENCIES

    @Inject SimpleObjects simpleObjects;

}
