package domox.dom.crc;

import domox.DomainModule;
import domox.dom.AbstractEntity;
import jakarta.inject.Named;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.Bounding;
import org.apache.causeway.applib.annotation.DomainObject;
import org.apache.causeway.applib.annotation.DomainObjectLayout;
import org.apache.causeway.applib.annotation.Editing;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = DomainModule.SCHEMA, name = "DomainModel")
@Named(DomainModule.NAMESPACE + ".DomainModel")
@DomainObject(bounding = Bounding.BOUNDED, editing = Editing.ENABLED)
@DomainObjectLayout(cssClassFa = "road", describedAs = "A DM. ...")
@NoArgsConstructor
@Data
public class DomainModel extends AbstractEntity implements Comparable<ClassCdd> {

    @OneToMany(mappedBy = "domainModel", cascade = CascadeType.ALL)
    public List<ClassCdd> classList = new ArrayList<>();

    @Override
    public int compareTo(@NotNull ClassCdd o) {
        return 0; //FIXME
    }
}
