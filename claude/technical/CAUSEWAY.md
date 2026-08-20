## **Apache Causeway 3.6.0 Annotations Best Practices**

### **1. Domain Object Annotations**
These annotations define how domain objects (entities, view models, and mixins) are exposed in the UI.

#### **`@DomainObject`**
- **Purpose**: Marks a class as a domain object and configures its behavior.
- **Best Practices**:
    - Use `entityChangePublishing = Publishing.ENABLED` for entities that require audit logging or event publishing.
    - Use `bounding = Bounding.BOUNDED` for domain objects with a finite set of instances (e.g., reference data).
    - Use `editing = Editing.ENABLED` to allow inline editing in tables.
    - Use `autoCompleteRepository` and `autoCompleteMethod` for dynamic dropdowns.

```java
@DomainObject(
      entityChangePublishing = Publishing.ENABLED,
      bounding = Bounding.BOUNDED,
      editing = Editing.ENABLED
  )
  public class Customer { ... }
```


#### **`@DomainObjectLayout`**
- **Purpose**: Controls the UI layout and appearance of domain objects.
- **Best Practices**:
    - Use `cssClassFa` to assign a Font Awesome icon to the domain object.
    - Use `describedAs` to provide a user-friendly description.
    - Use `paged` to enable pagination for large datasets.

```java
@DomainObjectLayout(
      cssClassFa = "user",
      describedAs = "A customer of the system",
      paged = 25
  )
  public class Customer { ... }
```


---

### **2. Property Annotations**
These annotations define how properties are displayed and edited in the UI.

#### **`@Property`**
- **Purpose**: Marks a field as a property and configures its behavior.
- **Best Practices**:
    - Use `editing = Editing.DISABLED` to make a property read-only.
    - Use `commandPublishing = Publishing.ENABLED` to enable command publishing for audit trails.
    - Use `executionPublishing = Publishing.ENABLED` to enable execution publishing for tracking changes.

```java
@Property(
      editing = Editing.DISABLED,
      commandPublishing = Publishing.ENABLED,
      executionPublishing = Publishing.ENABLED
  )
  private String name;
```


#### **`@PropertyLayout`**
- **Purpose**: Controls the UI layout of properties.
- **Best Practices**:
    - Use `fieldSetId` and `sequence` to organize properties into fieldsets.
    - Use `hidden = Where.ALL_TABLES` to hide a property from tables.
    - Use `multiLine = 3` to display a property as a multi-line text field.

```java
@PropertyLayout(
      fieldSetId = "contactDetails",
      sequence = "1",
      multiLine = 3
  )
  private String address;
```


---

### **3. Action Annotations**
These annotations define how actions (methods) are exposed in the UI.

#### **`@Action`**
- **Purpose**: Marks a method as an action and configures its behavior.
- **Best Practices**:
    - Use `semantics = SemanticsOf.SAFE` for read-only actions.
    - Use `semantics = SemanticsOf.IDEMPOTENT` for actions that can be retried safely.
    - Use `semantics = SemanticsOf.NON_IDEMPOTENT` for actions that modify data.
    - Use `commandPublishing = Publishing.ENABLED` to enable command publishing.

```java
@Action(
      semantics = SemanticsOf.SAFE,
      commandPublishing = Publishing.ENABLED
  )
  public List<Customer> findByName(String name) { ... }
```


#### **`@ActionLayout`**
- **Purpose**: Controls the UI layout and appearance of actions.
- **Best Practices**:
    - Use `sequence` to define the order of actions in the UI.
    - Use `cssClassFa` to assign a Font Awesome icon to the action.
    - Use `position = Position.PANEL` to display the action in a panel.
    - Use `describedAs` to provide a user-friendly description.

```java
@ActionLayout(
      sequence = "1",
      cssClassFa = "search",
      describedAs = "Search for customers by name"
  )
  public List<Customer> findByName(String name) { ... }
```


---

### **4. Collection Annotations**
These annotations define how collections are displayed in the UI.

#### **`@Collection`**
- **Purpose**: Marks a field as a collection and configures its behavior.
- **Best Practices**:
    - Use `typeOf = Customer.class` to specify the type of elements in the collection.
    - Use `editing = Editing.DISABLED` to make the collection read-only.

```java
@Collection(typeOf = Order.class)
  private List<Order> orders;
```


#### **`@CollectionLayout`**
- **Purpose**: Controls the UI layout of collections.
- **Best Practices**:
    - Use `defaultView = "table"` to display the collection as a table by default.
    - Use `sortedBy = "name"` to sort the collection by a specific property.
    - Use `paged = 10` to enable pagination for large collections.

```java
@CollectionLayout(
      defaultView = "table",
      sortedBy = "orderDate",
      paged = 10
  )
  private List<Order> orders;
```


---

### **5. Parameter Annotations**
These annotations define how action parameters are displayed and validated.

#### **`@Parameter`**
- **Purpose**: Configures the behavior of action parameters.
- **Best Practices**:
    - Use `optionality = Optionality.OPTIONAL` to make a parameter optional.
    - Use `regexPattern = "\\d{5}"` to validate input using a regex pattern.

```java
public void updateCustomer(
      @Parameter(optionality = Optionality.OPTIONAL)
      String phoneNumber
  ) { ... }
```


#### **`@ParameterLayout`**
- **Purpose**: Controls the UI layout of action parameters.
- **Best Practices**:
    - Use `named = "Customer Name"` to provide a user-friendly label.
    - Use `multiLine = 2` to display a parameter as a multi-line text field.
    - Use `describedAs` to provide a description for the parameter.

