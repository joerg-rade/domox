package domox.dom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.isis.applib.annotation.DomainService;
import org.apache.isis.applib.annotation.DomainServiceLayout;
import org.apache.isis.applib.annotation.NatureOfService;

import javax.inject.Inject;

@DomainService(nature = NatureOfService.VIEW,
        objectType = "domox.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor(onConstructor_ = {@Inject})
@Slf4j
public class Analysis {

}