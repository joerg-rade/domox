# DoMoX Coding Standards

Read during code review — and before writing entity/domain code. These are the
cross-cutting judgement calls no linter can substitute for; wherever a rule is
mechanically checkable the automated guardrail is named.

## 1. Never put class-level Lombok `@Data` on a Causeway entity

**Rule:** do not use Lombok `@Data` on `@DomainObject` / JPA `@Entity` classes.
Every other entity in this codebase uses **field-level** `@Getter` / `@Setter` (plus
`@NoArgsConstructor` where needed) — match that style.

**Why (the 2026-10 `DomainModel` veto):** `@Data` auto-generates an accessor for EVERY field,
including injected collaborators. `DomainModel` field-injected a `@DomainService`
(`DomainModelDiagrams`), so `@Data` synthesized `getDomainModelDiagramsService()` for it.
Causeway introspected that accessor as an entity **property** whose element type is a managed
`@DomainService` bean → the metamodel veto *"member with vetoed, mixin or managed
element-type"* killed context startup. Nothing fails to compile and no unit test catches it;
only the metamodel validator does.

**The pattern — injected collaborator fields on entities:**
```java
@Transient
@Inject
@Getter(AccessLevel.NONE)
@Setter(AccessLevel.NONE)
@EqualsAndHashCode.Exclude
@ToString.Exclude
private DomainModelDiagrams domainModelDiagramsService;
```
(If the field is not Lombok-generated at all — plain `@Inject` + hand-written getter — mark
the accessor `@Programmatic` instead. Real persisted/gettable properties keep normal accessors.)

**Guardrail:** `ValidateDomainModelIntegTest` (domox-webapp) runs the `DomainModelValidator`
over the whole metamodel; it is wired into CI (`.github/workflows/ci.yml`,
`integration-test` job). After touching entities, run it locally from the repo root:
`mvn -B test -pl domox-webapp -Dtest=ValidateDomainModelIntegTest -am -Dsurefire.failIfNoSpecifiedTests=false`.

## 2. Injected services are collaborators, not properties

Any `@Inject`ed field (service, repository, `TransactionService`, …) on a domain object must
never be exposed as a property or action. Suppress Lombok accessors or mark the accessor
`@Programmatic`. Prefer constructor injection on services; entities may field-inject but MUST
hide the field from the metamodel.

## 3. Review checklist

- [ ] No class-level `@Data` on entities / view models.
- [ ] Injected fields on domain objects have suppressed accessors or `@Programmatic`.
- [ ] After touching entity members, `ValidateDomainModelIntegTest` passes.
