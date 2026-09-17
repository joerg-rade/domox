# TypedDependency Rules - Before/After Examples

This document provides practical examples of how each rule transforms dependency parse trees into domain model extractions.

---

## Legend

- **Dependency**: Stanford CoreNLP dependency format `type(governor, dependent)`
- **Governor (A)**: First token in dependency (often the verb)
- **Dependent (B)**: Second token in dependency (often the target noun)
- **POS Tags**: VB=verb, NN=noun, JJ=adjective, CD=cardinal number, DT=determiner
- **Output**: What gets extracted by the rule

---

## GROUP 1: ENTITY & ATTRIBUTE EXTRACTION (TDR1-TDR13)

### TDR1: Subject Entity Extraction
**Rule**: `nsubj/nsubjpass(VB, NN≠BasicAttrib)`

**Example 1**:
```
Input:  nsubj(creates, user)
        A=creates (VB), B=user (NN)
        B is NOT in {name, number, type, address, level, date, time}
        previousTd ≠ compound

Output: Entity.add(user)
        >> Domain has entity class: User
```

**Example 2**:
```
Input:  nsubj(designed, system)
        A=designed (VBN), B=system (NN)
        
Output: Entity.add(system)
        >> Domain has entity class: System
```

### TDR2: Attribute Extraction
**Rule**: `nsubj/nsubjpass(VB, NN=BasicAttrib)`

**Example 1**:
```
Input:  nsubj(stored, name)
        A=stored (VB), B=name (NN)
        B IS in basic attributes list

Output: Attributes.add(name)
        >> Entities store basic attribute: name
```

**Example 2**:
```
Input:  nsubjpass(entered, date)
        A=entered (VBN), B=date (NN)

Output: Attributes.add(date)
        >> User can enter a date
```

### TDR3: Object Entity Extraction
**Rule**: `dobj/iobj/pobj(VB, NN≠BasicAttrib)` + context checks

**Example 1**:
```
Input:  dobj(creates, document)
        A=creates (VB), B=document (NN)
        B ≠ basic attribute
        previousTd ≠ amod, previousTd ≠ advmod
        VB ≠ {entered, inputted, saved, added, has}

Output: Entity.add(document)
        >> Action: create document
```

**Example 2**:
```
Input:  obj(process, request)
        A=process (VB), B=request (NN)

Output: Entity.add(request)
        >> System processes requests
```

### TDR4: Object Attribute Extraction
**Rule**: `dobj/iobj/pobj(VB, NN)` where NN=BasicAttrib OR VB=BlockedVerb

**Example 1**:
```
Input:  dobj(entered, address)
        A=entered (VB), B=address (NN)
        B=address (basic attribute)

Output: Attributes.add(address)
        >> User enters address
```

**Example 2**:
```
Input:  obj(saved, number)
        A=saved (VB), B=number (NN)
        VB=saved (blocked verb - indicates data management)

Output: Attributes.add(number)
        >> System saves number (storage operation, not business logic)
```

### TDR5: Adjective-Modified Attribute
**Rule**: `dobj/iobj/pobj(VB, NN)` + `previousTd=amod(?, JJ)`

**Example 1**:
```
Input:  Current: dobj(enter, information)
        Previous: amod(information, personal)
        A(curr)=enter (VB), B(curr)=information (NN)
        B(prev)=personal (JJ)

Output: Attributes.add(amod(information) + amod(personal))
        >> User enters personal information
```

**Example 2**:
```
Input:  Current: obj(display, details)
        Previous: advmod(details, relevant)
        B(prev)=relevant (JJ)

Output: Attributes.add(amod(details) + amod(relevant))
        >> System displays relevant details
```

### TDR6: Possessive Relationships (nmod:of)
**Rule**: `nmod:of(NN, NN)` with 3-way branching

**Example 1**:
```
Input:  nmod:of(owner, document)
        A=owner (NN=BasicAttrib), B=document (NN≠BasicAttrib)

Output: Entity.add(document)
        Attributes.add(owner)
        >> Document has owner attribute
```

**Example 2**:
```
Input:  nmod:of(organization, department)
        A=organization (NN≠BasicAttrib), B=department (NN≠BasicAttrib)

Output: Entity.add(organization)
        Entity.add(department)
        >> Two entities with relationship
```

**Example 3**:
```
Input:  nmod:of(timestamp, creation)
        A=timestamp (BasicAttrib), B=creation (BasicAttrib)

Output: Attributes.add(timestamp of creation)
        >> Composite attribute
```

### TDR7: Noun-in-Noun Relationship
**Rule**: `nmod:in(NN, NN)`

