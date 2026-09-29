---
name: endpoint-tests
description: Use when writing, updating or reviewing tests for an endpoint in this Spring Boot project — the authorization matrix rows, controller contract tests, service unit tests, or mail rollback. Covers where each kind of test lives, the two response envelope shapes, and the conventions every test class follows. Triggers on "write tests for", "test the new endpoint", "nuevos tests", "add coverage", "authorization matrix", "why is this test green".
---

# Testing an endpoint in BaseApplicationSpringBoot

## 1. The matrix rule

`UserControllerAuthorizationTests` derives six `@ParameterizedTest`s from three
lists. **An endpoint missing from the right list is silently untested and green.**
This is the single highest-cost failure mode in the project's test suite.

| List | What belongs in it |
|------|--------------------|
| `allEndpoints` | every route on the controller |
| `adminOnlyEndpoints` | every route with `hasRole('ADMIN')` |
| `sharedEndpoints` | every route with `hasAnyRole('ADMIN','USER')` |

The row shape:

```java
private record Endpoint(String path, HttpMethod method, String body, int expected) {}

private static Stream<Endpoint> allEndpoints() {
    return Stream.of(
            new Endpoint("/users/me", HttpMethod.GET, null, 200),
            new Endpoint("/users", HttpMethod.GET, null, 200),
            new Endpoint("/users", HttpMethod.PUT, UPDATE_BODY, 200));
}
```

- `{target}` is a placeholder that `call()` replaces with `targetId()`.
- `expected` is the **happy-path status for the role that is allowed** — 201 for
  a `POST` that creates, 200 otherwise.
- Request bodies are `static final String` constants at the top of the class,
  declared as text blocks.

Six tests are derived, and they assert:

| Test | Source | Asserts |
|------|--------|---------|
| `anonymousAccessIsRejected` | all | 401 + `$.message` not empty |
| `malformedTokenIsRejected` | all | 401 |
| `inactiveCallerIsRejected` | all | 401 |
| `regularUserCannotReachAdminEndpoints` | admin-only | 403 + **`$.data` does not exist** |
| `administratorReachesEveryEndpoint` | all | `expected` |
| `regularUserReachesSharedEndpoints` | shared | `expected` |

A new controller gets its own matrix class with the same structure. Do not mix
routes from different controllers into one class.

### The matrix assumptions are tied to the token model

`inactiveCallerIsRejected` holds because a token for a deactivated user is
rejected. **When the auth model grows — for example a refresh token with its own
lifecycle — re-check these assumptions instead of adding rows.** A refresh token
is not a user token and does not follow the same rules.

## 2. The split rule

When one route becomes two, the old row is **replaced by two new rows**, and each
may have a different `expected` status. A route that disappears has its row
removed. A row left behind is a lie that keeps passing against nothing.

## 3. Where each kind of test lives

| Behaviour | Class |
|-----------|-------|
| Status codes, envelope shape, per-field validation detail, no internals leak | `UserControllerContractTests` |
| Service logic, invariants, exception branches | `UserServiceTests` — unit, `@Nested` |
| Mail failure rolls the transaction back | `RegisterMailRollbackTests`, `ReactivateMailRollbackTests` |
| N+1 / query count | `UserServiceQueryTests` — counts Hibernate statements |
| Login and throttling | `SecurityControllerTests` |
| CORS | `CorsTests` |
| Filters and handlers | `JwtFilterTests`, `JwtEntryPointTests`, `JwtAccessDeniedHandlerTests`, `SecurityErrorWriterTests` |
| The throttling state machine | `LoginThrottleTests` |

Prefer a unit test over `@SpringBootTest` when there is no web, security or
serialization behaviour involved. `@SpringBootTest` is expensive; do not pay for
it by default.

## 4. The base classes

`AbstractIntegrationTest` supplies a random 32-byte Base64 JWT secret through
`@DynamicPropertySource`, so no test depends on a real key.

`AbstractSecuredIntegrationTest` extends it with:

| Member | Purpose |
|--------|---------|
| `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` | the web slice, H2 in MySQL mode |
| `@MockBean IEmailService` | no real mail is ever sent |
| `@MockBean LoginThrottle` | no throttling state leaks between tests |
| `admin`, `regularUser`, `inactiveUser` | seeded fresh in `@BeforeEach` |
| `targetId()` | the id of `regularUser` — the safe mutation target |
| `adminToken()` / `userToken()` / `inactiveToken()` | ready-made bearers |
| `bearerFor(user)` | build a bearer for any seeded user |

**`targetId()` is the target for every mutation**, never `admin.getId()`: the
admin is protected by the "cannot delete an admin" invariant and the assertions
would fail for the wrong reason.

## 5. The two envelope shapes

`ApplicationResponse` is `@JsonInclude(NON_NULL)`, so:

| Case | Body | Assert with |
|------|------|-------------|
| Error | `{"message": "..."}` — **no `data` key** | `jsonPath("$.message").isNotEmpty()` and `jsonPath("$.data").doesNotExist()` |
| Validation | `{"message": "...", "data": {"field": "msg"}}` | `jsonPath("$.data.firstname").exists()` |

**Never assert on `$.data` in a non-2xx response.** The `regularUserCannotReach
AdminEndpoints` test already guards this, and it is the assertion that catches a
DTO accidentally leaking into a failure body.

Error bodies must never expose framework internals. The pattern already exists:

```java
assertFalse(body.contains("org.springframework"), "a Spring class name leaked: " + body);
assertFalse(body.contains("org.hibernate"), "a Hibernate class name leaked: " + body);
assertFalse(body.contains("Exception"), "an exception class name leaked: " + body);
assertFalse(body.contains("jakarta."), "a Jakarta type leaked: " + body);
```

## 6. Conventions

- Class suffix is `*Tests`, not `*Test`.
- `@DisplayName` is in **English** and describes the behaviour, not the method
  name. "Update status reactivates a deactivated user", not "testUpdateStatus2".
- **Every assertion carries a reason.** An assertion without one is a trap for
  the next reader.
- `@ExtendWith(MockitoExtension.class)` with **strict stubs**. An unused stub
  fails the build — that is the point.
- `@Nested` groups the branches of one method.
- Every service method invoked needs a test for each exception it can throw.

## 7. If the endpoint sends mail

Two things, not one:

1. Assert the call — `verify(emailService).sendEmail(...)` with the right
   template name, subject and variable map.
2. Assert the rollback — make the mock throw, call the endpoint, then assert the
   row is **not** there. `RegisterMailRollbackTests` is the reference.

Skipping the rollback test leaves the transaction contract untested, and that
contract is deliberate here: the mail send sits inside the transaction so a
delivery failure undoes the write.

## 8. If the endpoint is public

The matrix does not apply — a public route has no token to reject. Assert
explicitly that the call works, **and** that it works without a token, because
`/auth/**` is `permitAll`. An accidentally-public state-changing endpoint is green
until someone notices in production.
