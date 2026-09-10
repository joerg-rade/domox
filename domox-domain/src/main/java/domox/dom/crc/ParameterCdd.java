package domox.dom.crc;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@Named(DomainModule.NAMESPACE + ".ParameterCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Parameter is the Type of an argument of an Action")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class ParameterCdd
        extends Candidate
        implements Comparable<ParameterCdd> {

    public ParameterCdd(String name, String type) {
        this.setCandidateName(name);
        this.type = type;
    }

    @Getter
    @Setter
    @ManyToOne()
    @JoinColumn(nullable = false)
    @Property()
    public ActionCdd actionCdd;

    @Getter
    @Setter
    @Property
    @Column(nullable = false)
    private String type;

    @Override
    public int compareTo(@NotNull ParameterCdd o) {
        //FIXME
        return 0;
    }

    public String toPlantUmlString() {
        return getCandidateName() + ": " + type;
    }
}
