---
name: new-endpoint
description: Use when adding, splitting or relocating a REST endpoint in this Spring Boot project — a new controller method, a new bounded area (entity, repository, service, mail template), or a refactor that splits one endpoint into two. Covers the resource attribute elicitation, the controller boilerplate block, the two authorization shapes, and the exception-to-status table. Triggers on "add an endpoint", "new route", "nuevo endpoint", "expose GET/POST/PUT/DELETE", "new resource", "split the endpoint".
---

# Adding an endpoint to BaseApplicationSpringBoot

Every rule below was extracted from the current code. When the code and this
document disagree, the code wins — fix this document instead.

## 1. Pick the case type

Do this before writing anything. The three cases have different touch sets.

| Case | Applies when | Extra work |
|------|--------------|-----------|
| **A — New method** | The resource already exists and you are adding a route to it | 6 files |
| **B — New bounded area** | A new resource needs an entity, repository, service and routes | Everything in A, plus 7 more |
| **C — Split** | One existing endpoint does two jobs and must become two | 8 ordered steps, section 10 |

Adding a second route to an existing resource is Case A. Adding a resource that
does not exist yet is Case B. Refactoring a route that already ships is Case C.

## 2. Elicitation

Never invent an attribute set. For **Case B** ask these four questions in a
single `question` call — one interruption, not four:

1. **Which attributes does the entity have?** Offer concrete presets first (for
   example `name` + `address` + `active`), then free text, then "decide for me".
2. **Natural key / uniqueness?** `existsByName` · a unique code like the user's
   `email` · no uniqueness.
3. **Does it relate to an existing entity?** Isolated · the other entity points
   at this one. **This answer decides the blast radius** — see section 9.
4. **Soft or hard delete?** Soft, with an `active` flag, as `UserEntity` does ·
   hard, with no flag, as `RoleEntity` does. Both precedents exist in this
   project, so this is never a default.

For **Case A** the elicitation is a single question: which fields does the
request DTO carry?

**Skip the elicitation entirely when the arguments already answer it.** If the
user passed a spec, do not ask what the spec says.

### These are already decided — do not ask

| Default | Evidence |
|---------|----------|
| `@CreationTimestamp createAt` / `@UpdateTimestamp updateAt` | both existing entities |
| Column naming `{field}_{table}` | `first_name_user`, `id_role`, `name_role` |
| Lengths 60 (names) / 80 (natural key) | `UserEntity` |
| `@Id @GeneratedValue(IDENTITY)` + `@Setter(AccessLevel.NONE)` on the id | both entities |
| `equals`/`hashCode` based on id | `RoleEntity` |
| Password policy `@Pattern("^[\\x20-\\x7E]+$") @Size(min = 12, max = 72)` | `UpdateUserRequestDTO` |
| Seed rules: `@Profile("dev")` + `CommandLineRunner` + `if (count() == 0)` | `UsersSeeder` |
| `I` prefix on repositories and services, none on converters | `IUserRepository`, `IUserService`, `UserConverter` |

### Attribute → annotation

| Answer | Generated code |
|--------|----------------|
| Required text | `@NotBlank @Column(name = "{field}_{table}", nullable = false, length = N)` |
| Optional text | `@Column(name = "{field}_{table}", length = N)` |
| Unique | `unique = true` on the `@Column` |
| Required reference | `@NotNull @ManyToOne(...)` + `@JoinColumn` |
| Soft delete | `boolean active` + `@Column(nullable = false)` |
| Exposed in the API | a field on the response DTO **and** a line in the converter |
| Not exposed | **stays only in the entity — the converter must not read it** |

That last row matters. `UserResponseDTO` deliberately withholds `createAt`,
`updateAt` and `password`. Defaulting to "expose every entity field" would leak
auditing metadata the project chose to keep internal.

### When the user says "decide for me"

Apply the defaults above, then **enumerate every assumption in the return
manifest**. Never invent silently.

## 3. Case A touch set — the 6 files

1. `dtos/request/XxxRequestDTO.java` — jakarta validation, `@AllArgsConstructor
   @NoArgsConstructor @Getter`
2. `services/interfaces/IXxxService.java` — DTO in, DTO out
3. `services/implementations/XxxService.java` — the logic and the invariants
4. `controllers/XxxController.java` — the method
5. `messages/SwaggerMessages.java` — `X_OPERATION` + `X_RESPONSE_<code>`
6. `messages/ResponseMessages.java` — `X_SUCCESSFUL`

Plus, when applicable:

