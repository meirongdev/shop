package dev.meirong.shop.kmp.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    val accessToken: String,
    val username: String,
    val displayName: String,
    val principalId: String,
    val roles: List<String>,
    val portal: String
)
