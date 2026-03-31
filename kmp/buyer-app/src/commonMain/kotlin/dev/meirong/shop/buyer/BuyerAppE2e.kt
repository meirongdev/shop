package dev.meirong.shop.buyer

import dev.meirong.shop.kmp.core.model.AuthSession

data class BuyerAppE2eConfig(
    val enabled: Boolean = false,
    val autoLogin: Boolean = false,
    val guestLogin: Boolean = false,
    val initialRoute: String? = null,
    val session: AuthSession? = null,
    val onStateChange: (BuyerAppE2eState) -> Unit = {}
)

data class BuyerAppE2eState(
    val route: String,
    val status: String,
    val username: String? = null,
    val message: String? = null
)
