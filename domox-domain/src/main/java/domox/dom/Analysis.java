package domox.dom;

import domox.DomainModule;
import jakarta.inject.Named;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;

@DomainService
@Named(DomainModule.NAMESPACE + ".Analysis")
//@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@NoArgsConstructor
@Slf4j
public class Analysis {

}