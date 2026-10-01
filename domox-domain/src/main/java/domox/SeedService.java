package domox;

import domox.dom.rqm.Documents;
import jakarta.inject.Inject;
import org.apache.causeway.applib.events.metamodel.MetamodelEvent;
import org.apache.causeway.applib.events.metamodel.MetamodelListener;
import org.apache.causeway.applib.services.iactnlayer.InteractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@Service
public class SeedService implements MetamodelListener {
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    
    // ...existing code...
    private final InteractionService interactionService;
    private final Documents documents;

    @Inject
    public SeedService(InteractionService interactionService, Documents documents) {
        this.interactionService = interactionService;
        this.documents = documents;
    }

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