package domainapp.modules.simple.types

import org.apache.isis.applib.annotation.*
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import javax.jdo.annotations.Column

@Column(length = Notes.MAX_LEN, allowsNull = "true")
@Property(editing = Editing.ENABLED, maxLength = Notes.MAX_LEN)
@PropertyLayout(named = "Notes", multiLine = 10, hidden = Where.ALL_TABLES)
@Parameter(maxLength = Notes.MAX_LEN)
@ParameterLayout(named = "Notes", multiLine = 10)
@Target(ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
annotation class Notes {
    companion object {
        const val MAX_LEN: Int = 4000
    }
}
