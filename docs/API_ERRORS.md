# API error response contract

`ApiError` is the standard error response model defined in `base-web/src/main/java/com/notshahrad/spring_boot_base/common/response/ApiError.java`. It is an immutable Java record for public API errors. The `base-web` JAR owns the reusable `GlobalExceptionHandler`; the `base-security` JAR consumes `ApiError` without depending on `demo-app`.

## Enable MVC exception handling in an application

Import `spring-boot-base-bom` and add the starter to your servlet application's Maven dependencies.

```xml
<dependency>
    <groupId>io.github.shahradbagheri</groupId>
    <artifactId>base-web-starter</artifactId>
</dependency>
```

The starter registers `GlobalExceptionHandler` through Boot auto-configuration, including in applications outside Base's package tree. It also provides the `ApiResponse` and `ApiError` types. Controllers can return `ApiResponse` explicitly. A bean typed as `GlobalExceptionHandler` or a subclass replaces the default. For an independent advice that handles specific exceptions, use a higher precedence than the default advice.

## JSON shape

An ordinary error has the following structure:

```json
{
  "status": 404,
  "message": "Resource not found",
  "timestamp": "2026-11-21T10:00:00Z",
  "fieldErrors": {}
}
```

A validation error uses the same structure with field-specific messages:

```json
{
  "status": 400,
  "message": "Validation failed",
  "timestamp": "2026-11-21T10:00:00Z",
  "fieldErrors": {
    "email": "Email must be valid",
    "name": "Name is required"
  }
}
```

| Property | Type | Meaning |
| --- | --- | --- |
| `status` | integer | HTTP error status code (400–599) |
| `message` | string | Public-facing error summary, never blank |
| `timestamp` | ISO-8601 string | UTC instant at which the error response was created |
| `fieldErrors` | object | Map of field names to client-safe validation messages; an empty object for other errors |

All four properties are always present, and `fieldErrors` is never null. Field errors are defensively copied and cannot be modified after construction.

## Using the model

Create a general error using `ApiError.of(status, message)`:

```java
ApiError notFound = ApiError.of(404, "Resource not found");
```

Supply field validation details when appropriate:

```java
ApiError validation = ApiError.of(
        400,
        "Validation failed",
        Map.of("email", "Email must be valid")
);
```

The response model is intentionally independent of exception handling and HTTP mapping decisions. It does not accept a `Throwable` or include a stack trace, exception class, internal path, or cause. Callers must use **client-safe messages**: never pass raw unexpected exception messages, SQL diagnostics, stack traces, credentials, or other internal details into the public response.

## HTTP exception mapping

`GlobalExceptionHandler` returns this response structure for the common Spring MVC failures handled by the application:

| Failure | HTTP status | Public message | `fieldErrors` |
| --- | --- | --- | --- |
| `ResourceNotFoundException` | 404 | Exception's intentional public message | `{}` |
| `BusinessException` | 400 | Exception's intentional public message | `{}` |
| Bean validation on `@Valid @RequestBody` | 400 | `Validation failed` | One entry per invalid field |
| Malformed request JSON | 400 | `Malformed request body` | `{}` |
| Unexpected exception | 500 | `An unexpected error occurred` | `{}` |

Unexpected exceptions are logged server-side and their internal messages are not included in the response. Validation errors use the configured constraint messages without including rejected values; if a message is missing, the fallback is `Invalid value`. When a field has multiple violations, the first message is returned for that field.

The exception types live in framework-independent `base-core`; HTTP status mapping and `ApiError` serialization live in `demo-app`. HTTP-level behavior is exercised through MockMvc tests. Other framework-generated errors not explicitly handled here may still use Spring's default response format.
