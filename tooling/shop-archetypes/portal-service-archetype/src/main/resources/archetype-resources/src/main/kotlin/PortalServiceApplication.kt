package ${package}

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PortalServiceApplication

fun main(args: Array<String>) {
    runApplication<PortalServiceApplication>(*args)
}
