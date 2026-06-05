package com.specthinker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SpecthinkerApplication

fun main(args: Array<String>) {
    runApplication<SpecthinkerApplication>(*args)
}
