package domainapp.webapp.application.services.homepage;

import java.util.List;

import javax.inject.Inject;

import org.apache.isis.applib.annotation.DomainObject;
import org.apache.isis.applib.annotation.DomainObjectLayout;
import org.apache.isis.applib.annotation.HomePage;
import org.apache.isis.applib.annotation.Nature;
import org.apache.isis.applib.services.i18n.TranslatableString;

import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObject;
import org.apache.isis.domox.modules.ksimple.dom.so.SimpleObjects;

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

    public List<SimpleObject> getObjects() {
        return simpleObjects.listAll();
    }

    @Inject SimpleObjects simpleObjects;
}
