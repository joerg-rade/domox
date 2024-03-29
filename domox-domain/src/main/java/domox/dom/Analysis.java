package domox.dom;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.NatureOfService;

import javax.inject.Named;

@DomainService(nature = NatureOfService.VIEW)
@Named("domox.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@NoArgsConstructor
@Slf4j
public class Analysis {

}