package dev.meirong.shop.seller.di

import dev.meirong.shop.kmp.core.di.coreModule
import org.koin.core.module.Module
import org.koin.dsl.module

val sellerModule: Module = module {
    includes(coreModule)
}
