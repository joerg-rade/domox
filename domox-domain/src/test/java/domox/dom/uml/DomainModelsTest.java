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
        final ClassCdd clazz1 = new ClassCdd();
        clazz1.setName("Alice");

        final PropertyCdd property1 = new PropertyCdd();
        property1.setName("property1");
        property1.setType("int");
        final PropertyCdd property2 = new PropertyCdd();
        property2.setName("property2");
        property2.setType("Integer");
        final List<PropertyCdd> propertyList = new ArrayList<>();
        propertyList.add(property1);
        propertyList.add(property2);
        clazz1.setPropertyList(propertyList);

        final ActionCdd action = new ActionCdd();
        action.setName("ordre");
        final Set<ParameterCdd> inputTypeList = new OrderedHashSet<>();
        final ParameterCdd p1 = new ParameterCdd();
        p1.setName("venirIci");
        p1.setType("String");
        inputTypeList.add(p1);
        final ParameterCdd p2 = new ParameterCdd();
        p2.setName("toutSuit");
        p2.setType("Boolean");
        inputTypeList.add(p1);
        inputTypeList.add(p2);
        action.setInputTypeList(inputTypeList);
        action.setOutputType(Integer.class.getSimpleName());
        clazz1.addAction(action);

        final ClassCdd clazz2 = new ClassCdd();
        clazz2.setName("Bob");

        final AssociationCdd association = new AssociationCdd(clazz1, clazz2);
        association.setName("command");
        clazz1.addAssociation(association);

        final DomainModel domainModel = classUnderTest.create();
        domainModel.getClassList().add(clazz1);
        domainModel.getClassList().add(clazz2);

        // when
        final String plantUml = classUnderTest.generateUmlDiagram(domainModel);
        // then
        assertEquals(2, domainModel.getClassList().size());
        assertTrue(plantUml.startsWith("@startuml"), "@startuml tag missing");
        assertTrue(plantUml.endsWith("@enduml"), "@enduml tag missing");
        assertTrue(plantUml.contains("class \"(C) Alice"));
        System.out.println(plantUml);
    }
}