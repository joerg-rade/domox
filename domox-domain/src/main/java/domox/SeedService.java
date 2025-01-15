package domox;

import domox.dom.rqm.Documents;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.causeway.applib.events.metamodel.MetamodelEvent;
import org.apache.causeway.applib.events.metamodel.MetamodelListener;
import org.apache.causeway.applib.services.iactnlayer.InteractionService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class SeedService implements MetamodelListener {
    private final InteractionService interactionService;
    private final Documents documents;

    @Override
    public void onMetamodelLoaded() {
        execute();
    }

    public void execute() {
        log.info("DB seeding started");
        interactionService.runAnonymous(documents::loadFileSample);
        log.info("DB seeding ended");
    }

    @Override
    public void onMetamodelAboutToBeLoaded() {
        MetamodelListener.super.onMetamodelAboutToBeLoaded();
    }

    @Override
    public void onMetamodelEvent(MetamodelEvent event) {
        MetamodelListener.super.onMetamodelEvent(event);
    }

}