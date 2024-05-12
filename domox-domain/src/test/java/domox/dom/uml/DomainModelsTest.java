package domox.dom.uml;

import org.antlr.v4.runtime.misc.OrderedHashSet;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
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
        final Set<PropertyCdd> propertyList = new HashSet<>();
        final PropertyCdd property1 = new PropertyCdd("property1", "int");
        propertyList.add(property1);
        final PropertyCdd property2 = new PropertyCdd("property2", "Integer");
        propertyList.add(property2);

        final Set<ParameterCdd> inputTypeList = new OrderedHashSet<>();
        final ParameterCdd p1 = new ParameterCdd("venirIci", "String");
        inputTypeList.add(p1);
        final ParameterCdd p2 = new ParameterCdd("toutSuit", "Boolean");
        inputTypeList.add(p2);

        final String outputType = Integer.class.getSimpleName();
        final ActionCdd action = new ActionCdd("ordre", inputTypeList, outputType);
        final Set<ActionCdd> actionList = new HashSet<>();
        actionList.add(action);

        final Set<AssociationCdd> associationList = new HashSet<>();

        final ClassCdd clazz1 = new ClassCdd(
                "Alice",
                propertyList,
                actionList,
                associationList);

        final ClassCdd clazz2 = new ClassCdd(
                "Bob",
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>());
        clazz2.setClassType(ClassType.ROLE);

        final AssociationCdd association = new AssociationCdd("command", clazz1, clazz2);
        association.setTargetCardinality("2");
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
        assertTrue(plantUml.contains("class \"Alice"));
        System.out.println(plantUml);
    }
}