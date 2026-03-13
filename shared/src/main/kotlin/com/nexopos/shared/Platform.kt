package com.nexopos.shared

/**
 * Platform abstraction for features that differ between Android and Desktop.
 * Implement this interface in each platform module.
 */
interface Platform {
    val name: String
}

/**
 * Placeholder for shared business logic.
 * This will eventually hold models, API clients, use cases, etc.
 */
object SharedModule {
    fun getPlatformInfo(platform: Platform): String {
        return "Running on: ${platform.name}"
    }
}
