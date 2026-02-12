* [x] user activity logging
* [ ] unit tests (junit, maybe integration tests)
* [x] log successfull attempts to important/secret paths (or endpoints maybe), and alert via email if this happens
* [x] better db indexing to improve read performances extremely in trade of a slight decrease in write performances
* [ ] accessToken (15m by default, still in jwt) + refreshToken (30d by default, for web in cookies, for mobile in jwt) approach instead of only 1 token approach -> improved security (especially against XSS) + way better UX (especially for mobile)
* [ ] FE guide (1 md file) having all endpoints and security, auth etc. implementations. 