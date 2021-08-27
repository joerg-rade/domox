package domox.dom.nlp;

import domox.dom.rqm.Document;
import lombok.Data;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.value.Blob;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@DomainObject(logicalTypeName = "domox.Sentence", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "paragraph")
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Sentence implements Comparable<Sentence> {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(nullable = false)
    @Programmatic
    private Long id;

    @javax.persistence.Version
    @Programmatic
    @javax.persistence.Column(nullable = false)
    private int version;

    @javax.persistence.Column(nullable = false)
    @Property()
    private String text;

    @javax.persistence.Column(nullable = true)
    @Property()
    private String typedDependencies;

    @javax.persistence.Column(nullable = true)
    @Property()
    private Blob diagram;

    @javax.persistence.ManyToOne()
    @javax.persistence.JoinColumn(name = "documentId")
    @Property()
    private Document document;

    //region > compareTo, toString
    @Override
    public int compareTo(final Sentence other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
