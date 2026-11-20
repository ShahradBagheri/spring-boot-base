# Implementation Roadmap

This document is the recommended execution order for the issues in this repository. It is a practical sequence, not a claim that every issue strictly depends on the one before it. Follow the issue descriptions and dependency links when work needs to branch.

## Working rules

- Work on one issue at a time unless two tasks are explicitly independent.
- Keep the project buildable after each structural change.
- Add tests with the feature they verify; do not postpone all testing until the end.
- Update documentation as architecture and behavior change. The documentation phase below is for final consolidation.
- Keep optional capabilities out of the initial module set until they are implemented.

## Phase 1 — Architecture and module foundation

1. [#87 — Define module boundaries and dependency rules](https://github.com/ShahradBagheri/spring-boot-base/issues/87) — see [Module boundaries and dependency rules](MODULE-BOUNDARIES.md).
2. [#8 — Review the BaseEntity contract](https://github.com/ShahradBagheri/spring-boot-base/issues/8) — see [BaseEntity contract](BASE-ENTITY-CONTRACT.md).
3. [#88 — Convert the root project into a Maven parent and aggregator POM](https://github.com/ShahradBagheri/spring-boot-base/issues/88)
4. [#89 — Create framework-independent base-core](https://github.com/ShahradBagheri/spring-boot-base/issues/89)
5. [#90 — Create base-data-jpa and move the JPA foundation](https://github.com/ShahradBagheri/spring-boot-base/issues/90)
6. [#91 — Create demo-app as the executable Spring Boot consumer](https://github.com/ShahradBagheri/spring-boot-base/issues/91)
7. [#92 — Verify the Maven reactor build and module isolation](https://github.com/ShahradBagheri/spring-boot-base/issues/92)
8. [#93 — Document the multi-module structure and extension workflow](https://github.com/ShahradBagheri/spring-boot-base/issues/93)

Initial modules should be only `base-core`, `base-data-jpa`, and `demo-app`. Add optional modules when their features are implemented, rather than creating empty modules in advance.

Status: items 3 to 6 landed as one change. The root `pom.xml` is now a `pom`-packaged aggregator and parent, it holds no source code, and the three child modules inherit the group, version, compiler settings, and managed dependency versions from it. Remaining work for this phase is verification ([#92](https://github.com/ShahradBagheri/spring-boot-base/issues/92)) and documentation ([#93](https://github.com/ShahradBagheri/spring-boot-base/issues/93)).

## Phase 2 — JPA foundation

9. [#9 — Verify BaseEntity mapping and identifier generation](https://github.com/ShahradBagheri/spring-boot-base/issues/9)
10. [#10 — Implement safe equals() and hashCode() behavior](https://github.com/ShahradBagheri/spring-boot-base/issues/10)
11. [#11 — Finalize the soft-delete design and implementation](https://github.com/ShahradBagheri/spring-boot-base/issues/11)
12. [#12 — Configure JPA auditing](https://github.com/ShahradBagheri/spring-boot-base/issues/12)
13. [#13 — Add createdAt and updatedAt auditing fields](https://github.com/ShahradBagheri/spring-boot-base/issues/13)
14. [#14 — Test the auditing lifecycle](https://github.com/ShahradBagheri/spring-boot-base/issues/14)

## Phase 3 — Database migrations and PostgreSQL integration tests

15. [#19 — Add Flyway dependencies and configuration to demo-app](https://github.com/ShahradBagheri/spring-boot-base/issues/19)
16. [#20 — Add the initial database migration](https://github.com/ShahradBagheri/spring-boot-base/issues/20)
17. [#21 — Test Flyway migrations](https://github.com/ShahradBagheri/spring-boot-base/issues/21)
18. [#22 — Add Testcontainers dependencies](https://github.com/ShahradBagheri/spring-boot-base/issues/22)
19. [#23 — Create reusable PostgreSQL integration-test setup](https://github.com/ShahradBagheri/spring-boot-base/issues/23)
20. [#24 — Test persistence and migrations against PostgreSQL](https://github.com/ShahradBagheri/spring-boot-base/issues/24)

## Phase 4 — Web and API error foundation

21. [#16 — Add framework-independent application exceptions in base-core](https://github.com/ShahradBagheri/spring-boot-base/issues/16)
22. [#15 — Define the API error response](https://github.com/ShahradBagheri/spring-boot-base/issues/15)
23. [#17 — Implement the global exception handler](https://github.com/ShahradBagheri/spring-boot-base/issues/17)
24. [#18 — Standardize validation error responses](https://github.com/ShahradBagheri/spring-boot-base/issues/18)

The web-specific pieces belong in the future optional `base-web` module.

## Phase 5 — Security baseline

25. [#25 — Choose the security policy](https://github.com/ShahradBagheri/spring-boot-base/issues/25)
26. [#26 — Add optional base-security configuration](https://github.com/ShahradBagheri/spring-boot-base/issues/26)
27. [#27 — Define authentication and authorization error responses](https://github.com/ShahradBagheri/spring-boot-base/issues/27)
28. [#28 — Add a protected endpoint to demo-app](https://github.com/ShahradBagheri/spring-boot-base/issues/28)

## Phase 6 — User identity and OAuth sign-in

29. [#33 — Choose the session/token model](https://github.com/ShahradBagheri/spring-boot-base/issues/33)
30. [#37 — Design local user identities](https://github.com/ShahradBagheri/spring-boot-base/issues/37)
31. [#38 — Implement user provisioning and account linking](https://github.com/ShahradBagheri/spring-boot-base/issues/38)
32. [#34 — Add the OAuth2 Client foundation](https://github.com/ShahradBagheri/spring-boot-base/issues/34)
33. [#35 — Implement Google OIDC sign-in](https://github.com/ShahradBagheri/spring-boot-base/issues/35)
34. [#36 — Implement Apple OIDC sign-in](https://github.com/ShahradBagheri/spring-boot-base/issues/36)
35. [#39 — Test OIDC sign-in and account linking](https://github.com/ShahradBagheri/spring-boot-base/issues/39)
36. [#40 — Document OAuth sign-in configuration](https://github.com/ShahradBagheri/spring-boot-base/issues/40)

Keep OAuth provider integration in the optional `base-auth-oauth2` module, built on the shared authentication and security contracts.

## Phase 7 — Email authentication and notifications

37. [#50 — Define email identity and authentication behavior](https://github.com/ShahradBagheri/spring-boot-base/issues/50)
38. [#51 — Implement email storage, normalization, and verification state](https://github.com/ShahradBagheri/spring-boot-base/issues/51)
39. [#54 — Define the email delivery abstraction](https://github.com/ShahradBagheri/spring-boot-base/issues/54)
40. [#55 — Implement an email delivery adapter](https://github.com/ShahradBagheri/spring-boot-base/issues/55)
41. [#52 — Implement email/password signup and login](https://github.com/ShahradBagheri/spring-boot-base/issues/52)
42. [#53 — Implement email verification and password reset](https://github.com/ShahradBagheri/spring-boot-base/issues/53)
43. [#56 — Add email templates and notifications](https://github.com/ShahradBagheri/spring-boot-base/issues/56)
44. [#57 — Test email authentication and delivery](https://github.com/ShahradBagheri/spring-boot-base/issues/57)
45. [#58 — Document email configuration and flows](https://github.com/ShahradBagheri/spring-boot-base/issues/58)

Keep authentication behavior and outbound email delivery as separate optional capabilities (`base-auth-email` and `base-mail`).

## Phase 8 — Phone authentication

46. [#42 — Define the phone-number authentication lifecycle](https://github.com/ShahradBagheri/spring-boot-base/issues/42)
47. [#43 — Implement phone-number storage and normalization](https://github.com/ShahradBagheri/spring-boot-base/issues/43)
48. [#45 — Define the SMS delivery abstraction](https://github.com/ShahradBagheri/spring-boot-base/issues/45)
49. [#44 — Implement OTP generation and verification](https://github.com/ShahradBagheri/spring-boot-base/issues/44)
50. [#46 — Implement phone signup and login](https://github.com/ShahradBagheri/spring-boot-base/issues/46)
51. [#47 — Add abuse protection and tests](https://github.com/ShahradBagheri/spring-boot-base/issues/47)
52. [#48 — Document phone authentication configuration](https://github.com/ShahradBagheri/spring-boot-base/issues/48)

Keep the phone authentication flow in `base-auth-phone` and SMS delivery in the separate `base-sms` capability.

## Phase 9 — Guest and anonymous users

53. [#60 — Define the guest-user lifecycle](https://github.com/ShahradBagheri/spring-boot-base/issues/60)
54. [#61 — Define public and authenticated endpoint access](https://github.com/ShahradBagheri/spring-boot-base/issues/61)
55. [#62 — Implement guest identity and session handling](https://github.com/ShahradBagheri/spring-boot-base/issues/62)
56. [#63 — Define guest data ownership](https://github.com/ShahradBagheri/spring-boot-base/issues/63)
57. [#64 — Implement guest-to-account conversion](https://github.com/ShahradBagheri/spring-boot-base/issues/64)
58. [#65 — Implement guest expiry and cleanup](https://github.com/ShahradBagheri/spring-boot-base/issues/65)
59. [#66 — Test guest-user flows](https://github.com/ShahradBagheri/spring-boot-base/issues/66)
60. [#67 — Document guest-user behavior](https://github.com/ShahradBagheri/spring-boot-base/issues/67)

## Phase 10 — Optional MCP server integration

61. [#78 — Decide the MCP architecture](https://github.com/ShahradBagheri/spring-boot-base/issues/78)
62. [#79 — Add Spring AI MCP integration](https://github.com/ShahradBagheri/spring-boot-base/issues/79)
63. [#80 — Implement MCP tools](https://github.com/ShahradBagheri/spring-boot-base/issues/80)
64. [#81 — Implement MCP resources](https://github.com/ShahradBagheri/spring-boot-base/issues/81)
65. [#82 — Implement MCP prompts](https://github.com/ShahradBagheri/spring-boot-base/issues/82)
66. [#83 — Secure MCP endpoints](https://github.com/ShahradBagheri/spring-boot-base/issues/83)
67. [#84 — Test MCP integration](https://github.com/ShahradBagheri/spring-boot-base/issues/84)
68. [#85 — Document MCP configuration and usage](https://github.com/ShahradBagheri/spring-boot-base/issues/85)

MCP belongs in an optional `base-mcp-server` module. Verify compatibility with the selected Spring AI and Spring Boot versions when this phase is reached.

## Phase 11 — Final documentation and end-to-end example

69. [#29 — Complete the README setup and run instructions](https://github.com/ShahradBagheri/spring-boot-base/issues/29)
70. [#30 — Document module structure and extension points](https://github.com/ShahradBagheri/spring-boot-base/issues/30)
71. [#31 — Build a complete vertical-slice example](https://github.com/ShahradBagheri/spring-boot-base/issues/31)

## Immediate next step

The design reviews in [#87](https://github.com/ShahradBagheri/spring-boot-base/issues/87) and [#8](https://github.com/ShahradBagheri/spring-boot-base/issues/8) are recorded in [Module boundaries and dependency rules](MODULE-BOUNDARIES.md) and [BaseEntity contract](BASE-ENTITY-CONTRACT.md). Complete the structural work in [#88](https://github.com/ShahradBagheri/spring-boot-base/issues/88)–[#92](https://github.com/ShahradBagheri/spring-boot-base/issues/92) before moving the existing JPA foundation into its dedicated module.
