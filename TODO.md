* [x] user activity logging
* [x] log successfull attempts to important/secret paths (or endpoints maybe), and alert via email if this happens
* [x] better db indexing to improve read performances extremely in trade of a slight decrease in write performances
* [x] accessToken (15m by default, still in jwt) + refreshToken (30d by default, for web in cookies, for mobile in jwt) approach instead of only 1 token approach -> improved security (especially against XSS) + way better UX (especially for mobile)
* [x] FE guide (1 md file) having all endpoints and security, auth etc. implementations. 
* [ ] unit/integration tests (junit, maybe integration tests)
    * [x] docs and plan for the implementation
    * [x] repo testing
    * [x] service testing (bussiness logic)
    * [ ] controller testing
    * [ ] Integration tests (Full auth flow)