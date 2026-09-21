# Rules That Indicate Attribute Relationships Between ClassCandidates

In the DomoX TDR rule system, attribute relationships between ClassCandidates (entities → `ClassCdd`, attributes →
`PropertyCdd`) are established by **Group 1** rules (TDR1–TDR13) from the Entity & Attribute Extraction category. These
rules determine whether a noun becomes a **ClassCdd (entity)** or a **PropertyCdd (attribute of a class)**, and when a
`PropertyCdd` is created, it is linked to its owning class via `relatedCandidateType=ClassCdd` /
`relatedCandidateName=className`.

## Rules That Produce Attribute Relationships (PropertyCdd linked to ClassCdd)

| Rule      | Dependency Pattern                                    | When It Produces an Attribute Relationship                                                                                                                                                                                                                              |
|-----------|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **TDR2**  | `nsubj/nsubjpass(VB, NN)`                             | B is a **Basic Attribute** (e.g. `name`, `number`, `type`) → creates `PropertyCdd` (`A+B`) with `relatedCandidateType=ClassCdd` (`A`). Example: `nsubj(stored, name)` → "name" is an attribute of the entity governing "stored".                                        |
| **TDR4**  | `dobj/iobj/pobj(VB, NN)`                              | B is a **Basic Attribute** OR the verb is a **blocked verb** (entered/inputted/saved/added/has) → creates `PropertyCdd` linked to the owning entity. Example: `dobj(entered, address)` → "address" is an attribute.                                                     |
| **TDR5**  | `dobj/iobj/pobj(VB, NN)` + previous=`amod/advmod(JJ)` | Creates a `PropertyCdd` from the adjective+noun combination. Example: `amod(information, personal)` + `dobj(enter, information)` → "personal information" is an attribute.                                                                                              |
| **TDR6**  | `nmod:of(A,B)` where A,B=NN                           | **Three branches** produce attributes depending on Basic_Attrib status: (1) A=BasicAttrib, B≠BasicAttrib → A is `PropertyCdd` of entity B; (2) Both BasicAttrib → combined `PropertyCdd` "A of B". Example: "name of the customer" → "name" is attribute of "Customer". |
| **TDR7**  | `nmod:in(A,B)` where A,B=NN                           | **Always** treats A as an attribute of entity B → `PropertyCdd`(A) linked to `ClassCdd`(B). Example: "status in the system" → "status" is attribute of "System".                                                                                                        |
| **TDR9**  | `nmod:by/agent/with(A,B)` where B=NN                  | When B is a **Basic Attribute** → creates `PropertyCdd`(B) linked to entity A. Example: "processed by the name" → "name" is an attribute.                                                                                                                               |
| **TDR10** | `nmod:poss(A,B)` where A=NN                           | **Two branches** produce attributes: (1) B=NN → A is `PropertyCdd` of entity B ("customer's name" → "name" is attribute of "Customer"); (2) B=PRP$ → A is `PropertyCdd` of the resolved possessor entity.                                                               |
| **TDR11** | `amod(A,B)` where A=NN, B=JJ/VBG                      | Two attribute branches: (1) A is **Basic Attribute** → combined "B A" is `PropertyCdd` of entity A; (2) A is **possessed** → "B A" is `PropertyCdd` of the possessor entity. Example: "full name" → "full name" is attribute.                                           |
| **TDR12** | `compound(A,B)` where A,B=NN                          | **Three branches** produce attributes: (1) A=BasicAttrib, B≠BasicAttrib → "B A" is `PropertyCdd` of entity B; (2) B=BasicAttrib, A≠BasicAttrib → "A B" is `PropertyCdd` of entity A; (3) Both BasicAttrib → combined "B A" is `PropertyCdd`.                            |
| **TDR13** | `nmod:and/or(A,B)` where A,B=NN                       | When **both** are Basic Attributes → both A and B are created as `PropertyCdd`. Example: "name and address" → both are attributes.                                                                                                                                      |

## Summary

**10 rules** (TDR2, TDR4, TDR5, TDR6, TDR7, TDR9, TDR10, TDR11, TDR12, TDR13) are responsible for establishing attribute
relationships between ClassCandidates. They all share a common pattern: the **Basic_Attribute vocabulary** (`name`,
`number`, `type`, `address`, `level`, `date`, `time` — defined in `BasicAttributeCatalog.java`) is the key discriminator
that determines whether a noun becomes an entity or an attribute of its owning entity.

The rules that most directly create attribute-to-class relationships (i.e., `PropertyCdd` with a non-null
`relatedCandidateName` pointing to a `ClassCdd`) are:

1. **TDR2** — subject basic attributes
2. **TDR4** — object basic attributes
3. **TDR6** — `nmod:of` possessive relationships (attribute-of)
4. **TDR7** — `nmod:in` relationships (attribute-as-location-modifier)
5. **TDR10** — possessive pronoun relationships
6. **TDR12** — compound word attributes