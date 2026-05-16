# Maven Multi-Module Best Practices

## 1. Overview

This guide explains best practices for maintaining a healthy multi-module Maven project. DoMoX follows these patterns across its four modules:
- **domox** (parent)
- **domox-domain** (entity/JPA layer)
- **domox-service** (business logic)
- **domox-webapp** (web application / Spring Boot)

---

## 2. Project Structure

A clean multi-module layout:

```
domox/
├─ pom.xml                     (packaging: pom)
├─ domox-domain/
│  └─ pom.xml
├─ domox-service/
│  └─ pom.xml
└─ domox-webapp/
   └─ pom.xml
```

**Rules:**
- Root POM uses `packaging: pom`
- All modules listed in `<modules>`
- No circular dependencies
- Each module has a single responsibility

---

## 3. Properties and Version Management Rule

### Principle: Centralization by Scope

Version numbers and dependency management should be organized based on their scope of usage:

**Properties Placement:**
- **Root POM (`pom.xml`):** Properties used by **2 or more modules**
  - Examples: `kotlin.version`, `spring-boot.version`, `junit-jupiter.version`, `postgresql.version`
  - Shared infrastructure and testing frameworks
- **Module POM (e.g., `domox-domain/pom.xml`):** Properties used by **only that module**
  - Examples in domox-domain: `twelvemonkeys.version`, `batik-transcoder.version`
  - Module-specific visualization or processing libraries

**Dependency Management Placement:**
- **Root `<dependencyManagement>`:** Dependencies used by 2+ modules
  - All Causeway framework dependencies
  - Spring Boot and testing dependencies
  - Shared utilities (Lombok, Jackson, etc.)
- **Module `<dependencyManagement>`:** Dependencies used only within that module
  - Module-specific visualization (SVG processing)
  - Module-specific integrations

**Example:**
```xml
<!-- root pom.xml - used by multiple modules -->
<properties>
  <kotlin.version>2.2.0</kotlin.version>
  <junit-jupiter.version>5.10.2</junit-jupiter.version>
</properties>

<!-- domox-domain/pom.xml - used only by domox-domain -->
<properties>
  <twelvemonkeys.version>3.12.0</twelvemonkeys.version>
  <batik-transcoder.version>1.19</batik-transcoder.version>
</properties>

<!-- root pom.xml dependencyManagement - cross-module -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot</artifactId>
      <version>${spring-boot.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- domox-domain/pom.xml dependencyManagement - module-specific -->
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.twelvemonkeys.imageio</groupId>
      <artifactId>imageio-batik</artifactId>
      <version>${twelvemonkeys.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

**Benefits:**
- Clarity: Immediately see which module depends on which libraries
- Maintainability: Update versions only where they matter
- Reduced size: Parent POM not bloated with unused definitions
- Scalability: Easy to add new modules with their own specific dependencies

---

## 4. Parent POM Inheritance

Every module inherits from the root POM via `<parent>`:

```xml
<parent>
  <groupId>org.apache.causeway.domox</groupId>
  <artifactId>domox</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <relativePath>../pom.xml</relativePath>
</parent>
```

**Benefits:**
- Shared properties (Java version, compiler config)
- Shared plugins and their versions
- Consistent build behavior across all modules

---

## 5. Root-Level Dependency Management

Use `<dependencyManagement>` in the root POM for centralized cross-module version control:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.apache.causeway</groupId>
      <artifactId>causeway-applib</artifactId>
      <version>4.0.0-M1</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Child modules declare dependencies **without versions**:

```xml
<dependency>
  <groupId>org.apache.causeway</groupId>
  <artifactId>causeway-applib</artifactId>
