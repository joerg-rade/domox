package domox.dom.uml;

import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.persistence.jpa.applib.integration.CausewayEntityListener;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = "domox")
@EntityListeners(CausewayEntityListener.class)
@Named("domox.PropertyCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Property is a Member of a Class")
//@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@NoArgsConstructor
@Slf4j
@Data
public class PropertyCdd
        extends Candidate
        implements Comparable<PropertyCdd> {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private Object type;

    @Override
    public int compareTo(@NotNull PropertyCdd o) {
        //FIXME
        return 0;
    }
}
