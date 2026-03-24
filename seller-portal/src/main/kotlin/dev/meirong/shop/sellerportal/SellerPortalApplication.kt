package dev.meirong.shop.sellerportal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["dev.meirong.shop"])
class SellerPortalApplication

fun main(args: Array<String>) {
    runApplication<SellerPortalApplication>(*args)
}
