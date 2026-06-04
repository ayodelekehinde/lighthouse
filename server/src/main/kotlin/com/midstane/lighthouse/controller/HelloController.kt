package com.midstane.lighthouse.controller

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.dto.UserDto
import com.midstane.lighthouse.entity.User
import com.midstane.lighthouse.repository.UserRepository
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

@ContributesIntoSet(LightHouseScope::class, binding<Controller>())
@Inject
class HelloController(
    private val userRepository: UserRepository
) : Controller {

    override fun registerRoutes(routing: Routing) {
        routing.get("/") {
            handleGetRoot(call)
        }
        routing.post("/add") {
            handleAddUser(call)
        }
        routing.get("/user") {
            handleGetUser(call)
        }
    }

    private suspend fun handleGetRoot(call: ApplicationCall) {
        call.respondText("Hello World!")
    }

    private suspend fun handleAddUser(call: ApplicationCall) {
        val user = call.receive<UserDto>()
        val savedUser = userRepository.save(user.toEntity())
        call.respond(HttpStatusCode.OK, savedUser.toDto())
    }

    private suspend fun handleGetUser(call: ApplicationCall) {
        call.ok(userRepository.findAll().map { it.toDto() })
    }

    private fun UserDto.toEntity(): User {
        return User(
            id = id,
            name = name,
            email = email,
            age = age,
        )
    }
    private fun User.toDto(): UserDto {
        return UserDto(
            id = id,
            name = name,
            email = email,
            age = age,
        )
    }
}
