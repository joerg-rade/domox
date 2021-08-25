package domox.fixture;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import lombok.AllArgsConstructor;
import org.apache.isis.applib.services.registry.ServiceRegistry;
import org.apache.isis.testing.fixtures.applib.personas.PersonaWithBuilderScript;
import org.apache.isis.testing.fixtures.applib.personas.PersonaWithFinder;
import org.apache.isis.testing.fixtures.applib.setup.PersonaEnumPersistAll;

@AllArgsConstructor
public enum SimpleObject_persona
        implements PersonaWithBuilderScript<SimpleObjectBuilder>, PersonaWithFinder<Author> {

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

    private final String name;

    @Override
    public SimpleObjectBuilder builder() {
        return new SimpleObjectBuilder().setName(name);
    }

    @Override
    public Author findUsing(final ServiceRegistry serviceRegistry) {
        Authors authors = serviceRegistry.lookupService(Authors.class).orElse(null);
        return authors.findByNameExact(name);
    }

    public static class PersistAll extends PersonaEnumPersistAll<SimpleObject_persona, Author> {
        public PersistAll() {
            super(SimpleObject_persona.class);
        }
    }
}
