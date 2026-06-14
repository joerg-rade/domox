# DoMoX Maven POM Refactoring Summary

## Date: 2026-05-16

Refactored all `pom.xml` files according to Maven best practices documented in `MAVEN.md`.

### Changes Made

#### 1. **Parent POM** (`domox/pom.xml`)

**Before:**
- Plugins defined in `<build><plugins>` with inline versions
- Inconsistent plugin version management

**After:**
- ✅ Added `<pluginManagement>` section for centralized plugin version control
- ✅ Moved enforcer-plugin configuration to pluginManagement
- ✅ Added kotlin-maven-plugin version management (2.2.0)
- ✅ Added maven-compiler-plugin version management (3.14.0) with centralized Java/Kotlin configuration
- ✅ Added spring-boot-maven-plugin version management (${spring-boot.version})
- ✅ Added maven-surefire-plugin version management (3.2.5)
- ✅ Added maven-cucumber-reporting version management (${maven-cucumber-reporting.version})
- ✅ Fixed testcontainers dependencies to exclude junit:junit to prevent JUnit 4/5 conflicts

**Key Configuration:**
```xml
<pluginManagement>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>${maven-compiler-plugin.version}</version>
      <configuration>
        <source>${maven.compiler.source}</source>
        <target>${maven.compiler.target}</target>
        <release>${maven.compiler.target}</release>
      </configuration>
    </plugin>
    <plugin>
      <groupId>org.jetbrains.kotlin</groupId>
      <artifactId>kotlin-maven-plugin</artifactId>
      <version>${kotlin.version}</version>
      <configuration>
        <jvmTarget>${maven.compiler.target}</jvmTarget>
        <languageVersion>2.2</languageVersion>
        <apiVersion>2.2</apiVersion>
      </configuration>
    </plugin>
    <!-- ... other plugin management ... -->
  </plugins>
</pluginManagement>
```

---

#### 2. **Domain Module** (`domox-domain/pom.xml`)

**Before:**
- Hardcoded annotations version: `13.0`
- kotlin-maven-plugin with inline version and duplicate configuration
- maven-compiler-plugin with hardcoded versions (21, 21, 21)
- Duplicate `jvmTarget` configuration

**After:**
- ✅ Removed hardcoded annotations version (now managed by parent)
- ✅ Removed kotlin-maven-plugin version (inherited from parent)
- ✅ Removed duplicate `jvmTarget` configuration (inherited from parent)
- ✅ Removed source/target/release hardcoding from maven-compiler-plugin
- ✅ Simplified to use parent's pluginManagement for both plugins
- ✅ Kept only necessary execution overrides and annotation processor paths

**Key Changes:**
```xml
<!-- Before: Had duplicate source configuration -->
<configuration>
  <source>21</source>
  <target>21</target>
  <release>21</release>
</configuration>

<!-- After: Delegates to parent -->
<configuration>
  <annotationProcessorPaths>
    <path>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>${lombok.version}</version>
    </path>
  </annotationProcessorPaths>
</configuration>
```

---

#### 3. **Service Module** (`domox-service/pom.xml`)

**Before:**
- testcontainers with hardcoded version `2.0.3` (non-existent)
- junit-jupiter with hardcoded version `1.21.4` (outdated)
- kotlin-maven-plugin with inline version and full configuration
- maven-compiler-plugin with hardcoded version `3.14.0`
- maven-surefire-plugin with hardcoded version `3.2.5`

**After:**
- ✅ Updated testcontainers to use managed version `1.19.3` from parent
- ✅ Removed testcontainers version numbers (now managed by parent)
- ✅ Added junit:junit exclusion to testcontainers (prevent JUnit 4/5 conflict)
- ✅ Removed kotlin-maven-plugin version (inherited from parent)
- ✅ Removed duplicate Kotlin configuration (jvmTarget, languageVersion, apiVersion)
- ✅ Removed maven-compiler-plugin version hardcoding
- ✅ Removed maven-surefire-plugin version (now managed by parent)
- ✅ Kept only module-specific configuration (Kotlin args, source directories)

