package com.nexopos.desktop.platform

import com.nexopos.shared.Platform

/**
 * Desktop implementation of Platform interface.
 */
class DesktopPlatform : Platform {
    override val name: String = buildString {
        append("Linux Desktop (JVM)")
        append(" - OS: ${System.getProperty("os.name")}")
        append(" - Arch: ${System.getProperty("os.arch")}")
        append(" - Java: ${System.getProperty("java.version")}")
    }
}
