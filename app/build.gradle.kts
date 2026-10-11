import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties

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

fun String.unquotePropertyValue(): String =
    trim().removeSurrounding("\"").removeSurrounding("'")

val cacheRefreshTestMode = providers.gradleProperty("cacheRefreshTestMode")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

// 本机签名属性优先取项目 local.properties（gitignore，各项目可用各自的密钥，
// 避免被 GRADLE_USER_HOME 全局同名属性劫持），其次 gradle 属性
// （CI 的 ORG_GRADLE_PROJECT_* 环境变量、-P、GRADLE_USER_HOME）。
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun signingProperty(name: String): Provider<String> = providers.provider {
    (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)
        ?.unquotePropertyValue()
}

val releaseStoreFile = signingProperty("RELEASE_STORE_FILE")
val releaseStorePassword = signingProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingProperty("RELEASE_KEY_PASSWORD")

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
        // 本地验证更新流程可用 -PappVersionName=1.20260101 -PappVersionCode=10831 模拟旧版本
        // （缺省为当天日期 + 提交数；覆盖安装校验的是 versionCode，模拟包需一并调低）
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 10000 + gitCommitCount
        versionName = (findProperty("appVersionName") as String?) ?: "1.${releaseTime()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "CACHE_REFRESH_TEST_MODE", "false")
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "true")
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
            buildConfigField("boolean", "UPDATE_CHECK_ENABLED", "false")
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
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
    implementation(libs.telephoto.zoomable.image.coil)

    // Animation
    implementation(libs.lottie.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // SAF DocumentFile access
    implementation(libs.documentfile)

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
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.room.testing)

    // Lifecycle + ViewModel + Navigation 3
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.process)

    // Coroutines
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
}

// AndroidX consistent resolution pins kotlinx-serialization to 1.7.3 transitively
// (via lifecycle-viewmodel-compose), but the Kotlin 2.3.21 serialization compiler
// plugin and Room 2.8.x's migration bundle both need the 1.8.x line. Pin every
// configuration to 1.8.1 so debug/release/androidTest share one serialization
// version — otherwise Room's MigrationTestHelper throws AbstractMethodError
// (GeneratedSerializer.typeParametersSerializers()) when loading schema JSON.
configurations.configureEach {
    listOf(
        "org.jetbrains.kotlinx:kotlinx-serialization-core",
        "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm",
        "org.jetbrains.kotlinx:kotlinx-serialization-json",
        "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm"
    ).forEach {
        resolutionStrategy.force("$it:1.8.1")
    }
}
