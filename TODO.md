* [x] user activity logging
* [x] log successfull attempts to important/secret paths (or endpoints maybe), and alert via email if this happens
* [x] better db indexing to improve read performances extremely in trade of a slight decrease in write performances
* [x] accessToken (15m by default, still in jwt) + refreshToken (30d by default, for web in cookies, for mobile in jwt) approach instead of only 1 token approach -> improved security (especially against XSS) + way better UX (especially for mobile)
* [x] FE guide (1 md file) having all endpoints and security, auth etc. implementations. 
* [x] unit/integration tests (junit, maybe integration tests)
    * [x] docs and plan for the implementation
    * [x] repo testing
    * [x] service testing (bussiness logic)
    * [x] controller testing
    * [x] Integration tests (Full auth flow)
* [x] Java version is set to 21 from 17.
* [x] user (with usertype in it) in, coach & client out everywhere. "role" field to seperate user and admin.
* [x] specific thread pool executors for email sending and activity logging (also use web mvc interceptors for auto. user activity logging)
* [x] user profile endpoints (get,update profile; change password; deactivate account)
    * [x] `DELETE /api/v1/profile` — let a user soft-delete their own account (set `active = false`); admins can hard-delete but users have no self-closure option
    * [x] Account reactivation grace period (30-day) — auto-reactivate on login within grace period, permanent closure + PII anonymization after expiry via AccountCleanupScheduledService
    * [x] change email feature. set email_verified 0, send a verification email to the new email. ask for password in request before email update this. create a unit test for this.
* [x] admin user management (level 0 & 1)
* [x] security audit & fixes
    * [x] IP spoofing in rate limiter: replaced manual header-walking with `request.getRemoteAddr()` via `server.forward-headers-strategy=native`
    * [x] login timing enumeration: dummy Argon2 verify when username not found (constant-time response)
    * [x] unified login error messages: all "not found" paths return "Invalid credentials" (no role/table leakage)
    * [x] `logout-all` validates JWT before revoking sessions (prevents DoS with expired tokens)
    * [x] image delete IDOR: ownership check added in `UserImageController` — only deletes own image
    * [x] Swagger disabled in prod via `application-prod.properties`
    * [x] refresh token cookie changed to `SameSite=Strict`
    * [x] `JwtUtils.extractAllClaims()` now enforces issuer verification
    * [x] password reset now revokes all existing refresh tokens
    * [x] `userType` removed from registration DTO — always defaults to `APP_USER`
        * //TODO: of course this is an example, would change depending on the project.
    * [⚠️] in-memory rate limit store (`RateLimitService`): acceptable for single-instance template; Redis may be used here instead for distributed/persistent rate limiting.
---
* [ ] use redis at least for storing rate limit data in prod. 
* [ ] use google recaptcha especially for auth endpoints in prod. (extra protection against brute-force attacks and bots)
* [ ] use cloudflare in prod.