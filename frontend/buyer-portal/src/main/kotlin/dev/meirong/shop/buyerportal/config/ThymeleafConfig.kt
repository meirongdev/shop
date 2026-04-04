package dev.meirong.shop.buyerportal.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class ThymeleafConfig(
    private val request: HttpServletRequest
) {
    @ModelAttribute("currentUri")
    fun currentUri(): String = request.requestURI
}
