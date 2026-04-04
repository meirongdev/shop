package dev.meirong.shop.seller

import dev.meirong.shop.kmp.core.model.AuthSession

data class SellerAppE2eConfig(
    val enabled: Boolean = false,
    val autoLogin: Boolean = false,
    val initialRoute: String? = null,
    val session: AuthSession? = null,
    val onStateChange: (SellerAppE2eState) -> Unit = {}
)

data class SellerAppE2eState(
    val route: String,
    val status: String,
    val username: String? = null,
    val message: String? = null
)
