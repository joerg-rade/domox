package generate;

import domox.dom.uml.ActionCdd;
import domox.dom.uml.AssociationCdd;
import domox.dom.uml.ClassCdd;
import domox.dom.uml.DomainModelRepository;
import domox.dom.uml.DomainModels;
import domox.dom.uml.ParameterCdd;
import domox.dom.uml.PropertyCdd;
import org.antlr.v4.runtime.misc.OrderedHashSet;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JavaCodeTest {

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
    void testGenerateJavaCode() {
        // given
        final List<PropertyCdd> propertyList = new ArrayList<>();
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
        final List<ActionCdd> actionList = new ArrayList<>();
        actionList.add(action);

        final List<AssociationCdd> associationList = new ArrayList<>();

        final ClassCdd clazz1 = new ClassCdd(
                "Alice",
                propertyList,
                actionList,
                associationList);

        // when
        final String javaCode = classUnderTest.generateJavaCode(clazz1);
        // then
 /*       assertEquals(2, domainModel.getClassList().size());
        assertTrue(plantUml.startsWith("@startuml"), "@startuml tag missing");
        assertTrue(plantUml.endsWith("@enduml"), "@enduml tag missing");
        assertTrue(plantUml.contains("class \"Alice")); */
        System.out.println(javaCode);
    }

}