</dependency>
```

**Benefits:**
- Centralized version control
- No version drift across modules
- Easier upgrades (change once in parent)
- Transitive dependency conflicts resolved upfront

---

## 6. Plugin Management

Define plugin versions only in the root POM's `<pluginManagement>`:

```xml
<pluginManagement>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.11.0</version>
      <configuration>
        <source>21</source>
        <target>21</target>
        <release>21</release>
      </configuration>
    </plugin>
    
    <plugin>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-maven-plugin</artifactId>
      <version>2.2.0</version>
      <configuration>
        <jvmTarget>21</jvmTarget>
      </configuration>
    </plugin>
  </plugins>
</pluginManagement>
```

Child modules should **only invoke** plugins, not define versions:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <!-- Version inherited from parent -->
</plugin>
```

---

## 7. DoMoX-Specific Configuration

### Java Version

All modules target **Java 21** (maximum compatibility with Kotlin 2.2.0):

```xml
<properties>
  <maven.compiler.source>21</maven.compiler.source>
  <maven.compiler.target>21</maven.compiler.target>
  <java.version>21</java.version>
</properties>
```

### Kotlin Configuration

Kotlin 2.2.0 requires `jvmTarget` to be explicitly set:

```xml
<plugin>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-maven-plugin</artifactId>
  <configuration>
    <jvmTarget>21</jvmTarget>
    <languageVersion>2.2</languageVersion>
    <apiVersion>2.2</apiVersion>
  </configuration>
</plugin>
```

### Apache Causeway Version Management

DoMoX uses Apache Causeway 4.0.0-M1. Repository configuration is centralized in the parent POM:

```xml
<repositories>
  <repository>
    <id>apache-releases</id>
    <url>https://repository.apache.org/content/repositories/releases/</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
  
  <repository>
    <id>apache-causeway-snapshots</id>
    <url>https://raw.githubusercontent.com/apache-causeway-committers/causeway-nightly/master/mvn-snapshots</url>
    <snapshots><enabled>true</enabled><updatePolicy>always</updatePolicy></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>
```

All child modules **inherit** these repositories; no duplication needed.

---

## 8. Transitive Dependency Management

When transitive dependencies have incorrect versions, override them via `<dependencyManagement>`:

```xml
<dependencyManagement>
  <dependencies>
    <!-- Override (do NOT declare as direct dependency) -->
    <dependency>
      <groupId>org.apache.causeway.extensions</groupId>
      <artifactId>causeway-extensions-audittrail-applib</artifactId>
      <version>${causeway.version}</version>
    </dependency>
  </dependencies>
</dependencyManagement>
```

This forces all modules to use the correct version without explicit declarations.

---

## 9. Security Vulnerability Fixes via Transitive Dependencies

### Identifying Vulnerabilities

**CVE scanning tools:**
- Maven plugin: `mvn dependency:check` (requires plugins setup)
- GitHub Dependabot: Automatic PR creation for vulnerable deps
- OWASP Dependency-Check: Comprehensive CVE scanning
- Manual check using CVE databases for key dependencies

**Example (from DoMoX project):**
```bash
# Check specific dependencies for CVEs
mvn validate  # If enforcer plugin is configured
# Or use external tools to scan project dependencies
```

### Fixing Transitive Vulnerabilities

When a transitive dependency has a known CVE, override it in the root POM's `<dependencyManagement>` section with a patched version:

```xml
<!-- In pom.xml dependencyManagement section -->
<dependencyManagement>
    <dependencies>
        <!-- Override transitive/vulnerable dependencies -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-compress</artifactId>
            <version>1.28.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot</artifactId>
            <version>3.5.14</version>  <!-- Fixes CVE-2026-40973 -->
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>42.7.11</version>  <!-- Fixes CVE-2026-42198 -->
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.18.6</version>  <!-- Fixes DoS vulnerability -->
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Important: Don't Declare Direct Dependencies

**Wrong approach:**
```xml
<!-- ❌ WRONG - This creates a direct dependency -->
<dependencies>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-compress</artifactId>
        <version>1.28.0</version>
    </dependency>
</dependencies>
```

**Correct approach:**
```xml
<!-- ✅ CORRECT - Declare ONLY in dependencyManagement -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-compress</artifactId>
            <version>1.28.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
