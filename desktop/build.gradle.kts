import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
    
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// Set target platform: "current", "linux_x64", "linux_arm64", "windows_x64", "macos_x64", "macos_arm64"
// Use: ./gradlew :desktop:packageDeb -PtargetPlatform=linux_arm64
val targetPlatform = project.findProperty("targetPlatform")?.toString() ?: "current"

dependencies {
    // Shared module
    implementation(project(":shared"))
    
    // Compose Desktop - use target platform or current OS
    val composeDesktop = when (targetPlatform) {
        "linux_arm64" -> compose.desktop.linux_arm64
        "linux_x64" -> compose.desktop.linux_x64
        "windows_x64" -> compose.desktop.windows_x64
        "macos_x64" -> compose.desktop.macos_x64
        "macos_arm64" -> compose.desktop.macos_arm64
        else -> compose.desktop.currentOs
    }
    println("Building for target platform: $targetPlatform -> $composeDesktop")
    implementation(composeDesktop)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    
    // JNA for libusb native bindings
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    
    // Database - Exposed (Kotlin SQL framework)
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    
    // HTTP Client - OkHttp + Moshi (matching Android)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    
    // Preferences
    implementation("com.russhwolf:multiplatform-settings:1.1.1")
    implementation("com.russhwolf:multiplatform-settings-no-arg:1.1.1")
    
    // Koin DI (matching Android version)
    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("io.insert-koin:koin-compose:1.1.5")
}

compose.desktop {
    application {
        mainClass = "com.nexopos.desktop.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "NexoPOS-Desktop"
            packageVersion = "5.1.0"
            description = "NexoPOS Desktop Application for Linux ARM"
            vendor = "NexoPOS"
            
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
        
        buildTypes.release.proguard.isEnabled = false
    }
}

// Task to create uber JAR for ARM64 (run with Java on target device)
tasks.register<Jar>("uberJar") {
    val classifier = when (targetPlatform) {
        "linux_arm64" -> "arm64-uber"
        "linux_x64" -> "x64-uber"
        else -> "uber"
    }
    archiveClassifier.set(classifier)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "com.nexopos.desktop.MainKt"
    }
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

// Print build instructions
tasks.register("buildInfo") {
    doLast {
        println("""
            |=== NexoPOS Desktop Build Options ===
            |
            |1. Build for current OS (development):
            |   ./gradlew :desktop:run
            |
            |2. Build DEB for current architecture:
            |   ./gradlew :desktop:packageDeb
            |
            |3. Build uber JAR for Linux x64 (Intel/AMD):
            |   ./gradlew :desktop:uberJar -PtargetPlatform=linux_x64
            |   Output: desktop/build/libs/desktop-x64-uber.jar
            |   Run on x64: java -jar desktop-x64-uber.jar
            |
            |4. Build uber JAR for ARM64 (requires Java on target):
            |   ./gradlew :desktop:uberJar -PtargetPlatform=linux_arm64
            |   Output: desktop/build/libs/desktop-arm64-uber.jar
            |   Run on ARM64: java -jar desktop-arm64-uber.jar
            |
            |5. Cross-compile dependencies for ARM64:
            |   ./gradlew :desktop:packageDeb -PtargetPlatform=linux_arm64
            |   Note: JVM will still be x64, only Compose/Skiko are ARM64
            |
            |For true ARM64 .deb, build on ARM64 device or use QEMU.
        """.trimMargin())
    }
}
