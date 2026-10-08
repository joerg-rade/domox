package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Named;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.apache.causeway.applib.annotation.PropertyLayout;
import org.apache.causeway.applib.annotation.Title;
import org.jspecify.annotations.NonNull;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "SubDomain")
@Named(DomainModule.NAMESPACE + ".SubDomain")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(
        cssClassFa = "folder",
        describedAs = "A named business / functional sub-domain that class candidates can be assigned to during review")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@ToString(onlyExplicitlyIncluded = true)
public class SubDomain extends AbstractEntity implements Comparable<SubDomain> {

    @Title
    public String title() {
        return name;
    }

    @Column(nullable = false)
    @Getter
    @Setter
    @ToString.Include
    @PropertyLayout(
            named = "Name",
            describedAs = "Name of the sub-domain (e.g. \"Checkout\", \"Inventory\")")
    private String name;

    @Override
    public int compareTo(@NonNull SubDomain o) {
        return Long.compare(this.getId(), o.getId());
    }
}