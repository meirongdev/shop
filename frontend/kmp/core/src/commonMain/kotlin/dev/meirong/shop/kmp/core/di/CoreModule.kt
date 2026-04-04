package dev.meirong.shop.kmp.core.di

import dev.meirong.shop.kmp.core.network.HttpClientFactory
import dev.meirong.shop.kmp.core.session.TokenStorage
import org.koin.core.module.Module
import org.koin.dsl.module

val coreModule: Module = module {
    single { HttpClientFactory.create(get<TokenStorage>()) }
}
