# DEVOXXGENIE.md

## Project Guidelines

### Build Commands

- **Build:** `./mvn clean install -DskipTests`
- **Test:** `./mvn test`
- **Single Test:** `./mvn test --tests ClassName.methodName`
- **Clean:** `./mvn clean`
- **Run:** `./mvn spring-boot:run`

### Code Style

- **Formatting:** Use IDE or checkstyle for formatting
- **Naming:**
  - Use camelCase for variables, methods, and fields
  - Use PascalCase for classes and interfaces
  - Use SCREAMING_SNAKE_CASE for constants
- **Documentation:** Use JavaDoc for documentation
- **Imports:** Organize imports and avoid wildcard imports
- **Exception Handling:** Prefer specific exceptions and document throws

### Dependencies

The project uses the following main dependencies:

- **JUnit** - Testing framework
- **Spring Framework** - Application framework
- **Lombok** - Code generation library

See pom.xml for the complete dependency list.
