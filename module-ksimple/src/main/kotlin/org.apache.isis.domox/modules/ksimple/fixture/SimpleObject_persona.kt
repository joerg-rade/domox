package org.apache.isis.domox.modules.ksimple.fixture

import org.apache.isis.applib.services.registry.ServiceRegistry
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObjects
import org.apache.isis.testing.fixtures.applib.api.PersonaWithBuilderScript
import org.apache.isis.testing.fixtures.applib.api.PersonaWithFinder
import org.apache.isis.testing.fixtures.applib.setup.PersonaEnumPersistAll

enum class SimpleObject_persona(name: String) :
        PersonaWithBuilderScript<SimpleObjectBuilder?>,
        PersonaWithFinder<SimpleObject?> {
    FOO("Foo"),
    BAR("Bar"),
    BAZ("Baz"),
    FRODO("Frodo"),
    FROYO("Froyo"),
    FIZZ("Fizz"),
    BIP("Bip"),
    BOP("Bop"),
    BANG("Bang"),
    BOO("Boo");

    open override fun builder(): SimpleObjectBuilder? {
        return SimpleObjectBuilder().setName(name)
    }

    open override fun findUsing(serviceRegistry: ServiceRegistry): SimpleObject {
        val simpleObjects: SimpleObjects = serviceRegistry
                .lookupService(SimpleObjects::class.java)
                .orElse(null)
        return simpleObjects.findByNameExact(name)
    }

    class PersistAll : PersonaEnumPersistAll<
            SimpleObject_persona, SimpleObject?>(
            SimpleObject_persona::class.java)

}