- `configurations/exceptions/XxxException.java` **and its handler** — section 6
- `messages/ExceptionMessages.java`
- `README.md` — the full endpoint contract lives there (`AGENTS.md:56`)
- `AGENTS.md` — when the area or its role changes the endpoint table

## 4. The controller block

The order used by all nine methods of `UserController`, and do not reorder it:

```java
@GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = SwaggerMessages.USER_ME_OPERATION)
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = SwaggerMessages.USER_ME_RESPONSE_200),
        @ApiResponse(responseCode = "401", description = SwaggerMessages.ERROR_RESPONSE_401),
        @ApiResponse(responseCode = "403", description = SwaggerMessages.ERROR_RESPONSE_403),
        @ApiResponse(responseCode = "404", description = SwaggerMessages.ERROR_RESPONSE_404),
        @ApiResponse(responseCode = "500", description = SwaggerMessages.ERROR_RESPONSE_500)
})
@PreAuthorize("hasAnyRole('ADMIN','USER')")
public ResponseEntity<ApplicationResponse<UserResponseDTO>> me(
        @AuthenticationPrincipal UserPrincipal principal) {
    log.info("GET:api/users/me");
    UserResponseDTO dto = userService.me(principal.getUsername());
    log.info(ResponseMessages.ME_SUCCESSFUL);
    return ResponseEntity.ok(new ApplicationResponse<>(dto, ResponseMessages.ME_SUCCESSFUL));
}
```

Shared response descriptions come from the common block of `SwaggerMessages`
(`ERROR_RESPONSE_400`, `_401`, `_403`, `_404`, `_409`, `_429`, `_500`, `_502`,
`_503`). Reuse them; only add a per-endpoint constant for the success code.

`POST` returns `201` with a `Location` header, as `register` does:

```java
return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/users/{id}")
                        .buildAndExpand(dto.getId())
                        .toUri())
        .body(new ApplicationResponse<>(dto, ResponseMessages.REGISTER_SUCCESSFUL));
```

### The `@RequestBody` gotcha

`UserController` imports `org.springframework.web.bind.annotation.RequestBody`, so
the OpenAPI annotation **must be fully qualified**:

```java
@io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = XxxRequestDTO.class)
        )
)
```

Writing `@RequestBody` for OpenAPI is an ambiguous-symbol compile error.

## 5. Authorization

Two shapes. They are not interchangeable.

| Shape | Annotation | Signature | Scope |
|-------|-----------|-----------|-------|
| Self-service | `hasAnyRole('ADMIN','USER')` | `(dto, principal)` | `principal.getUsername()`, no `id` in the path |
| Admin | `hasRole('ADMIN')` | `(id, dto, principal)` | takes `id` and passes `principal.getUsername()` as `callerEmail` |

Choose self-service when the caller acts on their own record and the path
carries no `id`. Choose admin otherwise.

### The `/auth/**` rule

`WebSecurityConfig` has `auth.requestMatchers("/auth/**", "/v3/**",
"/swagger-ui/**").permitAll()`. **Everything under `/auth` is public** unless an
explicit matcher overrides it.

Any endpoint that mutates authentication state — logout, refresh, revoke — must
**not** go under `/auth/*` while that `permitAll` stands. Put it under `/users/*`
where `anyRequest().authenticated()` applies, or add
`.requestMatchers("/auth/logout").authenticated()` explicitly **before** the
`permitAll`. In the second case a test asserting a tokenless call returns 401 is
mandatory, not optional.

### Invariants `@PreAuthorize` cannot express

These live in the service, because the annotation has no access to the target
row:

- an admin cannot delete themselves
- an admin cannot change their own status or their own role
- the last active admin cannot be demoted or deactivated
- an admin cannot be deleted at all

Every endpoint that accepts a `callerEmail` must honour all four.

## 6. Errors

### The silent 500

`MyExceptionHandler` ends with `@ExceptionHandler(Exception.class)`. A new domain
exception **without its own handler does not break the build** — it returns 500
with the generic `"An error Occurred"`. Add the handler and its test in the same
change as the exception.

```java
@ExceptionHandler(XxxNotExistException.class)
public ResponseEntity<Object> handlerXxxNotExist(XxxNotExistException e) {
    return build(HttpStatus.NOT_FOUND, e.getMessage());
}
```

### Exception → status

| Exception | Status |
|-----------|--------|
| `AuthenticationException` | 401 |
| `AuthenticationServiceUnavailableException` | 503 |
| `CurrentPasswordRequiredException` | 400 |
| `CurrentPasswordInvalidException` | **403**, not 401 |
| `ForbiddenException` | 403 |
| `InvalidRoleException` | 400 |
| `AttributeErrorsException` | 400 |
| `UserNotExistException` | 404 |
| `UserAlreadyExistException` | 409 |
| `UserInactiveException` | 409 |
| `MailSendException` | 502 |
| `TooManyAttemptsException` | 429 **+ `Retry-After` header** |
| `AccessDeniedException` | 403 |
| anything else | 500, `"An error Occurred"` |

