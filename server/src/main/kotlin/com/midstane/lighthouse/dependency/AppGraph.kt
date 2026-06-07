package com.midstane.lighthouse.dependency

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.ktor.server.config.ApplicationConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

@DependencyGraph(LightHouseScope::class)
interface AppGraph : RouteGraph {

    @Provides
    fun provideR2dbcDatabase(config: ApplicationConfig): R2dbcDatabase {
        return R2dbcDatabase.connect(
            url = config.property("database.r2dbc.url").getString(),
            driver = config.property("database.r2dbc.driver").getString(),
            user = config.property("database.r2dbc.user").getString(),
            password = config.property("database.r2dbc.password").getString(),
        )
    }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides applicationConfig: ApplicationConfig): AppGraph
    }
}
