package com.specthinker.config

import com.specthinker.llm.LlmProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

@Configuration
class HttpClientConfig {

    @Bean
    fun httpClient(llm: LlmProperties): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(llm.connectTimeout))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .version(HttpClient.Version.HTTP_1_1)
        .build()
}