**Example 1**:
```
Input:  nmod:in(department, organization)
        A=department (NN), B=organization (NN)

Output: Entity.add(organization)
        Attributes.add(department)
        >> Department exists within Organization
```

### TDR8: Prepositional Relationships
**Rule**: `nmod:to/for/from/as(?, NN)`

**Example 1**:
```
Input:  nmod:to(send, recipient)
        B=recipient (NN)

Output: Entity.add(recipient)
        >> Message sent to recipient (recipient is entity)
```

**Example 2**:
```
Input:  nmod:for(process, customer)
        B=customer (NN)

Output: Entity.add(customer)
        >> Process executed for customer
```

### TDR9: Agent Relationships
**Rule**: `nmod:by/agent/with(?, NN)` with basic-attrib branching

**Example 1**:
```
Input:  nmod:by(processed, manager)
        B=manager (NN≠BasicAttrib)

Output: Entity.add(manager)
        >> Processing done by manager (entity)
```

**Example 2**:
```
Input:  nmod:with(filled, name)
        B=name (NN=BasicAttrib)

Output: Attributes.add(name)
        >> Form filled with name (attribute)
```

### TDR10: Possessive Relationship
**Rule**: `nmod:poss(NN, NN or PREP or PRP$)` with branching

**Example 1**:
```
Input:  nmod:poss(author, article)
        A=author (NN), B=article (NN)

Output: Entity.add(article)
        Attributes.add(author)
        >> Article has author
```

**Example 2**:
```
Input:  nmod:poss(owner, property)
        A=owner (NN), B=property (NN≠BasicAttrib)

Output: Attributes.add(property)
        >> Owner possesses property
```

**Example 3 (PRP$ possessor)**:
```
Input:  nmod:poss(pets, their)  in "pet owners train their pets"
        A=pets (NNS), B=their (PRP$)
        Sentence also has nsubj(train, owners)

Output: Attributes.add(pets)
        >> PropertyCdd "pets" on resolved possessor ClassCdd "Owners"
```

**Example 4 (PRP$ + amod enrichment)**:
```
Input:  nmod:poss(pets, their)  in "pet owners train their beloved pets"
        A=pets (NNS), B=their (PRP$)
        Sentence also has amod(pets, beloved)

Output: Attributes.add(beloved pets)
        >> PropertyCdd "beloved pets" on ClassCdd "Owners"
```

### TDR11: Adjective Modifier
**Rule**: `amod(NN, JJ or VBG)` with basic-attrib branching and possession cross-reference

**Example 1**:
```
Input:  amod(users, multiple)
        A=users (NN=BasicAttrib), B=multiple (JJ)

Output: Attributes.add(multiple users)
        >> Multiple users (cardinality info)
```

**Example 2**:
```
Input:  amod(document, encrypted)
        A=document (NN≠BasicAttrib), B=encrypted (JJ)

Output: Entity.add(document)
        >> Document is entity, can be encrypted
```

**Example 3 (VBG modifier + possession)**:
```
Input:  amod(pets, beloved)  in "owners love their beloved pets"
        A=pets (NNS≠BasicAttrib), B=beloved (VBG)
        Sentence also has nmod:poss(pets, their) and nsubj(love, owners)

Output: Attributes.add(beloved pets)
        >> PropertyCdd "beloved pets" on ClassCdd "Owners" (not a standalone entity)
```

### TDR12: Compound Words
**Rule**: `compound(NN, NN)` with !nsubj & !dobj checks

**Example 1**:
```
Input:  compound(title, page)
        A=title (BasicAttrib), B=page (NN≠BasicAttrib)
        nextTd ≠ nsubj, nextTd ≠ dobj

Output: Attributes.add(page title)
        Entity.add(page)
        >> Page has title attribute
```

**Example 2**:
```
Input:  compound(report, document)
        A=report (NN≠BasicAttrib), B=document (NN≠BasicAttrib)

Output: Entity.add(document report)
        >> Compound entity name or type
```

### TDR13: Conjunction Relationships
**Rule**: `nmod:and/or(NN, NN)` with branching

**Example 1**:
```
Input:  nmod:and(name, email)
        A=name (BasicAttrib), B=email (BasicAttrib)

Output: Attributes.add(name)
        Attributes.add(email)
        >> Multiple attributes separated by "and"
```

**Example 2**:
```
Input:  nmod:or(user, administrator)
        A=user (NN≠BasicAttrib), B=administrator (NN≠BasicAttrib)

Output: Entity.add(user)
        Entity.add(administrator)
        >> Multiple entity types ("user or administrator")
```

---

## GROUP 2: RELATIONSHIP EXTRACTION (TDR14-TDR23)