```java
public void updateCustomer(
      @ParameterLayout(
          named = "Customer Name",
          describedAs = "The full name of the customer"
      )
      String name
  ) { ... }
```


---

### **6. Domain Service Annotations**
These annotations define how domain services are exposed in the UI.

#### **`@DomainService`**
- **Purpose**: Marks a class as a domain service and configures its behavior.
- **Best Practices**:
    - Use `nature = NatureOfService.VIEW` for services that provide read-only functionality.
    - Use `nature = NatureOfService.DOMAIN` for services that modify data.

```java
@DomainService(nature = NatureOfService.VIEW)
  public class CustomerService { ... }
```


#### **`@DomainServiceLayout`**
- **Purpose**: Controls the UI layout and appearance of domain services.
- **Best Practices**:
  - Use `named` to provide a user-friendly name for the service in the UI.
  - Use `describedAs` to provide a description for the service.
  - The order of menu items is determined by the `sequence` attribute in `@ActionLayout` or the natural order of methods in the class.
- **Changes**: The menuOrder attribute was removed when the framework transitioned to Spring Boot architecture. Use menubars.layout.xml for complete layout control:
- 
    The recommended way to organize global top-level menu ordering across multiple domain services is using the menubars.layout.xml file located in src/main/resources. This allows you to explicitly define the order of top-level menus, sections, and items:
```xml
<mb3:menuBars xmlns:mb3="https://causeway.apache.org/applib/layout/menubars/bootstrap3">
  <mb3:primary>
    <mb3:menu>
      <mb3:named>Customers</mb3:named>
      <mb3:section>
        <mb3:serviceAction objectType="demo.CustomerMenu" id="findByName"/>
      </mb3:section>
    </mb3:menu>
  </mb3:primary>
</mb3:menuBars>
```

---

### **7. Programmatic Annotations**
These annotations define methods or fields that should not be exposed in the UI.

#### **`@Programmatic`**
- **Purpose**: Marks a method or field as programmatic, excluding it from the UI.
- **Best Practices**:
    - Use this annotation for methods that are only called by other code (e.g., helper methods).
    - Use this annotation for fields that should not be displayed or edited in the UI.

```java
@Programmatic
  public void internalHelperMethod() { ... }
```


---

### **8. Validation Annotations**
These annotations define validation rules for properties and parameters.

#### **`@javax.validation.constraints`**
- **Purpose**: Provides standard validation constraints (e.g., `@NotNull`, `@Size`, `@Pattern`).
- **Best Practices**:
    - Use `@NotNull` for required fields.
    - Use `@Size(min = 3, max = 50)` to enforce length constraints.
    - Use `@Pattern(regexp = "\\d{5}")` to validate input using regex.

```java
@NotNull
  @Size(min = 3, max = 50)
  private String name;
```


---

### **9. Event Annotations**
These annotations enable event publishing for domain objects and actions.

#### **`@DomainEvent`**
- **Purpose**: Marks a method to publish domain events.
- **Best Practices**:
    - Use this annotation for actions that trigger business events.
    - Define a custom event class for complex scenarios.

```java
@DomainEvent(CustomerCreatedEvent.class)
  public Customer createCustomer(String name) { ... }
```


---

### **10. Mixin Annotations**
These annotations define mixins, which add behavior to existing domain objects.

#### **`@Mixin`**
- **Purpose**: Marks a class as a mixin and specifies the target domain object.
- **Best Practices**:
    - Use mixins to extend domain objects without modifying their source code.
    - Use `method = "act"` to specify the action method.

```java
@Mixin(method = "act")
  public class Customer_sendEmail {
      private final Customer customer;
      public Customer_sendEmail(Customer customer) { this.customer = customer; }
      public void act(String subject, String body) { ... }
  }
```


---

## **General Best Practices**
1. **Use `@Named` for Clarity**:
    - Always use `@Named` to provide clear, user-friendly names for domain objects, properties, and actions.

2. **Leverage `@DomainObjectLayout` and `@PropertyLayout`**:
    - Use these annotations to improve the UI experience by organizing fields, adding icons, and providing descriptions.

3. **Prefer `@Action` with `SemanticsOf`**:
    - Explicitly define the semantics of actions (e.g., `SAFE`, `IDEMPOTENT`, `NON_IDEMPOTENT`) to ensure proper behavior.

4. **Use `@Programmatic` for Internal Logic**:
    - Mark methods and fields that should not be exposed in the UI as `@Programmatic`.

5. **Enable Publishing for Audit Trails**:
    - Use `commandPublishing = Publishing.ENABLED` and `executionPublishing = Publishing.ENABLED` for actions and properties that require audit logging.

6. **Validate Inputs**:
    - Use validation annotations (e.g., `@NotNull`, `@Size`, `@Pattern`) to enforce constraints on properties and parameters.

7. **Use Mixins for Extensibility**:
    - Use mixins to add behavior to existing domain objects without modifying their source code.

8. **Organize Collections with `@CollectionLayout`**:
    - Use `defaultView`, `sortedBy`, and `paged` to improve the usability of collections.

9. **Leverage Events for Decoupling**:
    - Use `@DomainEvent` to publish events for actions that trigger business logic.

10. **Test Annotations**:
    - Ensure that annotations are tested in both unit and integration tests to verify their behavior in the UI.

---

## **Summary**
By following these best practices, you can create 
a **clean, maintainable, and user-friendly** domain model in Apache Causeway 3.6.0. 
Annotations play a critical role in defining the behavior, layout, and validation 
of your domain objects, services, and actions. Use them wisely to maximize the potential 
of your application!