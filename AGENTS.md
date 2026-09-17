# AGENTS.md — Leori ENIA Platform

## 1. Project Mission

Leori ENIA Platform is a platform for the governance, management,
assessment, traceability, and responsible adoption of Artificial
Intelligence systems.

The platform is initially oriented toward organizations operating under
Peru's National Artificial Intelligence Strategy (ENIA 2026–2030) and
its related regulatory framework.

The product should help organizations move from AI policy and governance
requirements to operational implementation.

Core capabilities are expected to include:

- AI initiative management
- AI governance
- AI risk and impact assessment
- AI model registry
- Dataset registry
- Controls and evidence
- Audit and traceability
- AI action plan management
- AI experimentation/sandbox capabilities
- Integration with domain-specific AI solutions

Leori Labor Intelligence (`leori-hr`) is expected to become one of the
first reference solutions integrated with the platform, but it is not
the core of this repository.

---

## 2. Architecture

The project follows:

- Domain-Driven Design (DDD)
- Clean Architecture
- Hexagonal Architecture principles
- Modular Monolith architecture

Do NOT introduce microservices unless an explicit architectural decision
has been made to do so.

The initial package structure is organized by bounded context / business
capability, not by technical layer at the application root.

Example:

    com.leori.enia
    ├── organization
    ├── initiative
    ├── governance
    ├── risk
    ├── registry
    ├── evidence
    ├── audit
    ├── reporting
    └── shared

Each bounded context may internally contain:

    domain/
    application/
    infrastructure/
    interfaces/

Dependencies must point inward:

    interfaces
        ↓
    application
        ↓
      domain

Infrastructure implements ports defined by the application/domain layers.

The domain must never depend on infrastructure.

---

## 3. Current Development Stage

The project is currently in an early domain-first stage.

The current implemented bounded context is:

    initiative

Existing concepts include:

- AIInitiative
- AIInitiativeId
- InitiativeStatus
- RiskLevel
- DomainEvent
- initiative lifecycle domain events

The current initiative lifecycle includes:

    DRAFT
      ↓
    SUBMITTED
      ↓
    UNDER_ASSESSMENT
      ↓
    RISK_ASSESSED
      ↓
    APPROVED / REJECTED

Future lifecycle states already identified include:

- EXPERIMENTATION
- READY_FOR_DEPLOYMENT
- ACTIVE
- SUSPENDED
- RETIRED

Do not implement future transitions merely because they exist in the enum.
Implement behavior only when required by the current task/use case.

---

## 4. Domain Rules

Domain objects must protect their own invariants.

Avoid anemic domain models.

Prefer behavior such as:

    initiative.submit(...)
    initiative.startAssessment()
    initiative.assessRisk(...)
    initiative.approve(...)
    initiative.reject(...)

instead of:

    initiative.setStatus(...)

Do not expose public setters that allow callers to bypass domain rules.

Invalid state transitions must fail explicitly.

Example:

    DRAFT -> APPROVED

must not be possible if risk assessment is required first.

Domain rules belong in the domain.

Application orchestration belongs in the application layer.

Infrastructure concerns belong in infrastructure.

---

## 5. Aggregate Design

Keep aggregates small and consistent.

Do NOT turn `AIInitiative` into a giant aggregate containing the complete
AI governance lifecycle.

Current/future aggregate roots include:

- Organization
- AIInitiative
- AISystem
- AIModel
- Dataset
- RiskAssessment
- Evidence

References between aggregates should normally use strongly typed IDs.

Example:

    AIInitiativeId
    OrganizationId
    AISystemId
    AIModelId
    DatasetId

Avoid referencing entire external aggregates when an ID is sufficient.

---

## 6. Value Objects

Prefer explicit domain types over primitive obsession.

Prefer:

    AIInitiativeId
    OrganizationId
    RiskLevel

over:

    String initiativeId
    String organizationId
    String risk

Java records are encouraged for immutable Value Objects when appropriate.

Value Objects must validate their own fundamental invariants.

---

## 7. Entity Construction

Core domain entities should use explicit construction patterns that
protect invariants.

