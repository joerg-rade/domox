package generate;

import domox.dom.crc.ActionCdd;
import domox.dom.crc.AssociationCdd;
import domox.dom.crc.ClassCdd;
import domox.dom.crc.DomainModels;
import domox.dom.crc.ParameterCdd;
import domox.dom.crc.PropertyCdd;
import domox.dom.crc.SubDomain;
import org.apache.causeway.applib.services.repository.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaCodeTest {

    private DomainModels classUnderTest;

    @Mock
    RepositoryService mockRepositoryService;

    @BeforeEach
    public void setUp() {
        classUnderTest = new DomainModels(
                mockRepositoryService);
    }

    @Test
    void testGenerateJavaCode() {
        // given
        final List<PropertyCdd> propertyList = new ArrayList<>();
        final PropertyCdd property1 = new PropertyCdd("property1", "int");
        propertyList.add(property1);
        final PropertyCdd property2 = new PropertyCdd("property2", "Integer");
        propertyList.add(property2);

        final List<ParameterCdd> inputTypeList = new ArrayList<>();
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

        // when — no SubDomain assigned: generateJavaCode must not crash
        final String javaCode = classUnderTest.generateJavaCode(clazz1);
        // then
        assertNotNull(javaCode, "Generated code must not be null");
        assertTrue(javaCode.startsWith("package "),
                "Generated code must start with a package declaration");
        assertTrue(javaCode.contains("package alice;"),
                "Missing subDomain must fall back to the lowercased candidate name as package");
        System.out.println(javaCode);
    }

    @Test
    void testGenerateJavaCodeUsesSubDomainAsPackage() {
        // given — a ClassCdd with an assigned SubDomain (set by the reviewer)
        final ClassCdd clazz = new ClassCdd("Alice", List.of(), List.of(), List.of());
        final SubDomain subDomain = new SubDomain();
        subDomain.setName("pets");
        clazz.setSubDomain(subDomain);

        // when
        final String javaCode = classUnderTest.generateJavaCode(clazz);

        // then
        assertNotNull(javaCode, "Generated code must not be null");
        assertTrue(javaCode.startsWith("package pets;"),
                "Assigned subDomain name must be used as the package declaration");
    }

}
