package domox.dom.uml;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;

@Ignore
class DomainModelsTest {

    @Mock
    RepositoryService mockRepositoryService;

    // ClassUnderTest
    DomainModels domainModels;

    @BeforeEach
    public void setUp() {
        domainModels = new DomainModels(
                mockRepositoryService
                );
    }

    @Test
    void testGenerateUml() {
        // given
        final ClassCdd clazz = new ClassCdd();
        clazz.setName("SampleClass");
        final ActionCdd action = new ActionCdd();
        action.setName("sampleAction");
        final List<Object> inputTypeList = new ArrayList<>();
        inputTypeList.add(String.class);
        inputTypeList.add(Boolean.class);
        action.setInputTypeList(inputTypeList);
        action.setOutputType(Integer.class);
        final List<ActionCdd> actionList = new ArrayList<>();
        actionList.add(action);
        clazz.setActionList(actionList);

        final DomainModel domainModel = domainModels.create();
        domainModel.getClassList().add(clazz);
        // when
        final String plantUml = domainModels.generateUmlDiagram(domainModel);
        // then
        assertEquals(1, domainModel.getClassList().size());
        assertTrue(plantUml.startsWith("@startuml"));
        assertTrue(plantUml.endsWith("@enduml"));
        assertTrue(plantUml.contains("class SampleClass"));

    }
}