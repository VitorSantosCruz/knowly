---
name: spring-endpoint-scaffold
description: Use when adding a new REST endpoint, service method, or DTO to knowly's backend. Triggers on "cria um endpoint", "adiciona um método no service", "preciso de uma nova API".
---

# spring-endpoint-scaffold

Concrete controller/service/DTO/exception template for **knowly**
(Spring Boot), matching `TenantController`/`TenantService`/`StaffController`/
`StaffService`'s established shape exactly.

## Rules & anti-patterns

- **DO** put permission checks on the annotation, not inline:
  `@RequiresPermission(Permission.X)` for tenant-scoped,
  `@RequiresGlobalPermission(GlobalPermission.X)` for global/staff-scoped.
- **DO** put `@AuditLog(action = "resource.action", resourceType = "X")`
  on every state-changing method and every permission-sensitive read.
- **STRICTLY PROHIBITED**: a bare `throw new RuntimeException(...)` or
  an inline `ResponseEntity.status(...)` for a named failure mode — add
  a dedicated exception class and a handler entry in
  `TenancyExceptionHandler` (or the equivalent for a different domain).
- **DO** keep controllers thin — permission/business logic lives in the
  service, the controller only maps DTOs and delegates.
- **DO** make DTOs Java records with Jakarta Validation annotations
  (`@NotBlank`, `@Email`, `@NotNull`) — never a plain class with manual
  null checks.
- **STRICTLY PROHIBITED**: a manual `WHERE tenant_id = ?` — tenant
  scoping is the Hibernate `@Filter`'s job, always.

## Execution steps

1. Confirm the PLAN.md's method signature/endpoint contract.
2. Write the integration test first (TDAD Red) — `@SpringBootTest` +
   `@Import(TestcontainersConfiguration.class)` + `@ActiveProfiles("test")`,
   asserting the exact status code and permission behavior (granted,
   ungranted, wrong-permission-doesn't-imply-this-one).
3. Add the exception (if new) + handler entry.
4. Add the DTO(s).
5. Add the service method with the correct annotation stack.
6. Add the controller method, wire the DTO.
7. `./mvnw test -Dtest=<Class>` (Green), then
   `./mvnw spotless:apply && ./mvnw verify` — check real exit codes.

## Templates

Exception + handler entry:

```java
public class ResourceAlreadyExistsException extends RuntimeException {}
```
```java
@ExceptionHandler(ResourceAlreadyExistsException.class)
public ResponseEntity<TenancyErrorResponseDto> handleResourceAlreadyExists(
        ResourceAlreadyExistsException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new TenancyErrorResponseDto("RESOURCE_ALREADY_EXISTS"));
}
```

Service method:

```java
@Transactional
@RequiresGlobalPermission(GlobalPermission.SOME_PERMISSION)
@AuditLog(action = "resource.create", resourceType = "Resource")
public Resource createResource(String input) {
    if (repository.findByX(input).isPresent()) {
        throw new ResourceAlreadyExistsException();
    }
    return repository.save(new Resource(input));
}
```

Controller method:

```java
@PostMapping("/resources")
public ResponseEntity<ResourceDto> create(@Valid @RequestBody CreateResourceRequestDto request) {
    Resource created = service.createResource(request.input());
    return ResponseEntity.status(HttpStatus.CREATED).body(ResourceDto.from(created));
}
```