### TDR14: Subject-Object Relationship
**Rule**: `nsubj(V, E1)` combined with `dobj(V, E2)`

**Example 1**:
```
Input:  nsubj(creates, user) 
        dobj(creates, document)

Output: relationship.add(user (creates) document)
        >> User creates Document
```

**Example 2**:
```
Input:  nsubj(manages, administrator)
        dobj(manages, permission)

Output: relationship.add(administrator (manages) permission)
        >> Administrator manages Permission
```

### TDR15: Passive Relationship with Agent
**Rule**: `nsubjpass(VBN, E1)` + `nmod:by/agent(VBN, E2)`

**Example 1**:
```
Input:  nsubjpass(processed, request)
        nmod:by(processed, system)

Output: relationship.add(request (processed) system)
        >> Request is processed by System
```

### TDR16: Possessive "Has" Relationship
**Rule**: `nmod:of(E1, E2)`

**Example 1**:
```
Input:  nmod:of(attributes, entity)

Output: relationship.add(entity (has) attributes)
        >> Entity has Attributes
```

### TDR17: Composite 3-Entity Relationship
**Rule**: `nsubj(V, E1)` + `dobj(V, E2)` + `nmod:of(E2, E3)`

**Example 1**:
```
Input:  nsubj(creates, user)
        dobj(creates, instance)
        nmod:of(instance, record)

Output: relationship.add(user (creates) instance)
        relationship.add(instance (has) record)
        >> User creates Instance which has Record
```

### TDR18: Directional Multi-Relationship
**Rule**: `nsubj/dobj/nmod:to` combination

**Example 1**:
```
Input:  nsubj(assign, administrator)
        dobj(assign, role)
        nmod:to(assign, user)

Output: relationship.add(administrator (assign) role)
        relationship.add(role (assign to) user)
        relationship.add(administrator (assign to) user)
        >> Administrator assigns role to user
```

### TDR19: Passive Directional
**Rule**: `nsubjpass(V, E1)` + `nmod:to(V, E2)`

**Example 1**:
```
Input:  nsubjpass(sent, notification)
        nmod:to(sent, user)

Output: relationship.add(notification (sent to) user)
        >> Notification is sent to User
```

### TDR20: Complex Multi-Subject Pattern
**Rule**: `nsubj/nsubjpass/nmod:to` combination

Multiple relationships generated from complex subject+passive+directional pattern.

### TDR21: Spatial Relationship
**Rule**: `nsubj(V, E1)` + `nmod:in(V, E2)`

**Example 1**:
```
Input:  nsubj(manage, administrator)
        nmod:in(manage, organization)

Output: relationship.add(administrator (manage in) organization)
        >> Administrator manages within Organization
```

### TDR22: Purpose Relationship
**Rule**: `nsubj(V, E1)` + `nmod:for(V, E2)`

**Example 1**:
```
Input:  nsubj(created, system)
        nmod:for(created, company)

Output: relationship.add(system (created for) company)
        >> System created for Company
```

### TDR23: Role Relationship
**Rule**: `nmod:as(V, E1)` + `dobj(V, E2)`

**Example 1**:
```
Input:  nmod:as(assign, admin)
        dobj(assign, role)

Output: relationship.add(admin (assign) role)
        >> Assign admin role
```

---

## GROUP 3: DESCRIPTOR & MULTIPLICITY RULES (TDR24-TDR26)

### TDR24: Adjective Descriptor
**Rule**: `amod(E, JJ)`

**Example 1**:
```
Input:  amod(service, veterinary)

Output: descriptor.add(service, veterinary)
        >> Service is categorized as veterinary
```

**Example 2**:
```
Input:  amod(store, online)

Output: descriptor.add(store, online)
        >> Store is an online store
```

**Example 3**:
```
Input:  amod(users, multiple)

Output: descriptor.add(users, multiple)
        >> Users are multiple (qualitative descriptor, not a numeric multiplicity)
```

### TDR25: Numeric Multiplicity
**Rule**: `nummod(E, CD)`

**Example 1**:
```
Input:  nummod(roles, 5)

Output: multiplicity.add(roles, 5)
        >> Roles limited to 5
```

### TDR26: Determiner Multiplicity
**Rule**: `det(E, DT)` with quantifier matching

**Example 1**:
```
Input:  det(pages, each)
        DT="each" (plural quantifier)

Output: multiplicity.add(pages, N)
        >> Each page (unbounded plural)
```

**Example 2**:
```
Input:  det(document, an)
        DT="an" (singular)

Output: multiplicity.add(document, 1)
        >> Exactly one document
```

---

## GROUP 4: OPERATION/ACTION RULES (TDR27-TDR37)