The project currently uses a custom Builder for `AIInitiative`.

Preserve this approach unless there is a demonstrated reason to change it.

Do not introduce Lombok builders into core domain entities.

Avoid constructors with large parameter lists.

Validation should normally happen when the entity/value object is created,
not scattered across arbitrary callers.

---

## 8. Time

Do not call:

    Instant.now()
    LocalDate.now()

inside domain entities when the value affects domain behavior,
auditability, or tests.

Time must be provided by the application layer.

Use:

    java.time.Clock

in application services/use cases.

Production may use:

    Clock.systemUTC()

Tests should normally use:

    Clock.fixed(...)

This keeps behavior deterministic and auditable.

---

## 9. Domain Events

Domain events describe meaningful facts that have already happened.

Examples:

- AIInitiativeSubmitted
- AIInitiativeRiskAssessed
- AIInitiativeApproved
- AIInitiativeRejected

Domain events must remain infrastructure-independent.

Do NOT introduce Kafka, RabbitMQ, Spring ApplicationEvent, or another
messaging technology merely to support domain events.

Infrastructure publishing will be introduced later through appropriate
ports/adapters.

Events should use past-tense names.

---

## 10. Application Layer

Application use cases orchestrate domain behavior.

Expected pattern:

    Command
       ↓
    Use Case
       ↓
    Repository Port
       ↓
    Aggregate
       ↓
    Domain Events

Example:

    CreateAIInitiativeCommand
             ↓
    CreateAIInitiativeUseCase
             ↓
    AIInitiativeRepository
             ↓
    AIInitiative

Application services must not contain domain rules that belong to
aggregates or domain services.

Commands represent user/system intent.

Do not allow external callers to provide internal domain state such as:

- lifecycle status
- audit timestamps
- computed risk state

unless the use case explicitly requires it.

---

## 11. Repository Ports

Repository interfaces belong to the inner architecture.

Example:

    AIInitiativeRepository

Infrastructure implementations may later include:

    JpaAIInitiativeRepository
    InMemoryAIInitiativeRepository

The application/domain must not depend on Spring Data interfaces.

Do NOT expose:

    JpaRepository
    CrudRepository

to the domain or application layer.

---

## 12. AI Governance Principles

This project deals with governance of AI systems and must preserve
traceability as a first-class architectural concern.

The architecture should be capable of representing:

- AI initiatives
- AI systems
- models and model versions
- datasets
- risk assessments
- controls
- mitigations
- evidence
- approvals
- responsible actors
- lifecycle changes
- audit history

Do not reduce governance concepts to simple boolean compliance flags when
evidence, lifecycle, responsibility, or traceability is required.

For example, prefer a model capable of expressing:

    Risk
      ↓
    Control
      ↓
    Implementation
      ↓
    Evidence

instead of only:

    compliant = true

---

## 13. AI Risk

Do not assume that HIGH risk automatically means prohibited.

Risk classification, prohibited practices, high-risk obligations,
mitigation, approval, human oversight, and evidence are distinct concepts.

Do not invent legal or regulatory rules.

When implementation depends on interpretation of ENIA 2026–2030,
Peruvian AI regulation, data protection rules, or another regulatory
source, explicitly identify the assumption or request clarification.

Regulatory rules should eventually be modeled so they can evolve without
hardcoding legislation throughout the domain.

---

## 14. AI vs Deterministic Business Rules

Do not use AI where deterministic business rules are more appropriate.

Examples such as payroll calculations, legal thresholds, workflow state
transitions, and validation rules should normally remain deterministic.

AI may assist with:

- explanation
- document analysis
- classification
- anomaly detection
- recommendations
- semantic search
- RAG
- prediction

AI-generated results affecting rights or important decisions must be
designed with traceability, explainability, and human oversight in mind.

---

## 15. Technology Baseline

Current baseline:

- Java 21
- Maven
- JUnit 5
- Spring Boot

Do not change the Java version without explicit instruction.

Do not introduce dependencies merely for convenience.

At the current stage, do NOT add unless explicitly requested:

