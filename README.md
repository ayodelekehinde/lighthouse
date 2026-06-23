# Lighthouse

Lighthouse is a Kotlin/Ktor toolkit for building API services with a small controller DSL,
standard JSON/error handling, request validation, authentication hooks, endpoint permissions, and
KSP-generated repositories and mappers.

### Published Dependency Setup

If Lighthouse is published to a Maven repository instead of used as project modules, replace the
project dependencies with the published artifacts:

```kotlin
dependencies {
    implementation("com.midstane.lighthouse:core:<version>")
    implementation("com.midstane.lighthouse:annotations:<version>")
    ksp("com.midstane.lighthouse:processors:<version>")
}
```

## Application Setup

Create a Metro dependency graph that implements `RouteGraph`, then call `lighthouse(graph)` from
your Ktor `Application`.

```kotlin
package com.example

import com.midstane.lighthouse.lighthouse
import com.midstane.lighthouse.dependency.RouteGraph
import dev.zacsweers.metro.createGraphFactory
import io.ktor.server.application.Application

fun Application.init() {
    val graph = createGraphFactory<AppGraph.Factory>().create(environment.config)
    lighthouse(graph)
}
```

Your graph must provide:

```kotlin
interface RouteGraph {
    val applicationConfig: ApplicationConfig
    val controllers: Set<Controller>
}
```

The sample graph uses Metro:

```kotlin
@DependencyGraph(LightHouseScope::class)
interface AppGraph : RouteGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides applicationConfig: ApplicationConfig): AppGraph
    }
}
```

## Configuration

`lighthouse` installs JSON content negotiation and Lighthouse status pages by default.

```kotlin
lighthouse(graph) {
    installContentNegotiation = true
    exposeExceptionMessages = false
}
```

Available options:

- `json`: the `kotlinx.serialization.json.Json` instance used by Ktor content negotiation.
- `installContentNegotiation`: set to `false` if the app installs `ContentNegotiation` itself.
- `exposeExceptionMessages`: set to `true` to return raw exception messages for unexpected errors.
- `permissionAuthorizer`: app-defined endpoint permission checker.

Default JSON settings:

```kotlin
Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

## Controllers

Create a class that implements `Controller`, then contribute it into the Metro controller set.

```kotlin
@ContributesIntoSet(LightHouseScope::class, binding<Controller>())
@Inject
class UserController(
    private val userRepository: UserRepository,
) : Controller {
    override val baseRoute: String = "/users"

    override fun registerRoutes(routes: LighthouseRouting) {
        routes.get<List<UserDto>>("") {
            userRepository.findAll().map(::toDto)
        }

        routes.post<CreateUserRequest, UserDto>("") { request ->
            val user = userRepository.save(request.toUser())
            user.toDto()
        }
    }
}
```

### Route Prefixes

`baseRoute` is applied to every route registered by the controller.

```kotlin
override val baseRoute = "/users"

