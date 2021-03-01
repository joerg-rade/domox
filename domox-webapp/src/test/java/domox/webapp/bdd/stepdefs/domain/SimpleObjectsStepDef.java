package domox.webapp.bdd.stepdefs.domain;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import domox.dom.rqm.Author;
import domox.dom.rqm.Authors;
import domox.webapp.bdd.CucumberTestAbstract;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

public class SimpleObjectsStepDef extends CucumberTestAbstract {

    @Given("^there (?:is|are).* (\\d+) simple object[s]?$")
    public void there_are_N_simple_objects(int n) {

        final List<Author> list = wrap(authors).listAll();
        assertThat(list.size(), is(n));
    }

    @When("^.*create (?:a|another) .*simple object$")
    public void create_a_simple_object() {

        wrap(authors).create(UUID.randomUUID().toString());
    }

    @Inject
    protected Authors authors;

}
