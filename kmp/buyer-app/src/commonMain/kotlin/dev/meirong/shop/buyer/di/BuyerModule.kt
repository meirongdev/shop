package dev.meirong.shop.buyer.di

import dev.meirong.shop.kmp.core.di.coreModule
import org.koin.core.module.Module
import org.koin.dsl.module

val buyerModule: Module = module {
    includes(coreModule)
}
