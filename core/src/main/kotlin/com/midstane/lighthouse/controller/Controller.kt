package com.midstane.lighthouse.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

/**
 * Defines a group of HTTP routes registered by Lighthouse during application startup.
 *
 * Implementations are usually contributed into the application's dependency graph and registered
 * by [com.midstane.lighthouse.start]. Use [baseRoute] for a shared route prefix, [auth] when the
 * whole controller should require a Ktor authentication provider, and [registerRoutes] for the
 * controller's route definitions.
 *
 * Example:
 * ```kotlin
 * class UserController(
 *     private val userService: UserService,
 * ) : Controller {
 *     override val baseRoute: String = "/users"
 *     override val auth: AuthRequirement = AuthRequirement.Required("jwt")
 *
 *     override fun registerRoutes(routes: LighthouseRouting) {
 *         routes.get<UserDto>("/me") {
 *             userService.currentUser()
 *         }
 *
 *         routes.post<CreateUserRequest, UserDto>("/") { request ->
 *             userService.create(request)
 *         }
 *     }
 * }
 * ```
 */
interface Controller {
    /**
     * Shared prefix applied to every route registered by this controller.
     *
     * The default empty value leaves route paths unchanged. When set to `"/users"`, route paths
     * such as `"/"` and `"/{id}"` resolve to `"/users"` and `"/users/{id}"`.
     */
    val baseRoute: String
        get() = ""

    /**
     * Authentication requirement for every route in this controller.
     *
     * The default [AuthRequirement.None] keeps the controller public. Use
     * [AuthRequirement.Required] to wrap all registered routes with Ktor's `authenticate` block.
     * The named providers must be installed in the Ktor application before Lighthouse registers
     * routes.
     */
    val auth: AuthRequirement
        get() = AuthRequirement.None

    /**
     * Registers this controller's routes with the Lighthouse route DSL.
     *
     * Routes are automatically scoped by [baseRoute], and body routes such as
     * [LighthouseRouting.post], [LighthouseRouting.put], and [LighthouseRouting.patch] parse and
     * validate request bodies before invoking the handler.
     */
    fun registerRoutes(routes: LighthouseRouting)
}

/**
 * Describes whether a [Controller] should be public or protected by Ktor authentication.
 */
sealed interface AuthRequirement {
    /**
     * Leaves the controller routes public.
     */
    data object None : AuthRequirement

    /**
     * Wraps the controller routes in Ktor's `authenticate` block.
     *
     * Pass one or more provider names to target configured authentication providers:
     *
     * ```kotlin
     * override val auth = AuthRequirement.Required("jwt")
     * ```
     *
     * An empty provider list delegates to Ktor's default authentication behavior.
     */
    data class Required(
        val providers: List<String> = emptyList(),
    ) : AuthRequirement {
        constructor(vararg providers: String) : this(providers.toList())
    }
}


suspend inline fun <reified T: Any> ApplicationCall.ok(data: T) {
    respond(status = HttpStatusCode.OK, message = data)
}