`TooManyAttemptsException` is the only handler that sets a header. The explicit
`AccessDeniedException` handler exists because the catch-all would otherwise turn
every `@PreAuthorize` denial into a 500.

## 7. `@Transactional`

Not reflexive. Add it when a write must roll back together with the mail send.
Today `register`, `updateStatus` and `updateRole` have it; `updateUser`,
`delete` and `me` do not.

**Do not move the mail send out of the transaction.** `register` and the
reactivation flow send before the commit on purpose: a send failure rolls back
the whole operation. This is the documented project contract, not an accident.

## 8. No inline strings

Every user-visible string comes from `SwaggerMessages`, `ResponseMessages` or
`ExceptionMessages`. There are no inline literals in the controllers today; keep
it that way.

## 9. Case B — a new bounded area

Everything in section 3, plus:

- **Entity** — follow `UserEntity`: Lombok `@AllArgsConstructor @NoArgsConstructor
  @Getter @Setter @Builder`, `@Setter(AccessLevel.NONE)` on the id, and
  `equals`/`hashCode` by id if the entity can land in a collection.
- **Repository** — `@Repository`, `extends JpaRepository<T, Integer>`.
- **Converter** — `@Component("xxxConverter")`, entity → DTO. It reads fields
  **outside any transaction**, so every field it touches must be eagerly
  available.
- **`@EntityGraph` on every reader that walks a relation.** This is the highest
  cost omission in the project. `roleEntities` is EAGER *and* the readers of
  `IUserRepository` still carry `@EntityGraph(attributePaths = "roleEntities")` —
  deliberately redundant, because flipping it to LAZY throws
  `LazyInitializationException` in the converter. **If the new relation makes an
  existing entity gain a field, all three `@EntityGraph` annotations in
  `IUserRepository` become `"roleEntities,newField"`-style lists.** Miss one and
  you ship an N+1 that no test catches and the login still works.

  The three are `findByEmail`, `findAllByOrderByIdAsc` and `findAllByActive`.
  `findAllByRoleForUpdate` is the exception: it has no `@EntityGraph`, only a
  `@Query` with `select distinct`, so it needs its own decision about whether the
  new field is joined there too.
- **Mail** — a new Thymeleaf template plus `emailService.sendEmail(to[], subject,
  template, vars)`, sent **inside** the transaction following `UserService.register`.
- **No migrations.** `spring.jpa.hibernate.ddl-auto=update` and no
  Flyway/Liquibase, so the schema change lands in production silently. Say so in
  the return manifest.
- **Seeder** — if dev environments need a row, extend the `@Profile("dev")`
  seeder with the same `if (count() == 0)` guard.

## 10. Case C — splitting an endpoint

Ordered, because the intermediate states do not compile or are wrong:

1. Create the narrow DTOs. One for the fields that stay, one for the fields that
   move.
2. Split the service into two methods. Remove the branch from the old one — a
   method that branches on "was a field sent?" is the thing being eliminated.
3. Point the existing route at the narrow method.
4. Add the new route for the moved behaviour.
5. **Update the three lists in `UserControllerAuthorizationTests`.** The old row
   is *replaced* by two new rows, and each may have a different expected status.
   A row left behind is a lie.
6. Update `README.md` and the `AGENTS.md` endpoint table.
7. This is a **breaking change in the frontend**: `api.ts` and the generated
   client `core/api/generated/api.ts` both change. Backend-first is safe because
   that client is generated from the OpenAPI spec — say to regenerate it.
8. Update the unit tests: every builder call for the old DTO must be revisited.

## 11. Verification checklist

```bash
# one _OPERATION constant per mapping
grep -c "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" src/main/java/com/organization/application/controllers/XxxController.java
grep -c "_OPERATION =" src/main/java/com/organization/application/messages/SwaggerMessages.java

# no inline strings in the controller
grep -n '"[A-Za-z]' src/main/java/com/organization/application/controllers/XxxController.java

# every new route is in the authorization matrix
grep -c "new Endpoint" src/test/java/com/organization/application/controllers/XxxControllerAuthorizationTests.java

# every new exception has a handler
grep -c "@ExceptionHandler" src/main/java/com/organization/application/configurations/exceptions/MyExceptionHandler.java
```

Then hand the endpoint to the `test-author` agent. Do not write the tests here.
