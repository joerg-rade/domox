package domox;

import domox.dom.rqm.Corpora;
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
    private final Corpora corpora;

    @Inject
    public SeedService(InteractionService interactionService, Corpora corpora) {
        this.interactionService = interactionService;
        this.corpora = corpora;
    }

    @Override
    public void onMetamodelLoaded() {
        execute();
    }

    public void execute() {
        log.info("DB seeding started");
        interactionService.runAnonymous(corpora::loadSampleFiles);
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