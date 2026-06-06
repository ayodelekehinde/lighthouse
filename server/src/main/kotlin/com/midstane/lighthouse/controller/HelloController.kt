package com.midstane.lighthouse.controller

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.dto.UserDto
import com.midstane.lighthouse.mapper.UserMapper
import com.midstane.lighthouse.repository.OccupationRepository
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
    private val userRepository: UserRepository,
    private val occupationRepository: OccupationRepository,
    private val userMapper: UserMapper,
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
        val savedUser = userRepository.save(userMapper.toUser(user))
        val savedOccupation = occupationRepository.save(userMapper.toOccupation(user, savedUser.id))
        call.respond(HttpStatusCode.OK, userMapper.toDto(savedUser, savedOccupation))
    }

    private suspend fun handleGetUser(call: ApplicationCall) {
        call.ok(
            userRepository.findAll().map {
                val occupation = occupationRepository.findByUserId(it.id) ?: throw Exception("Occupation not found")
                userMapper.toDto(it, occupation)
            }
        )
    }
}
