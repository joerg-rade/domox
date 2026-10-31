package domox.webapp.config;

import org.apache.causeway.viewer.wicket.model.causeway.WicketApplicationInitializer;
import org.apache.wicket.protocol.http.WebApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spring-managed {@link WicketApplicationInitializer} that registers
 * {@link StaleComponentRecoveryListener}, so that a click on a stale link (a link whose
 * component was rendered by an earlier render of the page) re-renders the page instead of
 * failing with {@code ComponentNotFoundException}.
 *
 * <p>Same hook as {@link WebjarFixerInitializer}: Causeway's {@code CausewayWicketApplication}
 * injects {@code List<WicketApplicationInitializer>} and applies each one during its own
 * {@code init()}, which is the supported way to refine the Wicket application from Spring.</p>
 *
 * <p>See {@link StaleComponentRecoveryListener} for the symptom, the triggers and the
 * (deliberate) trade-off of this behaviour.</p>
 */
@Component
public class StaleLinkRecoveryInitializer implements WicketApplicationInitializer {

    private static final Logger log = LoggerFactory.getLogger(StaleLinkRecoveryInitializer.class);

    @Override
    public void init(final WebApplication application) {
        application.getRequestCycleListeners().add(new StaleComponentRecoveryListener());
        log.info("Registered stale-link recovery listener (ComponentNotFoundException -> re-render page)");
    }
}