<!-- The version is applied transitively to all modules -->
```

### DoMoX Security Patch Examples

**Spring Boot temporal directory DoS** (CVE-2026-40973):
- Affected: 3.5.0–3.5.13
- Fixed: 3.5.14+
- Patch: Override `spring-boot` version in dependencyManagement

**PostgreSQL PBKDF2 CPU exhaustion** (CVE-2026-42198):
- Affected: 42.7.2
- Fixed: 42.7.11+
- Patch: Override `postgresql` version

**Jackson Core number parsing DoS** (GHSA-72hv-8253-57qq):
- Affected: 2.20.0
- Fixed: 2.18.6+
- Patch: Override `jackson-core` version

**Apache Tomcat multiple critical CVEs** (20+ issues):
- Affected: 10.1.36 (transitively from Spring Boot)
- Fixed: 11.0.22+
- Patch: Override `tomcat-embed-core` version

### Verification After Patching

1. **Rebuild and verify no regressions:**
   ```bash
   mvn clean install -DskipTests
   ```

2. **Check dependency tree to confirm versions:**
   ```bash
   mvn dependency:tree | grep commons-compress  # Should show patched version
   ```

3. **Run full test suite:**
   ```bash
   mvn clean install  # Include tests
   ```

### When Patches Aren't Available

**Some CVEs cannot be immediately fixed:**
- New CVEs with no patch yet (0-day)
- Incompatible version constraints (e.g., Spring Framework 7.0.7 requires Spring Boot 3.2+, but your project uses 3.5.x)

**Action plan:**
1. Document the unpatchable CVEs with their CVE IDs
2. Add risk assessment (can it be mitigated at application level?)
3. Set reminder to upgrade when patches become available
4. Monitor for workarounds or alternative libraries

**Example (unpatchable):**
```xml
<!-- Spring Core CVE-2025-41249 - No patch available yet (Spring 6.2.6) -->
<!-- Will be fixable in next major Spring version -->
<!-- Current mitigation: Don't use @EnableMethodSecurity with generic superclasses -->
```

---

## 10. Module Responsibilities

### domox-domain
- JPA entities with Causeway annotations
- Domain model classes (Corpus, Document, Sentence, Token, etc.)
- Relationship definitions
- No business logic; data structures only

### domox-service
- Kotlin-based business logic
- Service layer (repositories, managers)
- Depends on domox-domain
- No UI dependencies

### domox-webapp
- Spring Boot web application
- Causeway viewers (Wicket, REST)
- Application bootstrap and configuration
- Depends on domox-service (which transitively pulls domox-domain)

### domox (parent)
- **Build configuration only** (no source code)
- Dependency/plugin management
- Repository configuration
- Shared properties

**Anti-Pattern:** Never put business logic in the parent POM.

---

## 11. Build Operations

### Clean build all modules:
```bash
mvn clean install
```

### Build specific module and dependencies:
```bash
mvn -pl domox-webapp -am install
```

### Skip tests:
```bash
mvn install -DskipTests
```

### Rebuild after dependency changes:
```bash
mvn clean dependency:tree
mvn dependency:resolve-plugins
mvn install
```

### Check bytecode compatibility:
```bash
mvn dependency:tree | grep "^\["
# Look for consistent versions; report conflicts
```

---

## 12. Understanding the Module Dependency Graph

DoMoX follows a **layered architecture**:

```
domox-webapp (top)
    ↓ depends on
domox-service (middle)
    ↓ depends on
domox-domain (bottom)
    
domox (parent configuration, no dependencies)
```

**Rules:**
- Downward dependencies only (webapp → service → domain)
- No upward dependencies (domain must not know about service/webapp)
- No circular dependencies
- Service can use domain; domain cannot use service

---

## 13. Common Issues & Solutions

### Issue: "Cannot find artifact X:Y:Z:jar:0.1.0-SNAPSHOT"

**Cause:** Transitive dependency with wrong version (common in milestone releases)

**Solution:** Add override to `<dependencyManagement>`:
```xml
<dependency>
  <groupId>org.apache.causeway.viewer</groupId>
  <artifactId>causeway-viewer-restfulobjects-viewer</artifactId>
  <version>${causeway.version}</version>
