package com.specthinker.auth

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Component
class CurrentUserResolver {

    fun fromRequest(request: HttpServletRequest): CurrentUser? =
        request.getAttribute(CurrentUserKey.ATTRIBUTE) as? CurrentUser

    fun currentRequest(): HttpServletRequest? {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        return attrs?.request
    }

    fun current(): CurrentUser? {
        val req = currentRequest() ?: return null
        return fromRequest(req)
    }
}
