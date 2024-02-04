package domox.dom.uml;

import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;

@RunWith(SpringRunner.class)
@ExtendWith(MockitoExtension.class)
class DomainModelsTest {
    private DomainModels classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;
    @Mock
    DomainModelRepository mockDomainModelRepository;

    @BeforeEach
    public void setUp() {
        classUnderTest = new DomainModels(
                mockRepositoryService,
                mockDomainModelRepository);
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

        final DomainModel domainModel = classUnderTest.create();
        domainModel.getClassList().add(clazz);
        // when
        final String plantUml = classUnderTest.generateUmlDiagram(domainModel);
        // then
        assertEquals(1, domainModel.getClassList().size());
        assertTrue(plantUml.startsWith("@startuml"), "@startuml tag missing");
        assertTrue(plantUml.endsWith("@enduml"), "@enduml tag missing");
        assertTrue(plantUml.contains("class \"(C) SampleClass"));
    }
}