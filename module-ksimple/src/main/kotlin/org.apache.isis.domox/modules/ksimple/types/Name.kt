package org.apache.isis.domox.modules.ksimple.types

import org.apache.isis.applib.annotation.Parameter
import org.apache.isis.applib.annotation.ParameterLayout
import org.apache.isis.applib.annotation.Property
import org.apache.isis.applib.services.i18n.TranslatableString
import org.apache.isis.applib.spec.AbstractSpecification2
import org.apache.isis.domox.modules.ksimple.SimpleModule
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import javax.inject.Inject
import javax.jdo.annotations.Column

@Column(length = Name.MAX_LEN, allowsNull = "false")
@Property(mustSatisfy = [Name.Specification::class], maxLength = Name.MAX_LEN)
@Parameter(mustSatisfy = [Name.Specification::class], maxLength = Name.MAX_LEN)
@ParameterLayout(named = "Name")
@Target(ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
annotation class Name {
    class Specification : AbstractSpecification2<String?>() {
        @Inject
        private val configuration: SimpleModule.Configuration? = null

        @Override
        override fun satisfiesTranslatableSafely(name: String?): TranslatableString? {
            if (name == null) {
                return null
            }
            val prohibitedCharacters = configuration!!.getTypes().getName().getValidation().getProhibitedCharacters()
            for (prohibitedCharacter in prohibitedCharacters) {
                if (name.contains("" + prohibitedCharacter)) {
                    val message: String = configuration.getTypes().getName().getValidation().getMessage()
                    return TranslatableString.tr(message, "character", "" + prohibitedCharacter)
                }
            }
            return null
        }
    }

    companion object {
        const val MAX_LEN: Int = 40
    }
}
