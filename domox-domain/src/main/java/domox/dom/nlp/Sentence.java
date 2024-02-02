package domox.dom.nlp;

import domox.dom.rqm.Document;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import javax.inject.Named;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.Sentence")
@DomainObject(nature = Nature.ENTITY, entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "paragraph")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Sentence implements Comparable<Sentence> {

    @Id
    @GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 2048)
    @Property()
    @Getter
    @Setter
    private String text;

    @Column(nullable = true, length = 2048)
    @Property()
    @Getter
    @Setter
    private String typedDependencies;

    @Column(nullable = true)
    @Property()
    @Getter
    @Setter
    private Blob diagram;

    @ManyToOne()
    @JoinColumn(name = "documentId")
    @Property()
    @Getter
    @Setter
    private Document document;

    //region > compareTo, toString
    @Override
    public int compareTo(final Sentence other) {
        return org.apache.causeway.applib.util.ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
