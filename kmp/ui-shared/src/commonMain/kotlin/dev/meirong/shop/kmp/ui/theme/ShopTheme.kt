package dev.meirong.shop.kmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun ShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ShopColors.Primary,
            onPrimary = ShopColors.OnPrimary,
            surface = ShopColors.Surface,
            error = ShopColors.Error
        ),
        content = content
    )
}
