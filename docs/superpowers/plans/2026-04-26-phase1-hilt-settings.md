# Phase 1: Hilt Foundation + Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish Hilt DI infrastructure and migrate the Settings screen to modern Android architecture (ViewModel + Repository + Compose + Navigation) as the first feature in the Strangler Fig migration.

**Architecture:** New code lives under `me.jbusdriver.modern/` package, coexisting with existing MVP code. Hilt modules wrap existing singletons (NetClient, DB, CacheLoader). The new SettingsScreen provides URL switching and a bridge to the legacy SettingActivity for unmigrated features.

**Tech Stack:** Hilt, Jetpack Compose + Material 3, ViewModel + StateFlow, Kotlin Coroutines/Flow, Navigation Compose, Room (existing), Retrofit (existing).

**Design Spec:** `docs/superpowers/specs/2026-04-26-architecture-migration-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `gradle/libs.versions.toml` | Add Hilt, Compose, Navigation, Coroutines versions & libraries |
| Modify | `build.gradle.kts` (root) | Add Hilt + Compose compiler plugins |
| Modify | `app/build.gradle.kts` | Add Hilt, Compose, Navigation dependencies; enable Compose |
| Modify | `app/src/main/AndroidManifest.xml` | Register new Application class and ModernMainActivity |
| Modify | `app/src/main/java/me/jbusdriver/ui/activity/MainActivity.kt` | Redirect settings click to ModernMainActivity |
| Create | `app/src/main/java/me/jbusdriver/modern/JBusApplication.kt` | @HiltAndroidApp Application, delegates to old AppContext init |
| Create | `app/src/main/java/me/jbusdriver/modern/data/remote/di/NetworkModule.kt` | Provides OkHttpClient, Gson, JAVBusService |
| Create | `app/src/main/java/me/jbusdriver/modern/data/local/di/DatabaseModule.kt` | Provides Room databases and DAOs |
| Create | `app/src/main/java/me/jbusdriver/modern/data/SettingsRepository.kt` | Interface + DefaultSettingsRepository |
| Create | `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt` | Binds repository interfaces |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt` | @HiltViewModel with UiState |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/theme/Color.kt` | Color definitions |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt` | Material 3 theme |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/theme/Type.kt` | Typography definitions |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt` | Route constants |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt` | NavHost + route graph |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt` | Compose Settings UI |
| Create | `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt` | @AndroidEntryPoint, Compose host |
| Create | `app/src/test/java/me/jbusdriver/modern/data/DefaultSettingsRepositoryTest.kt` | Unit test for repository |
| Create | `app/src/test/java/me/jbusdriver/modern/ui/settings/SettingsViewModelTest.kt` | Unit test for ViewModel |

---

### Task 1: Add Dependencies to Build Files

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions and libraries to version catalog**

Add to `gradle/libs.versions.toml` under `[versions]`:

```toml
hilt = "2.51.1"
compose-bom = "2024.12.01"
lifecycle = "2.8.7"
activity-compose = "1.9.3"
navigation-compose = "2.8.5"
hilt-navigation-compose = "1.2.0"
coroutines = "1.9.0"
```

Add to `gradle/libs.versions.toml` under `[libraries]`:

```toml
# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Compose (BOM-managed)
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

Add to `gradle/libs.versions.toml` under `[plugins]`:

```toml
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Add plugins to root build.gradle.kts**

`build.gradle.kts` (root) should become:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

- [ ] **Step 3: Add plugins and dependencies to app/build.gradle.kts**

Add plugins at top of `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}
```

Add to `android {}` block, after existing `buildFeatures`:

```kotlin
buildFeatures {
    viewBinding = true
    dataBinding = true
    buildConfig = true
    compose = true  // ADD THIS
}
```

Add dependencies to `dependencies {}` block:

```kotlin
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
debugImplementation(libs.compose.ui.tooling)

// Lifecycle + ViewModel + Navigation (Compose)
implementation(libs.activity.compose)
implementation(libs.lifecycle.runtime.compose)
implementation(libs.lifecycle.viewmodel.compose)
implementation(libs.navigation.compose)

// Coroutines
implementation(libs.coroutines.android)
testImplementation(libs.coroutines.test)
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL (no code changes yet, just dependency setup)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "build: add Hilt, Compose, Navigation dependencies for Phase 1 migration"
```

---

### Task 2: Create JBusApplication

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/JBusApplication.kt`

- [ ] **Step 1: Create the Application class**

Create file `app/src/main/java/me/jbusdriver/modern/JBusApplication.kt`:

```kotlin
package me.jbusdriver.modern

