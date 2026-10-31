package domox.dom.crc.service;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.model.ClassType;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.DomainModel;
import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.ResponsibilityParameter;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.apache.causeway.commons.internal.assertions._Assert.assertEquals;
import static org.apache.causeway.commons.internal.assertions._Assert.assertTrue;

@ExtendWith(MockitoExtension.class)
class DomainModelsTest {
    private DomainModels classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @BeforeEach
    public void setUp() {
        classUnderTest = new DomainModels(
                mockRepositoryService);
    }

    @Test
    void testGenerateUml() {
        // given
        final List<AttributeCdd> attributeList = new ArrayList<>();
        final AttributeCdd property1 = new AttributeCdd("property1", "int");
        attributeList.add(property1);
        final AttributeCdd property2 = new AttributeCdd("property2", "Integer");
        attributeList.add(property2);

        final List<ResponsibilityParameter> inputTypeList = new ArrayList<>();
        final ResponsibilityParameter p1 = new ResponsibilityParameter("venirIci", "String");
        inputTypeList.add(p1);
        final ResponsibilityParameter p2 = new ResponsibilityParameter("toutSuit", "Boolean");
        inputTypeList.add(p2);

        final String outputType = Integer.class.getSimpleName();
        final ResponsibilityCdd action = new ResponsibilityCdd("ordre", inputTypeList, outputType);
        final List<ResponsibilityCdd> responsibilityList = new ArrayList<>();
        responsibilityList.add(action);

        final List<CollaboratorCdd> collaboratorList = new ArrayList<>();

        final ClassCdd clazz1 = new ClassCdd(
                "Alice",
                attributeList,
                responsibilityList,
                collaboratorList);

        final ClassCdd clazz2 = new ClassCdd(
                "Bob",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>());
        clazz2.setClassType(ClassType.ROLE);

        final CollaboratorCdd association = new CollaboratorCdd("command", clazz1, clazz2);
        association.setTargetCardinality("2");
        clazz1.addCollaborator(association);

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