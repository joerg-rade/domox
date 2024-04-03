package domox.dom.uml;

import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Publishing;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.ActionCdd")
@DomainObject(entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "bolt")
//@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@Data
public class ActionCdd
        extends Candidate
        implements Comparable<ActionCdd> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Property
    @Column
    @OneToMany(mappedBy = "inputTypeList")
    private List<Object> inputTypeList = new ArrayList<>();

    @Property
    @Column(nullable = false)
    private Object outputType;

    @Override
    public int compareTo(@NotNull ActionCdd o) {
        return 0; //FIXME
    }
}
