---
name: causeway
description: "Apache Causeway 3.6.0 annotations and conventions for DoMoX — metamodel veto trap, domain object patterns, and annotation quick-reference."
disable-model-invocation: true
---

# DoMoX Causeway Conventions — Quick Reference

**Full reference:** `docs/causeway/causeway-annotations.md`

## Metamodel member-type veto (the critical trap)

Causeway introspects every JavaBean accessor on a `@DomainObject` / `@Entity` and treats each
getter as a **property** unless suppressed or `@Programmatic`. If a getter's return type is a
managed bean (injected `@DomainService`, view model, mixin, vetoed type), startup fails with:

> member with vetoed, mixin or managed element-type

**Root cause:** Lombok `@Data` auto-generates `getXxx()` for an `@Inject @DomainService`
collaborator → the service becomes a property → vetoed. Compiles fine, all unit tests pass;
only the metamodel validator catches it.

**Fix — suppress accessors and exclude from equality/toString:**
```java
@Transient
@Inject
@Getter(AccessLevel.NONE)
@Setter(AccessLevel.NONE)
@EqualsAndHashCode.Exclude
@ToString.Exclude
private DomainModelDiagrams domainModelDiagramsService;
```

**Guardrail:** `ValidateDomainModelIntegTest` (domox-webapp) — wired into CI.
See `CODING_STANDARDS.md` §1.

## Key annotation patterns used in this codebase

| Annotation | Where | Purpose |
|---|---|---|
| `@DomainObject` | Entity classes | `entityChangePublishing`, `bounding`, `editing`, `autoCompleteRepository` |
| `@DomainObjectLayout` | Entity classes | `cssClassFa`, `describedAs`, `paged` |
| `@Property` | Fields | `editing`, `commandPublishing`, `executionPublishing` |
| `@PropertyLayout` | Fields | `fieldSetId`, `sequence` |
| `@Action` | Methods | `semanticsOf` (SAFE/IDEMPOTENT/NON_IDEMPOTENT) |
| `@ActionLayout` | Methods | `fieldSetId`, `sequence`, `describedAs` |
| `@CollectionLayout` | Collections | `defaultView`, `sortedBy`, `paged` |
| `@Programmatic` | Methods/fields | Hide from metamodel (internal logic, injected services) |
| `@Mixin` | Mixin classes | Extend domain objects without modifying source |
| `@DomainService` | Service classes | `nature = NatureOfService.VIEW` for UI services |
| `@Named` | Any | User-friendly display names |
| Validation | Params/fields | `@NotNull`, `@Size`, `@Pattern` |
