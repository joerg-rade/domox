package org.apache.isis.domox.modules.ksimple

import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject
import org.apache.isis.testing.fixtures.applib.modules.ModuleWithFixtures
import org.apache.isis.testing.fixtures.applib.teardown.TeardownFixtureAbstract
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.validation.annotation.Validated

@Configuration
@Import()
@ComponentScan
@EnableConfigurationProperties(Configuration::class)
class SimpleModule : ModuleWithFixtures {
    // @get:Override
    val teardownFixture = object : TeardownFixtureAbstract() {
        @Override
        override fun execute(executionContext: ExecutionContext?) {
            deleteFrom(SimpleObject::class.java)
        }
    }

    open class PropertyDomainEvent<S, T> : org.apache.isis.applib.events.domain.PropertyDomainEvent<S, T>()
    open class CollectionDomainEvent<S, T> : org.apache.isis.applib.events.domain.CollectionDomainEvent<S, T>()
    open class ActionDomainEvent<S> : org.apache.isis.applib.events.domain.ActionDomainEvent<S>()

    @ConfigurationProperties("app.ksimple-module")
    @Validated
    class Configuration {
        private val types = Types()
        fun getTypes(): Types {
            return types
        }

        class Types {
            private val name = Name()
            fun getName(): Name {
                return name
            }

            class Name {
                private val validation = Validation()
                fun getValidation(): Validation {
                    return validation
                }

                class Validation {
                    private val prohibitedCharacters = "!&%$".toCharArray()
                    fun getProhibitedCharacters(): CharArray {
                        return prohibitedCharacters
                    }

                    private val message = "Character '{character}' is not allowed"
                    fun getMessage(): String {
                        return message
                    }
                }
            }
        }
    }
}