routes.get<UserDto>("/{id}") { ... }
```

The final route is `GET /users/{id}`.

### Route Methods

Lighthouse provides typed helpers for common HTTP methods:

```kotlin
routes.get<Response>("/path") { ... }
routes.post<Request, Response>("/path") { request -> ... }
routes.put<Request, Response>("/path") { request -> ... }
routes.patch<Request, Response>("/path") { request -> ... }
routes.delete<Response>("/path") { ... }
```

Handlers return a value and Lighthouse responds automatically:

- Non-`Unit` values return `200 OK`.
- `Unit` returns `204 No Content`.
- `post`, `put`, and `patch` receive and validate the request body before the handler runs.

### Raw Ktor Routes

Use `raw` when you need direct access to Ktor routing APIs:

```kotlin
routes.raw {
    get("/health") {
        call.respondText("ok")
    }
}
```

`raw` routes are intentionally low-level. Lighthouse route permissions are only applied by the typed
route helpers.

## Request Validation

Request bodies can implement `Validatable`.

```kotlin
@Serializable
data class CreateUserRequest(
    val email: String,
    val name: String,
) : Validatable {
    override fun validate(): List<ValidationError> = buildList {
        if (email.isBlank()) add(ValidationError("email", "email is required"))
        if (name.isBlank()) add(ValidationError("name", "name is required"))
    }
}
```

If validation returns errors, Lighthouse responds with `400 Bad Request` and the handler is not
called.

```json
{
  "success": false,
  "message": "Request validation failed",
  "errors": [
    { "field": "email", "message": "email is required" }
  ]
}
```

Missing request bodies also return `400 Bad Request`.

## Authentication

Authentication is still Ktor authentication. Install providers before calling `lighthouse`, then
mark a controller with `AuthRequirement.Required`.

```kotlin
fun Application.init() {
    install(Authentication) {
        basic("basic") {
            validate { credentials ->
                if (credentials.name == "admin" && credentials.password == "secret") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    lighthouse(graph)
}
```

Require auth for a controller:

```kotlin
class AdminController : Controller {
    override val baseRoute = "/admin"
    override val auth = AuthRequirement.Required("basic")

    override fun registerRoutes(routes: LighthouseRouting) {
        routes.get<AdminStatus>("") {
            AdminStatus("ok")
        }
    }
}
```

Authentication failures return `401 Unauthorized` through Ktor.

## Endpoint Permissions

Permissions are named strings declared on controllers and routes. Lighthouse does not know how your
app stores users, roles, JWT claims, or database permissions. Your app supplies that logic through
`PermissionAuthorizer`.

By default:

- routes with no permissions are allowed.
- routes with one or more permissions are denied with `403 Forbidden`.

Configure permission checks:

```kotlin
lighthouse(graph) {
    permissionAuthorizer = PermissionAuthorizer { call, required ->
        val principal = call.principal<UserIdPrincipal>() ?: return@PermissionAuthorizer false
        val userPermissions = permissionService.permissionsFor(principal.name)

        required.all(userPermissions::contains)
    }
}
```

Declare route permissions:

```kotlin
routes.get<List<UserDto>>("/users", "users:read") {
    getUsers()
}

routes.post<CreateUserRequest, UserDto>("/users", "users:create") { request ->
    createUser(request)
}
```

Declare controller-wide permissions:

```kotlin
class ReportsController : Controller {
    override val baseRoute = "/reports"
    override val auth = AuthRequirement.Required("jwt")
    override val permissions = setOf("reports:access")

    override fun registerRoutes(routes: LighthouseRouting) {
        routes.get<ReportList>("", "reports:read") {
            listReports()
        }
    }
}
```

The required permissions are combined:

```text
controller permissions + route permissions
```

For the example above, the user must have both:

```text
reports:access
reports:read
```

Permission checks run before request body parsing and before the handler is called.

### Authentication vs Permissions

Use authentication to prove who the user is. Use permissions to decide what the user can do.

- Missing or invalid authentication returns `401 Unauthorized`.
- Authenticated but missing permissions returns `403 Forbidden`.
- Authenticated and authorized routes continue to the handler.

## Error Responses

Lighthouse installs standard error handling through Ktor `StatusPages`.

| Condition | Status | Response |
| --- | --- | --- |
| Validation failure | `400 Bad Request` | `ErrorResponse` with validation errors |
| Missing request body | `400 Bad Request` | `ErrorResponse` |
| Permission denied | `403 Forbidden` | `ErrorResponse(message = "Permission denied")` |
| `LighthouseException` | `422 Unprocessable Entity` | exception message |
| Unexpected exception | `500 Internal Server Error` | safe message unless `exposeExceptionMessages = true` |

`ErrorResponse` shape:

```kotlin
@Serializable
data class ErrorResponse(
    val success: Boolean = false,
    val message: String,
    val errors: List<ValidationError> = emptyList(),
)
```

## Generated Repositories

Annotate repository interfaces with `@DataRepository`. The KSP processor generates an Exposed R2DBC
implementation and contributes it to Metro using the configured binding scope.

```kotlin
@DataRepository(
    entity = User::class,
    tableName = "users",
    bindingScope = LightHouseScope::class,
)
interface UserRepository : CrudRepository<User, Long> {
    suspend fun findByEmail(email: String): User?
    suspend fun findAllByAge(age: Int): List<User>
    suspend fun existsByEmail(email: String): Boolean
    suspend fun countByAge(age: Int): Long
}
```

`CrudRepository<E, ID>` provides:

```kotlin
suspend fun save(entity: E): E
suspend fun findById(id: ID): E?
suspend fun findAll(): List<E>
suspend fun deleteById(id: ID)
```

Supported finder method patterns include:

- `findBy<Property>(value): Entity?`
- `findAllBy<Property>(value): List<Entity>`
- `existsBy<Property>(value): Boolean`
- `countBy<Property>(value): Long`

Entity property annotations:

```kotlin
data class User(
    val id: Long = 0,
    val name: String,
    @Unique val email: String,
    @Autogenerate val uuid: Uuid = Uuid.NIL,
    @Text val bio: String = "",
)
```

- `@Unique`: marks a generated column as unique.
- `@Autogenerate`: marks a generated value.
- `@Text`: stores a string as text.

## Generated Mappers

Annotate mapper interfaces with `@Mapper`. The processor generates an implementation and contributes
it to Metro.

```kotlin
@Mapper(bindingScope = LightHouseScope::class)
interface UserMapper {
    @Mapping(target = "createdAt", expression = "\"\"")
    @Mapping(target = "updatedAt", expression = "\"\"")
    fun toUser(dto: UserDto): User

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "uuid", expression = "user.uuid.toString()")
    @Mapping(target = "occupationType", source = "occupation.type")
    fun toDto(user: User, occupation: Occupation): UserDto
}
```

Mapper behavior:

- Same-name properties with the same type are mapped automatically.
- Use `@Mapping(source = "...")` for renamed, nested, or ambiguous source values.
- Use `@Mapping(expression = "...")` for constants and conversions.
- Set exactly one of `source` or `expression` per `@Mapping`.
- Generated class name defaults to `Generated<InterfaceName>` unless `generatedName` is set.

## Running Tests

Run all tests:

```bash
./gradlew test
```

Run only core tests:

```bash
./gradlew :core:test
```

Run the sample server:

```bash
./gradlew :server:run
```
