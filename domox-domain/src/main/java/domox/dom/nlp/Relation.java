package domox.dom.nlp;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.isis.applib.annotation.*;
import org.apache.isis.applib.jaxb.PersistentEntityAdapter;
import org.apache.isis.applib.services.repository.RepositoryService;
import org.apache.isis.persistence.jpa.applib.integration.IsisEntityListener;
import org.hibernate.type.ObjectType;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.util.List;

@javax.persistence.Entity
@javax.persistence.Table(schema = "domox")
@javax.persistence.EntityListeners(IsisEntityListener.class)
@DomainObject(logicalTypeName = "domox.Relation", entityChangePublishing = Publishing.ENABLED)
@DomainObjectLayout(cssClassFa = "arrows-h")
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@XmlJavaTypeAdapter(PersistentEntityAdapter.class)
@ToString(onlyExplicitlyIncluded = true)
@Data
public class Relation implements Comparable<Relation> {

    @Inject
    @javax.persistence.Transient
    RepositoryService repositoryService;

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy = javax.persistence.GenerationType.AUTO)
    @javax.persistence.Column(name = "id", nullable = false)
    private Long id;

    @javax.persistence.Version
    @javax.persistence.Column(name= "version", nullable = false)
    @PropertyLayout(fieldSetId = "metadata", sequence = "999")
    private long version;

    @javax.persistence.Column(nullable = false)
    private RelationType type;

    //TODO cardinality 1:2
    @javax.persistence.OneToMany(mappedBy = "relation")
    private List<TypedDependency> modelDependencies;

    //region > compareTo, toString
    @Override
    public int compareTo(final Relation other) {
        return org.apache.isis.applib.util.ObjectContracts.compare(this, other, "id");
    }

    @Override
    public String toString() {
        return org.apache.isis.applib.util.ObjectContracts.toString(this, "id");
    }
    //endregion

}
