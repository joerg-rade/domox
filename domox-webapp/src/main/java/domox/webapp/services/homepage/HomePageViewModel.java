package domox.webapp.services.homepage;

import java.util.List;

import javax.inject.Inject;

import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.annotation.HomePage;
import org.apache.isis.applib.annotation.Nature;
import org.apache.isis.applib.services.i18n.TranslatableString;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;

@DomainObject(
        nature = Nature.VIEW_MODEL,
        objectType = "domainapp.HomePageViewModel"
        )
@HomePage
@DomainObjectLayout()
public class HomePageViewModel {

    public TranslatableString title() {
        return TranslatableString.tr("{num} objects", "num", getObjects().size());
    }

    public List<Author> getObjects() {
        return authors.listAll();
    }

    @Inject
    Authors authors;
}
