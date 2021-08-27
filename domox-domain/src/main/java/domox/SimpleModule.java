package domox;

import domox.dom.rqm.Author;
import lombok.Data;
import org.apache.isis.testing.fixtures.applib.fixturescripts.FixtureScript;
import org.apache.isis.testing.fixtures.applib.modules.ModuleWithFixtures;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.validation.annotation.Validated;

@Configuration
/*@Import({
        IsisModuleCoreRuntimeServices.class,
        IsisModuleTestingFixturesApplib.class,
})
@PropertySources({
        @PropertySource(IsisPresets.NoTranslations),
        @PropertySource(IsisPresets.SilenceWicket),
})*/
@ComponentScan
@EnableJpaRepositories
@EntityScan(basePackageClasses = {SimpleModule.class})
public class SimpleModule implements ModuleWithFixtures {

    @Override
    public FixtureScript getTeardownFixture() {
        return new FixtureScript() {
            @Override
            protected void execute(ExecutionContext executionContext) {
                repositoryService.removeAll(Author.class);
            }
        };
    }

    public static class PropertyDomainEvent<S, T>
            extends org.apache.isis.applib.events.domain.PropertyDomainEvent<S, T> {
    }

    public static class CollectionDomainEvent<S, T>
            extends org.apache.isis.applib.events.domain.CollectionDomainEvent<S, T> {
    }

    public static class ActionDomainEvent<S>
            extends org.apache.isis.applib.events.domain.ActionDomainEvent<S> {
    }

    @Data
    @Validated
    public static class Configuration {
        private final Types types = new Types();

        @Data
        public static class Types {
            private final Name name = new Name();

            @Data
            public static class Name {
                private final Validation validation = new Validation();

                @Data
                public static class Validation {
                    private char[] prohibitedCharacters = "!&%$".toCharArray();
                    private String message = "Character '{character}' is not allowed";
                }
            }
        }
    }
}