### TDR27: Input Operations
**Rule**: `nsubj/dobj/nmod:to/mark(InputVerb, ?)`

**Example 1**:
```
Input:  nsubj(enter, user)
        dobj(enter, email)
        Email = BasicAttrib

Output: Input_Data.add(email)
        >> System expects user to enter email
```

**Example 2**:
```
Input:  nsubj(fill, user)
        dobj(fill, form)

Output: Input_Data.add(form)
        >> Form is input data
```

### TDR28: Output Operations
**Rule**: `nsubj/dobj/nmod:to/mark(OutputVerb, ?)`

**Example 1**:
```
Input:  nsubj(display, system)
        dobj(display, results)
        Results = BasicAttrib

Output: Output_Data.add(results)
        >> System outputs results
```

**Example 2**:
```
Input:  nsubj(show, application)
        dobj(show, status)

Output: Output_Data.add(status)
        >> Status is display output
```

### TDR27-TDR31: Data Flow Examples

```
Requirements:
"User enters personal details and the system saves the information"

Parsed Dependencies:
  nsubj(enters, user)
  dobj(enters, details) [detail=BasicAttrib]
  nsubj(saves, system)
  dobj(saves, information)

Rules Applied:
  TDR27: enters(personal details) → Input_Data.add(details)
  TDR28: saves(information) → Output_Data.add(information)

Extracted:
  Input_Data: {details}
  Output_Data: {information}
  Operation: User → System (data transformation)
```

### TDR32: Actor Classification
**Rule**: `nsubj/nmod:by(V, Actor)` with verb matching

**Example 1**:
```
Input:  nsubj(click, user)
        Verb="click" (user action)

Output: User_Action.add(click)
        >> Click is user action
```

**Example 2**:
```
Input:  nsubj(delete, system)
        Verb="delete" (system action)

Output: System_Actions.add(delete)
        >> Delete is system action
```

### TDR34: Exception Handling
**Rule**: `xcomp/amod/neg(?, ErrorTerm)`

**Example 1**:
```
Input:  amod(validation, invalid)
        ErrorTerm="invalid"

Output: Exceptions.add(invalid validation)
        >> System may encounter invalid validation exception
```

**Example 2**:
```
Input:  amod(response, error)
        ErrorTerm="error"

Output: Exceptions.add(error response)
        >> Error response is possible exception
```

### TDR35: Conditional Logic
**Rule**: `advcl:if/mark(V, if)`

**Example 1**:
```
Input:  advcl:if(process, condition)
        mark(process, if)

Output: System_Actions.add("if condition process ...")
        >> Conditional system action
```

### TDR36: Validation Operations
**Rule**: `nsubj(validate, ?)`

**Example 1**:
```
Input:  nsubj(validate, system)
        dobj(validate, input)

Output: System_Actions.add(system validate)
        >> System performs validation
```

### TDR37: Control Flow
**Rule**: `nsubj/xcomp(ControlVerb, ?)`

**Example 1**:
```
Input:  nsubj(repeat, process)
        nummod(repeat, 5)

Output: System_Action.add(repeat 5)
        >> Repeat process 5 times (loop control)
```

---

## GROUP 6: GENERALIZATION EXTRACTION (TDR38-TDR40)
Extract generalization (is-a) relationships between domain entities.

### TDR38: Copula-Based Generalization ("X is a Y")
**Rule**: `nsubj(ParentHead, ChildNoun)` + `cop(ParentHead, is/are)` + `det(ParentHead, a/an)`

**Example 1: Simple copula pattern**
```
Input:  nsubj(animal, dog)
        cop(animal, is)
        det(animal, a)

Output: generalization(dog --|> animal)
        >> Domain has generalization: Dog --|> Animal
        >> (creates AssociationCdd with AssociationType.GENERALIZATION)
```

**Example 2: Copula with other sentence elements**
```
Input:  nsubj(mammal, cat)
        cop(mammal, is)
        det(mammal, a)

Output: generalization(cat --|> mammal)
        >> Domain has generalization: Cat --|> Mammal
```

**Example 3: Plural / "are"**
```
Input:  nsubj(mammals, whales)
        cop(mammals, are)
        det(mammals, Ø)  # no det needed for plural

Output: generalization(whales --|> mammals)
        >> Domain has generalization: Whales --|> Mammals
```

**Non-example** (kind/type/sort — handled by TDR39):
```
Input:  nsubj(type, service)
        cop(type, is)
        det(type, a)
        nmod:of(type, service)

Output: No match — governor "type" is excluded from TDR38
        (reserved for TDR39)
```

