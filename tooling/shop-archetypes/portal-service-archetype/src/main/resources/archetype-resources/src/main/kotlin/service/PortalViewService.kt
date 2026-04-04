package ${package}.service

import org.springframework.stereotype.Service

@Service
class PortalViewService {
    fun homepage(): PortalPageModel = PortalPageModel(
        title = "${artifactId}",
        message = "Replace this page with your buyer/seller portal journey."
    )
}

data class PortalPageModel(
    val title: String,
    val message: String,
)
