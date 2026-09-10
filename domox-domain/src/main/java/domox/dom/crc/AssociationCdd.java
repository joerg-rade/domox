package domox.dom.crc;

import domox.DomainModule;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.apache.causeway.applib.annotation.Property;
import org.jetbrains.annotations.NotNull;

@Entity
@Table(schema = DomainModule.SCHEMA)
@Named(DomainModule.NAMESPACE + ".AssociationCdd")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A Class candidate ...")
@NoArgsConstructor
public class AssociationCdd
        extends Candidate
        implements Comparable<AssociationCdd> {

    public AssociationCdd(String name, ClassCdd source, ClassCdd target) {
        this.setCandidateName(name);
        this.source = source;
        this.target = target;
    }

    @Setter
    @Property
    @JoinColumn(nullable = false)
    @ManyToOne
    private ClassCdd classCdd;

    @Setter
    @Property
    @OneToOne
    private ClassCdd source;

    @Setter
    @Property
    @OneToOne
    private ClassCdd target;

    @Setter
    @Property
    @Column(nullable = false)
    private AssociationType type;

    @Setter
    @Property
    @Column(nullable = false)
    private String sourceCardinality = "";

    @Setter
    @Property
    @Column(nullable = false)
    private String targetCardinality = "";

    @Override
    public int compareTo(@NotNull AssociationCdd o) {
        return 0; //FIXME
    }

    public String toPlantUmlString() {
        final String arrow = AssociationType.ASSOCIATION.symbol;
        final String relation = quote(sourceCardinality) + arrow + quote(targetCardinality);
        return source.getCandidateName() + relation + target.getCandidateName() + ": " + getCandidateName();
    }

    private String quote(String s) {
        if (s.isBlank()) return s;
        return "\"" + s + "\"";
    }
}
