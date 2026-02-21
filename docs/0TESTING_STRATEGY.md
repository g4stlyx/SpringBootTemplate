# Testing Strategy

## Core Principle

Don't test every function in every file. Test **behavior at the right architectural boundaries**.

Coverage is vanity. Correctness is sanity.

---

## Testing Layers

```
Controller  →  Service  →  Repository  →  DB
```

| Layer      | Test Type         | Goal                         | Tool                          |
|------------|-------------------|------------------------------|-------------------------------|
| Service    | Unit test         | Business logic correctness   | JUnit 5 + Mockito             |
| Controller | Web layer test    | Request/response validation  | `@WebMvcTest` + MockMvc       |
| Repository | Integration test  | Query correctness            | `@DataJpaTest` + H2           |
| Full app   | Integration test  | End-to-end critical flows    | `@SpringBootTest`             |

---

## What to Test — High ROI Priority Order

### 1. Service Layer (Highest Priority)

This is where business logic lives. If this breaks, your system is wrong.

Test:
- Valid flows (happy path)
- Invalid inputs / bad state → expected exceptions
- Security logic (authorization checks, token validation)
- State transitions (account locking, token rotation)
- Edge cases that actually matter in production

Tools: **JUnit 5 + Mockito**. Mock all repositories. Test the logic, not the framework.

### 2. Repository Layer (Where You Write Custom Queries)

Only test what Spring Data JPA doesn't auto-implement:
- Custom `@Query` methods (JPQL or native SQL)
- `@Modifying` bulk updates/deletes
- Complex filtering / dynamic queries
- Date range queries

Skip testing trivial `findById`, `save`, `existsBy*` — these are framework responsibility.

Tools: **`@DataJpaTest` + H2 in-memory DB**

### 3. Controller Layer (Lightweight)

Test:
- Correct HTTP status codes returned
- Validation errors (`@Valid`) trigger 400
- Security annotations enforce access (`@PreAuthorize`)
- Input deserialization / output serialization

Do NOT retest business logic here. That's the service's job.

Tools: **`@WebMvcTest` + `@MockBean` for services**

### 4. Integration Tests (Selective — Max 10)

Test only critical cross-cutting flows:
- Full authentication flow (register → verify → login → refresh → logout)
- Transaction rollback scenarios
- Multi-service orchestration

Tools: **`@SpringBootTest` + Testcontainers** (or H2 for non-MySQL-specific paths)

---

## What NOT to Test

- Trivial getters/setters
- Lombok-generated code (`@Data`, `@Builder`)
- Simple DTO constructors
- Spring Data JPA derived query methods (`findById`, `save`, `existsBy...`)
- Aim for 100% coverage

---

## Realistic Coverage Targets

| Layer      | Target Coverage |
|------------|----------------|
| Services   | 80–90%         |
| Controllers| 60–70%         |
| Repositories | Where custom queries exist |
| Overall    | 70–85%         |

100% coverage usually means bad tests — you're testing the framework, not your logic.

---

## Risk-Driven Test Priority

For this stack (Spring Boot + JWT + Argon2 + MySQL + S3), the real failure risk lives in:

| Area                        | Risk      | Test Aggressively? |
|-----------------------------|-----------|--------------------|
| Authentication & JWT logic  | Very High | Yes                |
| Password hashing & salting  | Very High | Yes                |
| Refresh token rotation      | High      | Yes                |
| Rate limiting logic         | High      | Yes                |
| Account locking             | High      | Yes                |
| Email verification flow     | Medium    | Yes                |
| 2FA TOTP logic              | Medium    | Yes                |
| Activity logging            | Low       | Negative paths only|
| Image upload                | Low       | Error paths only   |

---

## Test Folder Structure

```
src/test/java/com/g4stly/templateApp/
├── repos/                         # @DataJpaTest — custom query validation
│   ├── AdminRepositoryTest.java
│   ├── ClientRepositoryTest.java
│   ├── RefreshTokenRepositoryTest.java
│   ├── AuthenticationErrorLogRepositoryTest.java
│   ├── UserActivityLogRepositoryTest.java
│   ├── VerificationTokenRepositoryTest.java
│   └── PasswordResetTokenRepositoryTest.java
│
├── services/                      # JUnit 5 + Mockito — business logic
│   ├── AuthServiceTest.java
│   ├── RefreshTokenServiceTest.java
│   ├── PasswordServiceTest.java
│   ├── TwoFactorAuthServiceTest.java
│   └── RateLimitServiceTest.java
│
├── controllers/                   # @WebMvcTest — HTTP layer
│   ├── AuthControllerTest.java
│   └── RefreshTokenControllerTest.java
│
├── integration/                   # @SpringBootTest — critical flows only
│   └── AuthFlowIntegrationTest.java
│
└── TemplateAppApplicationTests.java  # Spring context loads, nothing more
```

---

## Execution Pattern: Feature-by-Feature, Not File-by-File

Wrong approach: "Test every file in repos/"
Right approach: "Test the authentication feature end-to-end across all layers"

Sequence for testing a new feature:
1. Write service unit tests first
2. Write repository tests for any custom queries it relies on
3. Write controller test for the HTTP interface
4. Add one integration test if the flow is critical

---

## Tool Reference

| Tool                   | Purpose                                        |
|------------------------|------------------------------------------------|
| `@DataJpaTest`         | Loads only JPA slice, uses H2 in-memory        |
| `@WebMvcTest`          | Loads only web layer (controllers, filters)    |
| `@SpringBootTest`      | Full context — expensive, use sparingly        |
| `@MockBean`            | Replaces a Spring bean with a Mockito mock     |
| `Mockito.mock()`       | Pure unit test mock — no Spring context        |
| `@Transactional` (test)| Rolls back DB state after each test            |
| `@Sql`                 | Load SQL fixtures for integration tests        |
| `TestEntityManager`    | JPA testing utility for `@DataJpaTest`         |

---

## Current Status

- [x] Repository tests — all repos with custom queries
- [x] Service tests — AuthService, RefreshTokenService, PasswordService, TwoFactorAuthService, RateLimitService
- [x] Controller tests — AuthController, RefreshTokenController
- [x] Integration tests — Full auth flow
