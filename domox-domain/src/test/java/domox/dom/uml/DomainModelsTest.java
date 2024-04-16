package domox.dom.uml;

import org.antlr.v4.runtime.misc.OrderedHashSet;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;

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
        final Set<ParameterCdd> inputTypeList = new OrderedHashSet<>();
        final ParameterCdd p1 = new ParameterCdd();
        p1.setType("String");
        inputTypeList.add(p1);
        final ParameterCdd p2 = new ParameterCdd();
        p2.setType("Boolean");
        inputTypeList.add(p2);
        inputTypeList.add(p2);
        action.setInputTypeList(inputTypeList);
        action.setOutputType(Integer.class.getSimpleName());
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