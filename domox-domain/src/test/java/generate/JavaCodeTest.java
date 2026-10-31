package generate;

import domox.dom.crc.model.ResponsibilityCdd;
import domox.dom.crc.model.CollaboratorCdd;
import domox.dom.crc.model.ClassCdd;
import domox.dom.crc.service.DomainModels;
import domox.dom.crc.model.ResponsibilityParameter;
import domox.dom.crc.model.AttributeCdd;
import domox.dom.crc.model.SubDomain;
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
