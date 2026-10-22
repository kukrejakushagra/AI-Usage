package io.github.kukrejakushagra.claudeusage.data

import io.github.kukrejakushagra.claudeusage.BuildConfig
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("User-Agent", "ClaudeUsageWidget/${BuildConfig.VERSION_NAME} (Android; unofficial)")
                .build(),
        )
    }
    .build()
