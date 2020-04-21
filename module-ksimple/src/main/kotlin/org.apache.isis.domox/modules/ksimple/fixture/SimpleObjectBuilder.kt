package  org.apache.isis.domox.modules.ksimple.fixture

import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObjects
import org.apache.isis.testing.fixtures.applib.fixturescripts.BuilderScriptWithResult
import javax.inject.Inject

class SimpleObjectBuilder : BuilderScriptWithResult<SimpleObject?>() {
    private var name: String? = null
    fun setName(value: String): SimpleObjectBuilder {
        name = value
        return this
    }

    override fun buildResult(ec: ExecutionContext?): SimpleObject {
        checkParam("name", ec, String::class.java)
        return wrap(simpleObjects)!!.create(name)
    }

    // -- DEPENDENCIES
    @Inject
    var simpleObjects: SimpleObjects? = null

}
