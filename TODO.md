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
* [ ] admin user management (level 0 & 1)