package domox.dom.nlp;

import domox.Constants;
import domox.DomainModule;
import domox.dom.rqm.Document;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Nature;
import org.apache.causeway.applib.annotation.Programmatic;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.Title;
import org.apache.causeway.applib.jaxb.PersistentEntityAdapter;
import org.apache.causeway.applib.util.ObjectContracts;
import org.apache.causeway.applib.value.Blob;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Entity
@Table(schema = DomainModule.SCHEMA, name = "Sentence")
@EntityListeners(CausewayEntityListener.class)
@Named(DomainModule.NAMESPACE + ".Sentence")
@DomainObject(nature = Nature.ENTITY)
@DomainObjectLayout(cssClassFa = "paragraph")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
public class Sentence implements Comparable<Sentence> {

    @Title
    String title() {
        return this.document.getTitle() + ".S" + this.id + "." + this.version;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    @Programmatic
    private Long id;

    @Version
    @Programmatic
    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 20)
    @Property()
    @Getter
    @Setter
    private PartOfSpeechType type;

    @Column(nullable = false, length = 2048)
    @Property()
    @Getter
    @Setter
    private String text;

    @OneToMany(mappedBy = "sentence", cascade = CascadeType.ALL)
    @Getter
    @Setter
    private List<Token> tokenList = new ArrayList<>();

    @Lob
    @Getter
    @Setter
    private Blob image;

    @Programmatic
    public Sentence updateImageFromBytes(byte[] bytes, String filename) {
        final Blob blob = new Blob(filename, Constants.svgMimeType, bytes);
        this.image = blob;
        return this;
    }

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

    @Programmatic
    public Token getToken(int index) {
        final List<Token> tokens = new ArrayList<>(this.tokenList);
        if (index >= tokens.size()) {
            log.debug("index >= tokenList");
            return null;
        }
        return tokens.get(index);
    }

}
