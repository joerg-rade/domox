package domox.dom.nlp;

import domox.DomainModule;
import domox.dom.rqm.Document;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.util.ObjectContracts;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "Sentence")
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Sentence")
@DomainObject()
@DomainObjectLayout(cssClassFa = "paragraph")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Sentence implements Comparable<Sentence> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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

    @Column(nullable = true, length = 8192)
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
        return ObjectContracts.compare(this, other, "id");
    }
    //endregion

}
