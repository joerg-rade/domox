package domox.webapp.bdd.stepdefs.infrastructure;

import org.apache.isis.applib.annotation.OrderPrecedence;

import domox.webapp.integtests.ApplicationIntegTestAbstract;

public class BootstrapStepDef extends ApplicationIntegTestAbstract {

    @io.cucumber.java.Before(order = OrderPrecedence.FIRST)
    public void bootstrap() {
        // empty
    }

}
