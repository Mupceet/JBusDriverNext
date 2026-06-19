import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

fun parseLocalProperty(text: String, key: String): String =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .firstNotNullOfOrNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@firstNotNullOfOrNull null
            val name = line.substring(0, separator).trim()
            if (name == key) line.substring(separator + 1).trim() else null
        }
        .orEmpty()

fun String.unquotePropertyValue(): String =
    trim().removeSurrounding("\"").removeSurrounding("'")

val localJavbusAuthCookie = providers.fileContents(layout.projectDirectory.file("../local.properties"))
    .asText
    .map { parseLocalProperty(it, "JAVBUS_AUTH_COOKIE") }
    .orElse("")

val javbusAuthCookie = providers.gradleProperty("JAVBUS_AUTH_COOKIE")
    .orElse(providers.environmentVariable("JAVBUS_AUTH_COOKIE"))
    .orElse(localJavbusAuthCookie)
    .orElse("")
    .get()
    .unquotePropertyValue()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

val cacheRefreshTestMode = providers.gradleProperty("cacheRefreshTestMode")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE")
    .map { it.unquotePropertyValue() }
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD")
    .map { it.unquotePropertyValue() }
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS")
    .map { it.unquotePropertyValue() }
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD")
    .map { it.unquotePropertyValue() }

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.orNull.isNullOrBlank() }

fun releaseTime(): String? = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

android {
    namespace = "me.jbusdriver"
    compileSdk = 37

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    defaultConfig {
        applicationId = "me.jbus"
        minSdk = 28
        versionCode = 10000 + gitCommitCount
        versionName = "1.${releaseTime()}"
        buildConfigField("String", "JAVBUS_AUTH_COOKIE", "\"$javbusAuthCookie\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "CACHE_REFRESH_TEST_MODE", "false")
            applicationIdSuffix = ".release"
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["allowBackup"] = "false"
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
        debug {
            buildConfigField("boolean", "CACHE_REFRESH_TEST_MODE", cacheRefreshTestMode.toString())
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            enableUnitTestCoverage = true
            manifestPlaceholders["allowBackup"] = "true"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: "unknown"
        variant.outputs.forEach { output ->
            output.outputFileName.set("jbus_${buildType}_v${android.defaultConfig.versionName}.apk")
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material)

    // Network
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Database
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    // Parse
    implementation(libs.jsoup)
    implementation(libs.gson)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.telephoto.zoomable.image)

    // Animation
    implementation(libs.lottie.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Debug
    debugImplementation(libs.leakcanary)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Lifecycle + ViewModel + Navigation 3
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)

    // Coroutines
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
}
