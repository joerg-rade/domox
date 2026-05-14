package domox.dom;

import domox.DomainModule;
import jakarta.inject.Named;
import lombok.NoArgsConstructor;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DomainService
@Named(DomainModule.NAMESPACE + ".Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@NoArgsConstructor
public class Analysis {
    private static final Logger log = LoggerFactory.getLogger(Analysis.class);

}