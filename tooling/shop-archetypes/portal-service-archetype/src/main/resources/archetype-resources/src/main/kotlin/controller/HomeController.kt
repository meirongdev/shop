package ${package}.controller

import ${package}.service.PortalViewService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(
    private val portalViewService: PortalViewService,
) {

    @GetMapping("/")
    fun index(model: Model): String {
        val page = portalViewService.homepage()
        model.addAttribute("title", page.title)
        model.addAttribute("message", page.message)
        return "index"
    }
}