import android.app.Application
import android.os.Environment
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import me.jbusdriver.BuildConfig
import me.jbusdriver.base.JBusManager
import me.jbusdriver.base.arrayMapof
import me.jbusdriver.common.JBus
import me.jbusdriver.http.JAVBusService
import java.io.File

@HiltAndroidApp
class JBusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLegacyCode()
    }

    private fun initLegacyCode() {
        // Delegate to original AppContext initialization logic
        JBusManager.setContext(this)
        me.jbusdriver.common.JBus = this

        if (BuildConfig.DEBUG) {
            Log.d("JBusApplication", "Debug mode enabled")
        }

        RxJavaPlugins.setErrorHandler {
            Log.e("JBusApplication", "RxJava undeliverable error", it)
        }

        this.registerActivityLifecycleCallbacks(JBusManager)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        JBus.JBusServices.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        JBus.JBusServices.clear()
    }
}
```

**Note:** `me.jbusdriver.common.JBus` is a top-level `lateinit var` in `common/AppContext.kt`. Setting it here ensures backward compatibility. The old `AppContext` class still exists but is no longer the manifest-declared Application class.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/JBusApplication.kt
git commit -m "feat(migration): add JBusApplication with @HiltAndroidApp and legacy init"
```

---

### Task 3: Update AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Update manifest**

Change `android:name` in `<application>` from `me.jbusdriver.common.AppContext` to `me.jbusdriver.modern.JBusApplication`, and add `ModernMainActivity`:

In `app/src/main/AndroidManifest.xml`, change:

```xml
android:name="me.jbusdriver.common.AppContext"
```

to:

```xml
android:name="me.jbusdriver.modern.JBusApplication"
```

Add after the `SettingActivity` `<activity>` element:

```xml
<activity
    android:name="me.jbusdriver.modern.ui.ModernMainActivity"
    android:exported="false" />
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(migration): register JBusApplication and ModernMainActivity in manifest"
```

---

### Task 4: Create Hilt NetworkModule

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/remote/di/NetworkModule.kt`

- [ ] **Step 1: Create NetworkModule**

Create file `app/src/main/java/me/jbusdriver/modern/data/remote/di/NetworkModule.kt`:

```kotlin
package me.jbusdriver.modern.data.remote.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.base.GSON
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.common.JBus
import me.jbusdriver.base.arrayMapof
import me.jbusdriver.common.KLog
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = NetClient.glideOkHttpClient

    @Provides
    @Singleton
    fun provideGson(): Gson = GSON

    @Provides
    @Singleton
    fun provideJavBusService(): JAVBusService {
        return JBus.JBusServices.getOrPut(JAVBusService.defaultFastUrl) {
            JAVBusService.getInstance(JAVBusService.defaultFastUrl)
        }
    }
}
```

**Note:** This wraps the existing `NetClient` and `JAVBusService` singletons. The Retrofit service creation logic stays in `JAVBusService.getInstance()`. Future migrations may refactor this to create services directly in the module.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/remote/di/NetworkModule.kt
git commit -m "feat(migration): add Hilt NetworkModule wrapping existing NetClient"
```

---

### Task 5: Create Hilt DatabaseModule

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/local/di/DatabaseModule.kt`

- [ ] **Step 1: Create DatabaseModule**

Create file `app/src/main/java/me/jbusdriver/modern/data/local/di/DatabaseModule.kt`:

```kotlin
package me.jbusdriver.modern.data.local.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.db.CollectDatabase
import me.jbusdriver.db.JBusDatabase
import me.jbusdriver.db.dao.CategoryDao
import me.jbusdriver.db.dao.HistoryDao
import me.jbusdriver.db.dao.LinkItemDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideJBusDatabase(): JBusDatabase = me.jbusdriver.db.DB.jBusDatabase

    @Provides
    @Singleton
    fun provideCollectDatabase(): CollectDatabase = me.jbusdriver.db.DB.collectDatabase

    @Provides
    fun provideHistoryDao(db: JBusDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideCategoryDao(db: CollectDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideLinkItemDao(db: CollectDatabase): LinkItemDao = db.linkItemDao()
}
```

**Note:** This wraps the existing `DB` object's lazy singletons. The Room databases are initialized on first access by the existing `DB` object.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/local/di/DatabaseModule.kt
git commit -m "feat(migration): add Hilt DatabaseModule wrapping existing Room DBs"
```

---

### Task 6: Create SettingsRepository

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/SettingsRepository.kt`

- [ ] **Step 1: Create repository interface and default implementation**

Create file `app/src/main/java/me/jbusdriver/modern/data/SettingsRepository.kt`:

```kotlin
package me.jbusdriver.modern.data

import me.jbusdriver.base.ACache
import me.jbusdriver.base.CacheLoader
import me.jbusdriver.base.GSON
import me.jbusdriver.base.common.C
import me.jbusdriver.common.JBus
import me.jbusdriver.http.JAVBusService
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    fun getCurrentUrl(): String
    fun getAvailableUrls(): List<String>
    suspend fun updateUrl(url: String)
}

