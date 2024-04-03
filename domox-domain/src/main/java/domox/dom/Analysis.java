package domox.dom;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;

import jakarta.inject.Named;

@DomainService
@Named("domox.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@NoArgsConstructor
@Slf4j
public class Analysis {

}