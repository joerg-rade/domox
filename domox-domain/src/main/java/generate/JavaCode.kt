package generate

import domox.dom.uml.ActionCdd
import domox.dom.uml.ClassCdd
import domox.dom.uml.PropertyCdd

class JavaCode(val clazz: ClassCdd) : BaseCode() {
    lateinit var clazzName:String
    lateinit var packageName: String
    init {
        clazzName = clazz.name
        packageName = clazz.packageName
        code = buildHeader()
        code += buildClass()
    }

    private fun buildHeader():String {
        return """package sample;
            
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".$clazzName")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor
"""
    }

    private fun buildClass(): String {
        val prefix = "public class $clazzName implements Comparable<$clazzName>"
        val body = buildBody()
        return prefix + body
    }

    private fun buildBody(): String {
        var body = buildPersistenceProperties()
        clazz.propertyList.forEach() { p ->
            body += build(p)
        }
        clazz.actionList.forEach() { a ->
            body += build(a)
        }
        body += buildComparator()
        return wrapInBrackets("$NL $body $NL")
    }
    private fun buildPersistenceProperties(): String  {
        return """
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

        """}

    private fun build(p: PropertyCdd): String {
        var result = "@Getter$NL@Setter$NL@Property$NL"
        result += "public ${p.type} ${p.name};$NL$NL"
        return indent(result)
    }

    private fun build(a: ActionCdd): String {
        val result = "public void ${a.name}() {$NL}$NL"
        return indent(result)
    }

    private fun buildComparator():String {
        return """
    @Override
    public int compareTo(@NotNull $clazzName o) {
        return 0; //FIXME
    }            
        """}

    private fun wrapInBrackets(s: String): String {
        return "{$s}"
    }

    private fun indent(multilineString: String): String {
        return multilineString.replace("\n", "\n\t")
    }
}