@Singleton
class DefaultSettingsRepository @Inject constructor() : SettingsRepository {

    override fun getCurrentUrl(): String = JAVBusService.defaultFastUrl

    override fun getAvailableUrls(): List<String> {
        val cachedJson = CacheLoader.lru.get(C.Cache.BUS_URLS)
        if (!cachedJson.isNullOrBlank()) {
            val map = GSON.fromJson<LinkedHashMap<String, String>>(cachedJson)
            return map.values.distinct().filter { it.isNotBlank() }
        }
        val diskJson = CacheLoader.acache.getAsString(C.Cache.BUS_URLS)
        if (!diskJson.isNullOrBlank()) {
            val map = GSON.fromJson<LinkedHashMap<String, String>>(diskJson)
            return map.values.distinct().filter { it.isNotBlank() }
        }
        return listOf(JAVBusService.defaultFastUrl)
    }

    override suspend fun updateUrl(url: String) {
        JAVBusService.defaultFastUrl = url
        JAVBusService.INSTANCE = JAVBusService.getInstance(url)
        JBus.JBusServices.clear()
    }
}
```

**Note:** `DefaultSettingsRepository` reads cached URL lists from `CacheLoader` (populated by `SplashActivity`). URL switching updates the static `JAVBusService.defaultFastUrl` and recreates the service instance. This directly wraps existing code — future migrations may persist URLs through SharedPreferences instead.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/SettingsRepository.kt
git commit -m "feat(migration): add SettingsRepository wrapping existing URL management"
```

---

### Task 7: Create DataModule (Hilt Repository Binding)

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`

- [ ] **Step 1: Create DataModule**

Create file `app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt`:

```kotlin
package me.jbusdriver.modern.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.jbusdriver.modern.data.DefaultSettingsRepository
import me.jbusdriver.modern.data.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DefaultSettingsRepository
    ): SettingsRepository
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/data/di/DataModule.kt
git commit -m "feat(migration): add Hilt DataModule binding SettingsRepository"
```

---

### Task 8: Test SettingsRepository

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/data/DefaultSettingsRepositoryTest.kt`

- [ ] **Step 1: Write the unit test**

Create file `app/src/test/java/me/jbusdriver/modern/data/DefaultSettingsRepositoryTest.kt`:

```kotlin
package me.jbusdriver.modern.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSettingsRepositoryTest {

    private val repository = DefaultSettingsRepository()

    @Test
    fun getCurrentUrl_returnsNonNullUrl() {
        val url = repository.getCurrentUrl()
        assertTrue(url.isNotBlank())
        assertTrue(url.startsWith("http"))
    }

    @Test
    fun getAvailableUrls_returnsNonEmptyList() {
        val urls = repository.getAvailableUrls()
        assertTrue(urls.isNotEmpty())
    }

    @Test
    fun updateUrl_changesCurrentUrl() = runTest {
        val originalUrl = repository.getCurrentUrl()
        val testUrl = repository.getAvailableUrls().first { it != originalUrl }

        repository.updateUrl(testUrl)
        assertEquals(testUrl, repository.getCurrentUrl())

        // Restore original state
        repository.updateUrl(originalUrl)
        assertEquals(originalUrl, repository.getCurrentUrl())
    }
}
```

**Note:** These tests depend on static state (`JAVBusService.defaultFastUrl`). This is intentional for Phase 1 — the repository wraps existing singletons. Future phases will refactor to use SharedPreferences-based persistence with proper testability.

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.data.DefaultSettingsRepositoryTest"`
Expected: All 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/data/DefaultSettingsRepositoryTest.kt
git commit -m "test(migration): add DefaultSettingsRepository unit tests"
```

---

### Task 9: Create SettingsViewModel

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Create UiState and ViewModel**

Create file `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt`:

```kotlin
package me.jbusdriver.modern.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.jbusdriver.modern.data.SettingsRepository
import javax.inject.Inject

