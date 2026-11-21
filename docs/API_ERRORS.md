# API error response contract

`ApiError` is the standard error response model defined in `demo-app/src/main/java/com/notshahrad/spring_boot_base/common/response/ApiError.java`. It is an immutable Java record for public API errors.

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

## Integration status

Issue #15 defines and tests the `ApiError` contract only. Existing `GlobalExceptionHandler` methods currently return `ApiResponse` or `ValidationErrorResponse`; they are not switched to `ApiError` in this issue. Updating those exception handlers, their HTTP status mapping, and endpoint-level tests is separate work. Until those handlers are migrated, not every live API error will use this new schema.
