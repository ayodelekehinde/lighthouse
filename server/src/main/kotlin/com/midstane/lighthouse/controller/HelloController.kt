package com.midstane.lighthouse.controller

import com.midstane.lighthouse.dependency.LightHouseScope
import com.midstane.lighthouse.dto.UserDto
import com.midstane.lighthouse.mapper.UserMapper
import com.midstane.lighthouse.repository.OccupationRepository
import com.midstane.lighthouse.repository.UserRepository
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.requirePathParameter
import io.ktor.server.request.requireQueryParameter

@ContributesIntoSet(LightHouseScope::class, binding<Controller>())
@Inject
class HelloController(
    private val userRepository: UserRepository,
    private val occupationRepository: OccupationRepository,
    private val userMapper: UserMapper,
    private val applicationConfig: ApplicationConfig,
) : Controller {

    override fun registerRoutes(routes: LighthouseRouting) {
        routes.get("/"){
            rootMessage()
        }
        routes.post<UserDto, UserDto>("/add") { user ->
            addUser(user)
        }
        routes.get<List<UserDto>>("/user") {
            getUsers()
        }
    }

    private fun rootMessage(): String {
        return "Hello World! ${applicationConfig.property("ktor.deployment.port").getString()}"
    }

    private suspend fun addUser(user: UserDto): UserDto {
        val savedUser = userRepository.save(userMapper.toUser(user))
        val savedOccupation = occupationRepository.save(userMapper.toOccupation(user, savedUser.id))
        return userMapper.toDto(savedUser, savedOccupation)
    }

    private suspend fun getUsers(): List<UserDto> {
        return userRepository.findAll().map {
            val occupation = occupationRepository.findByUserId(it.id) ?: throw Exception("Occupation not found")
            userMapper.toDto(it, occupation)
        }
    }
}