data class SettingsUiState(
    val baseUrl: String = "",
    val availableUrls: List<String> = emptyList(),
    val isUpdating: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            baseUrl = repository.getCurrentUrl(),
            availableUrls = repository.getAvailableUrls()
        )
    }

    fun updateUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdating = true)
            repository.updateUrl(url)
            _uiState.value = _uiState.value.copy(
                baseUrl = url,
                isUpdating = false
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsViewModel.kt
git commit -m "feat(migration): add SettingsViewModel with UiState and Hilt injection"
```

---

### Task 10: Test SettingsViewModel

**Files:**
- Create: `app/src/test/java/me/jbusdriver/modern/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write the ViewModel test**

Create file `app/src/test/java/me/jbusdriver/modern/ui/settings/SettingsViewModelTest.kt`:

```kotlin
package me.jbusdriver.modern.ui.settings

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.jbusdriver.modern.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createViewModel(repository: SettingsRepository): SettingsViewModel {
        return SettingsViewModel(repository)
    }

    @Test
    fun initialState_loadsFromRepository() {
        val repository = object : SettingsRepository {
            override fun getCurrentUrl() = "https://example.com"
            override fun getAvailableUrls() = listOf("https://example.com", "https://example.org")
            override suspend fun updateUrl(url: String) {}
        }

        val viewModel = createViewModel(repository)

        assertEquals("https://example.com", viewModel.uiState.value.baseUrl)
        assertEquals(listOf("https://example.com", "https://example.org"), viewModel.uiState.value.availableUrls)
    }

    @Test
    fun updateUrl_updatesState() = runTest(testDispatcher) {
        var currentUrl = "https://old.com"
        val repository = object : SettingsRepository {
            override fun getCurrentUrl() = currentUrl
            override fun getAvailableUrls() = listOf("https://old.com", "https://new.com")
            override suspend fun updateUrl(url: String) { currentUrl = url }
        }

        val viewModel = createViewModel(repository)
        viewModel.updateUrl("https://new.com")
        advanceUntilIdle()

        assertEquals("https://new.com", viewModel.uiState.value.baseUrl)
        assertFalse(viewModel.uiState.value.isUpdating)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests "me.jbusdriver.modern.ui.settings.SettingsViewModelTest"`
Expected: All 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/me/jbusdriver/modern/ui/settings/SettingsViewModelTest.kt
git commit -m "test(migration): add SettingsViewModel unit tests"
```

---

### Task 11: Create Compose Theme

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/theme/Color.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/theme/Type.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt`

- [ ] **Step 1: Create Color.kt**

Create file `app/src/main/java/me/jbusdriver/modern/ui/theme/Color.kt`:

```kotlin
package me.jbusdriver.modern.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFFE91E63)
val PrimaryDark = Color(0xFFC2185B)
val Accent = Color(0xFF7C4DFF)
val Background = Color(0xFFF5F5F5)
val Surface = Color(0xFFFFFFFF)
val OnPrimary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFF212121)
val OnSurface = Color(0xFF212121)
```

**Note:** Colors match the existing app's Material Design color scheme (pink primary, purple accent). These will be refined when the full Compose theme migration happens.

- [ ] **Step 2: Create Type.kt**

Create file `app/src/main/java/me/jbusdriver/modern/ui/theme/Type.kt`:

```kotlin
package me.jbusdriver.modern.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
```

- [ ] **Step 3: Create Theme.kt**

Create file `app/src/main/java/me/jbusdriver/modern/ui/theme/Theme.kt`:

```kotlin
package me.jbusdriver.modern.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Accent,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Accent,
)

@Composable
fun JBusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/theme/
git commit -m "feat(migration): add Compose Material 3 theme (JBusTheme)"
```

---

### Task 12: Create Navigation Infrastructure

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`
- Create: `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`

- [ ] **Step 1: Create NavigationKeys.kt**

Create file `app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt`:

```kotlin
package me.jbusdriver.modern.ui

object NavigationKeys {
    const val ROUTE_SETTINGS = "settings"
}
```

- [ ] **Step 2: Create Navigation.kt**

Create file `app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt`:

```kotlin
package me.jbusdriver.modern.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.jbusdriver.modern.ui.settings.SettingsScreen

@Composable
fun JBusNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavigationKeys.ROUTE_SETTINGS
    ) {
        composable(NavigationKeys.ROUTE_SETTINGS) {
            SettingsScreen()
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/NavigationKeys.kt app/src/main/java/me/jbusdriver/modern/ui/Navigation.kt
git commit -m "feat(migration): add Compose Navigation with Settings route"
```

---

### Task 13: Create SettingsScreen

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create SettingsScreen composable**

Create file `app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt`:

```kotlin
package me.jbusdriver.modern.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import me.jbusdriver.ui.activity.SettingActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL switching section
            Text(
                text = "数据源",
                style = MaterialTheme.typography.titleLarge
            )

            UrlSelector(
                currentUrl = uiState.baseUrl,
                availableUrls = uiState.availableUrls,
                isUpdating = uiState.isUpdating,
                onUrlSelected = { viewModel.updateUrl(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Bridge to legacy settings
            Text(
                text = "其他设置",
                style = MaterialTheme.typography.titleLarge
            )

            Button(
                onClick = {
                    context.startActivity(Intent(context, SettingActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("更多设置（旧版）")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UrlSelector(
    currentUrl: String,
    availableUrls: List<String>,
    isUpdating: Boolean,
    onUrlSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = currentUrl,
                onValueChange = {},
                readOnly = true,
                label = { Text("当前地址") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableUrls.forEach { url ->
                    DropdownMenuItem(
                        text = { Text(url) },
                        onClick = {
                            onUrlSelected(url)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (isUpdating) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "可用源: ${availableUrls.size} 个",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}
```

**Note:** The screen includes a "更多设置（旧版）" button that opens the legacy `SettingActivity`. This is the Strangler Fig bridge — users can still access all unmigrated settings features while the new architecture is being built out.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/settings/SettingsScreen.kt
git commit -m "feat(migration): add Compose SettingsScreen with URL selector and legacy bridge"
```

---

### Task 14: Create ModernMainActivity

**Files:**
- Create: `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt`

- [ ] **Step 1: Create ModernMainActivity**

Create file `app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt`:

```kotlin
package me.jbusdriver.modern.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import me.jbusdriver.modern.ui.theme.JBusTheme

@AndroidEntryPoint
class ModernMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JBusTheme {
                JBusNavigation()
            }
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, ModernMainActivity::class.java)
            context.startActivity(intent)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/modern/ui/ModernMainActivity.kt
git commit -m "feat(migration): add ModernMainActivity as Compose host with @AndroidEntryPoint"
```

---

### Task 15: Wire Old Navigation to New Activity

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/ui/activity/MainActivity.kt`

- [ ] **Step 1: Update the settings click handler**

In `app/src/main/java/me/jbusdriver/ui/activity/MainActivity.kt`, change line 113-116 from:

```kotlin
tvAppSetting.setOnClickListener {
    SettingActivity.start(this@MainActivity)
    drawer.closeDrawer(GravityCompat.START)
}
```

to:

```kotlin
tvAppSetting.setOnClickListener {
    me.jbusdriver.modern.ui.ModernMainActivity.start(this@MainActivity)
    drawer.closeDrawer(GravityCompat.START)
}
```

**Note:** This is the only change to existing MVP code. The old `SettingActivity` is still accessible from the new `SettingsScreen`'s "更多设置" button.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/me/jbusdriver/ui/activity/MainActivity.kt
git commit -m "feat(migration): redirect settings navigation to ModernMainActivity"
```

---

### Task 16: Build, Test, and Verify

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all unit tests**

Run: `./gradlew test`
Expected: All tests PASS (including DefaultSettingsRepositoryTest and SettingsViewModelTest)

- [ ] **Step 3: Verify installed on device**

Run: `./gradlew installDebug`
Then manually verify:
1. App launches through SplashActivity → MainActivity as before
2. Click "设置" in navigation header → opens ModernMainActivity with Compose SettingsScreen
3. URL selector shows available URLs
4. Selecting a different URL updates the current URL
5. "更多设置（旧版）" button opens the legacy SettingActivity
6. Press back → returns to MainActivity
7. All existing features (movie lists, navigation drawer, etc.) work unchanged

- [ ] **Step 4: Final commit with any fixes**

If any issues were found and fixed during verification, commit them:

```bash
git add -A
git commit -m "fix(migration): address verification issues from Phase 1 build"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Every section of the design spec (sections 1-4) maps to a task
- [x] **Placeholder scan:** No TBD, TODO, or vague instructions — all code is concrete
- [x] **Type consistency:** `SettingsRepository` interface defined in Task 6 matches usage in Tasks 7, 8, 9, 10; `SettingsUiState` fields match between Task 9 and Task 13; `NavigationKeys.ROUTE_SETTINGS` matches between Task 12 and Navigation.kt
- [x] **File paths:** All file paths are absolute under `app/src/main/java/me/jbusdriver/modern/`
- [x] **Coexistence:** Old code is only touched in Task 15 (single line change in MainActivity) and Task 3 (manifest Application class swap)
