package dev.meirong.shop.buyerportal

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["dev.meirong.shop"])
class BuyerPortalApplication

fun main(args: Array<String>) {
    runApplication<BuyerPortalApplication>(*args)
}