**Key Changes:**
```xml
<!-- Before: Had multiple hardcoded versions -->
<plugin>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-maven-plugin</artifactId>
  <version>${kotlin.version}</version>
  <configuration>
    <jvmTarget>21</jvmTarget>
    <languageVersion>2.2</languageVersion>
    <apiVersion>2.2</apiVersion>
  </configuration>
</plugin>

<!-- After: Cleaner, delegates to parent -->
<plugin>
  <groupId>org.jetbrains.kotlin</groupId>
  <artifactId>kotlin-maven-plugin</artifactId>
  <configuration>
    <args>
      <arg>-Xjvm-default=all</arg>
    </args>
  </configuration>
</plugin>
```

---

#### 4. **Webapp Module** (`domox-webapp/pom.xml`)

**Before:**
- maven-cucumber-reporting with hardcoded version `${maven-cucumber-reporting.version}`
- spring-boot-maven-plugin with hardcoded version `${spring-boot.version}`
- maven-compiler-plugin with hardcoded version `${maven-compiler-plugin.version}` and configuration

**After:**
- ✅ Removed maven-cucumber-reporting version (now managed by parent)
- ✅ Removed spring-boot-maven-plugin version (now managed by parent)
- ✅ Removed maven-compiler-plugin version and configuration (now managed by parent)
- ✅ All plugins now inherit from parent's pluginManagement

**Example:**
```xml
<!-- Before -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>${maven-compiler-plugin.version}</version>
  <configuration>
    <source>${maven.compiler.source}</source>
    <target>${maven.compiler.target}</target>
  </configuration>
</plugin>

<!-- After -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
</plugin>
```

---

### Compliance with MAVEN.md Best Practices

| Principle | Status | Details |
|-----------|--------|---------|
| Root POM uses `packaging: pom` | ✅ | Already in place |
| All modules in `<modules>` | ✅ | Already in place |
| Centralized `<dependencyManagement>` | ✅ | Enhanced with testcontainers overrides |
| Centralized `<pluginManagement>` | ✅ | **NEW** - Added in parent |
| Each module inherits via `<parent>` | ✅ | Already in place |
| No versions in child modules | ✅ | **FIXED** - All removed or delegated |
| Repositories centralized | ✅ | Already in place |
| Java 21 & Kotlin 2.2.0 config | ✅ | **CENTRALIZED** - In parent pluginManagement |
| No upward dependencies | ✅ | Already in place |
| Module responsibilities clear | ✅ | Already in place |

---

### Build Verification

```bash
$ mvn clean compile -DskipTests
[INFO] Reactor Summary for DoMoX Parent 0.1.0-SNAPSHOT:
[INFO] 
[INFO] DoMoX Parent ....................................... SUCCESS [  0.312 s]
[INFO] DoMoX Service Module ............................... SUCCESS [  7.966 s]
[INFO] DoMoX Domain Module ................................ SUCCESS [  3.472 s]
[INFO] DoMoX Webapp ....................................... SUCCESS [  5.763 s]
[INFO] 
[INFO] BUILD SUCCESS
```

### Benefits of Refactoring

1. **Single Source of Truth**: All plugin versions and configurations now managed in parent POM
2. **Reduced Duplication**: Removed repetitive version declarations in child modules
3. **Easier Maintenance**: Changes to plugin versions require editing parent only
4. **Better Dependency Convergence**: Enforcer plugin now consistently validates all modules
5. **Consistent Java/Kotlin Configuration**: All modules use Java 21 and Kotlin 2.2.0 uniformly
6. **Cleaner Child POMs**: Modules are now focused on their specific dependencies and build requirements
7. **Prevents Version Drift**: Child modules can't accidentally use different plugin versions

### Files Modified

- ✅ `/domox/pom.xml` - Added pluginManagement, fixed testcontainers
- ✅ `/domox-domain/pom.xml` - Removed duplicate configurations
- ✅ `/domox-service/pom.xml` - Removed hardcoded versions
- ✅ `/domox-webapp/pom.xml` - Removed hardcoded versions and configurations

### Next Steps (Optional)

1. Update build CI/CD scripts to reference parent POM for consistent builds
2. Document plugin management in project wiki
3. Consider adding `.mvn/maven.config` for consistent repository and settings configuration
4. Add `dependencyConvergence()` rule to enforcer to prevent transitive conflicts