</dependency>
```

### Issue: "Unknown JVM target version: 24"

**Cause:** Kotlin doesn't support Java 24

**Solution:** Set Java target to 21 in all pom.xml files and kotlin-maven-plugin configuration

### Issue: "class file has wrong version 68.0, should be 65.0"

**Cause:** Mixed bytecode from different Java versions (partial rebuild)

**Solution:** Run `mvn clean` to delete old compiled classes and rebuild entirely

### Issue: Repository returns 404 for artifact

**Cause:** Artifact doesn't exist in specified repository

**Solution:**
1. Check if repository URL is correct
2. Use `mvn dependency:tree` to verify transitive dependencies
3. Add repository that hosts the artifact
4. Or downgrade to version that exists

---

## 14. Repository & Settings Configuration

### Global Settings (~/.m2/settings.xml)

Store credentials here, never in POMs:

```xml
<settings>
  <servers>
    <server>
      <id>nexus3</id>
      <username>user</username>
      <password>pass</password>
    </server>
  </servers>
</settings>
```

### Project Settings (.mvn/project-settings.xml)

Repository URLs without credentials:

```xml
<settings>
  <profiles>
    <profile>
      <id>project-repos</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>project-repos</activeProfile>
  </activeProfiles>
</settings>
```

### Activation

Configure Maven to use both:

**.mvn/maven.config:**
```
-s ~/.m2/settings.xml
-s .mvn/project-settings.xml
```

---

## 15. Build Performance

1. **Use incremental builds during development:**
   ```bash
   mvn -pl domox-service -am install
   ```

2. **Enable Maven build cache (Maven 3.9+):**
   Add to `.mvn/maven.config`:
   ```
   -Daether.enhancedDependencyManagement=true
   ```

3. **Reuse compiled artifacts** when only source comments change

4. **Run integration tests separately** from unit tests in CI

5. **Keep modules focused** — one responsibility per module

---

## 16. Testing Best Practices

- **Unit tests:** Each module's own `src/test/java`
- **Integration tests:** Use TestContainers for databases
- **Mock dependencies:** Never depend on external services during tests
- **Test module isolation:** Tests in domox-webapp don't require domox-service compilation (transitive pull only)

---

## 17. Release & Versioning

### Semantic Versioning

Use `MAJOR.MINOR.PATCH-SNAPSHOT` format:
- `0.1.0-SNAPSHOT` — development
- `0.1.0` — release
- `0.2.0` — new features
- `1.0.0` — production ready

### Single Version Strategy

All modules share one version (`0.1.0-SNAPSHOT`). Incrementing happens for the entire project, not individual modules.

---

## 18. Health Checklist

- [ ] Root POM uses `packaging: pom`
- [ ] All modules declared in `<modules>`
- [ ] All modules have `<parent>` pointing to root
- [ ] Versions only in `<dependencyManagement>` (not in child modules)
- [ ] Plugins only in `<pluginManagement>` (not in child modules)
- [ ] No circular module dependencies
- [ ] Java/Kotlin versions consistent across all modules
- [ ] Repositories centralized in parent (not duplicated)
- [ ] No credentials in pom.xml files
- [ ] Each module has clear, single responsibility
- [ ] Dependency tree is acyclic (use `mvn dependency:tree`)
- [ ] Tests pass independently per module

---

## 19. References

- [Maven POM Reference](https://maven.apache.org/pom.html)
- [Maven Dependency Management](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [Apache Causeway Documentation](https://causeway.apache.org/)
- [Kotlin Maven Plugin](https://kotlinlang.org/docs/maven.html)