### TDR39: Kind-of/Type-of Generalization
**Rule**: `nmod:of(TypeKindNoun, ParentNoun)` where TypeKindNoun contains "kind"/"type"/"sort"

**Example 1: "is a type of"**
```
Input:  nsubj(type, service)
        cop(type, is)
        det(type, a)
        nmod:of(type, service)

Output: generalization(service --|> service)
        >> Both entities have the same name — requires compound resolution
        >> Ideal: A premium service is a type of service —>
        >>  nsubj(type, service) — subject "premium service"
        >>  nmod:of(type, service) — complement "service"
        >>  Pending: compound resolution → PremiumService --|> Service
```

**Example 2: "is a kind of"**
```
Input:  nsubj(kind, suv)
        cop(kind, is)
        det(kind, a)
        nmod:of(kind, car)

Output: generalization(suv --|> car)
        >> Domain has generalization: Suv --|> Car
```

**Example 3: "are types of"**
```
Input:  nsubj(types, sedan)
        cop(types, are)
        nmod:of(types, vehicle)

Output: generalization(sedan --|> vehicle)
        >> Domain has generalization: Sedan --|> Vehicle
```

### TDR40: Adjectival Classifier Generalization
**Rule**: `amod(Noun, Adjective)` where adjective is a classifier (not evaluative/generic)

**Example 1: "linked device"**
```
Input:  amod(device, linked)
        A=device (NN), B=linked (JJ/VBG)
        B is not in stop-adjectives

Output: generalization(LinkedDevice --|> Device)
        >> Domain has generalization: LinkedDevice --|> Device
```

**Example 2: "premium account"**
```
Input:  amod(account, premium)
        A=account (NN), B=premium (JJ)

Output: generalization(PremiumAccount --|> Account)
        >> Domain has generalization: PremiumAccount --|> Account
```

**Non-example** (evaluative adjective "valid" — in stop list):
```
Input:  amod(input, valid)
        A=input (NN), B=valid (JJ)
        B is in stop-adjectives

Output: No match — "valid" is evaluative/generic, handled by TDR11
```

**Non-example** (noun involved in copula relation — handled by TDR38/39):
```
Input:  amod(tree, large)
        cop(tree, is) in same sentence

Output: No match — noun "tree" is governed by cop, handled by TDR38
```

---

**Sample Requirement**:
```
"A user creates a new project with a name and description. 
The system validates the input and stores the project.
If validation fails, an error message is displayed."
```

**Dependency Parse Tree (simplified)**:
```
nsubj(creates, user)
dobj(creates, project)
nmod:with(creates, name)
nmod:with(creates, description)
nsubj(validates, system)
dobj(validates, input)
nsubjpass(stored, project)
nmod:by(stored, system)
advcl:if(displayed, fails)
dobj(displayed, message)
amod(message, error)
```

**Rules Applied**:
```
TDR1:  nsubj(creates, user) → Entity.add(user)
TDR3:  dobj(creates, project) → Entity.add(project)
TDR8:  nmod:with(creates, name) → Entity.add(name)
TDR8:  nmod:with(creates, description) → Entity.add(description)
TDR14: nsubj(creates, user)+dobj(creates, project) → relationship.add(user (creates) project)
TDR32: nsubj(validates, system) → System_Actions.add(validate)
TDR27: dobj(validates, input) → Input_Data.add(input)
TDR15: nsubjpass(stored, project)+nmod:by(stored, system) → relationship.add(project (stored by) system)
TDR35: advcl:if(displayed, fails) → System_Actions.add("if validation fails")
TDR34: amod(message, error) → Exceptions.add(error message)
TDR28: dobj(displayed, message) → Output_Data.add(message)
```

**Extracted Domain Model**:
```
Entities:
  - User
  - Project
  - Name
  - Description
  - Message

Attributes:
  - name, description (of Project)
  - content (of Message, inferred error message)

Relationships:
  - User creates Project
  - System validates Input
  - System stores Project
  - System displays Message (on error)

Data Flows:
  Input: name, description
  Output: message

Operations:
  - User Action: create
  - System Actions: validate, store, display
  - Control Flow: if validation fails

Exceptions:
  - error message

Cardinality:
  - Project: 1 or many
  - Name/Description: required
```

---

## Usage Notes

1. **Rule Ordering**: Rules execute in sequence 1-37; earlier rules (entity extraction) should fire before later rules (relationship extraction)

2. **Context Dependency**: Some rules require previousTd or nextTd context; these should only fire within a dependency stream

3. **Multiple Outputs**: Single dependency can trigger output from multiple branching paths

4. **Composability**: Extract outputs are designed to be assembled into complete domain models

---

**End of Examples**

