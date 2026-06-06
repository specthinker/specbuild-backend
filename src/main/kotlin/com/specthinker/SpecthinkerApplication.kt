package com.specthinker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class SpecthinkerApplication

fun main(args: Array<String>) {
    runApplication<SpecthinkerApplication>(*args)
}