- Spring Data JPA
- PostgreSQL
- Flyway
- Spring Security
- REST controllers
- Kafka
- RabbitMQ
- Redis
- Lombok
- Docker infrastructure
- Kubernetes
- external AI SDKs

These technologies may be introduced later when required by an explicit
architectural increment.

---

## 16. Testing

Every domain behavior must have tests.

Prefer testing behavior and business invariants over implementation
details.

Tests should cover:

- happy paths
- invalid transitions
- invariant violations
- important domain events
- deterministic timestamps when relevant

Tests must not depend on the current system clock.

Use fixed values when possible.

Before considering a task complete, run:

    mvn clean test

All existing tests must continue to pass.

Do not delete or weaken tests simply to make a build pass.

---

## 17. Coding Style

Prefer:

- small classes
- explicit names
- immutable Value Objects
- final fields where appropriate
- meaningful domain language
- constructor dependency injection
- standard Java before adding libraries
- explicit behavior over clever abstractions

Avoid:

- unnecessary inheritance
- generic "Manager" or "Util" classes
- static mutable state
- public setters on aggregates
- premature abstractions
- framework annotations in the domain
- primitive obsession
- large god classes

Code must optimize for maintainability and domain clarity rather than
minimum line count.

---

## 18. Package Boundaries

Do not move classes between bounded contexts casually.

Before introducing a dependency from one bounded context to another,
consider whether:

- an ID is sufficient
- a port is required
- a domain event can decouple the interaction
- the concept belongs in `shared`

The `shared` package must remain small.

Do not use `shared` as a dumping ground for unrelated utilities.

---

## 19. Refactoring

Before changing existing domain behavior:

1. Inspect the existing implementation.
2. Inspect the existing tests.
3. Understand the invariant being protected.
4. Make the smallest change necessary.
5. Add/update tests.
6. Run the complete test suite.

Do not perform unrelated large refactors during a focused feature task.

If a larger refactor appears necessary, explain why before implementing it.

---

## 20. Git and Task Discipline

Work in small, reviewable increments.

For each task:

1. Inspect the repository.
2. State the intended change.
3. Implement the smallest coherent increment.
4. Add tests.
5. Run `mvn clean test`.
6. Review `git diff`.
7. Report the result.

Do not automatically commit unless explicitly requested.

Do not push unless explicitly requested.

Do not rewrite Git history.

Do not use force push.

Suggested commit prefixes:

    feat:
    fix:
    refactor:
    test:
    docs:
    chore:

---

## 21. Scope Control

Do not implement features merely because they appear in the roadmap.

Build only the current requested increment.

In particular, avoid prematurely implementing:

- persistence
- authentication
- frontend
- distributed messaging
- microservices
- model training
- LLM integrations
- sandbox infrastructure
- dashboards

unless the current task explicitly requires them.

YAGNI applies.

---

## 22. Definition of Done

A coding task is complete when:

- requested behavior is implemented
- domain invariants remain protected
- architecture boundaries are respected
- relevant tests exist
- existing tests remain green
- `mvn clean test` succeeds
- no generated build artifacts are committed
- no unrelated dependencies were introduced
- the resulting diff is focused and reviewable

At completion, report:

- files created
- files modified
- tests added/modified
- test result
- architectural decisions or assumptions
- anything intentionally left for a future increment

---

## 23. Current Next Increment

Unless explicitly changed by the project owner, the next planned
increment is the application layer for assessing the preliminary risk of
an AI initiative:

- `AssessRiskAIInitiativeCommand`
- `AssessRiskAIInitiativeUseCase`
- load the initiative through `AIInitiativeRepository`
- call the existing aggregate preliminary risk assessment behavior
- save and return the risk-assessed initiative
- injected `java.time.Clock`
- unit test with `Clock.fixed(...)`
- in-memory/fake repository for the test

Constraints for this increment:

- no JPA
- no REST
- no Spring annotations
- no database
- no Lombok
- no messaging infrastructure
- no changes to existing domain behavior unless required by a
  demonstrated issue

After implementation:

    mvn clean test

Do not commit automatically.
