package domox.dom;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.annotation.DomainService;
import org.apache.causeway.applib.annotation.DomainServiceLayout;
import org.apache.causeway.applib.annotation.NatureOfService;

@DomainService(nature = NatureOfService.VIEW,
        logicalTypeName = "domox.Analysis")
@DomainServiceLayout(named = "Start", menuBar = DomainServiceLayout.MenuBar.PRIMARY)
@RequiredArgsConstructor
@Slf4j
public class Analysis {

}