# Network Layer Cleanup Design

## Goal

Remove dead Retrofit code and unused dependencies from the network layer, replace custom LoggerInterceptor with the official OkHttp logging interceptor, and delete the unused NetworkModule. NetClient becomes a clean OkHttp-only HTTP client.

## Current State

NetClient (`core/http/NetClient.kt`) is a 217-line `object` that mixes active OkHttp usage with dead Retrofit infrastructure:

- **Active**: `defaultFastUrl` (8 callers), `glideOkHttpClient` (2 callers), `fetchDocument()` (5 callers)
- **Dead**: `getRetrofit()`, `strConv`, `jsonConv`, `defaultXyzUrl`, `xyzHostDomains`, `isNetAvailable()` — zero callers
- **Internal-only**: `apiClient`, `fetchHtml()` — only called inside NetClient itself, unnecessarily public

NetworkModule (`data/di/NetworkModule.kt`) provides `OkHttpClient` and `Gson` via Hilt, but neither is `@Inject`-ed anywhere.

LoggerInterceptor (`core/http/LoggerInterceptor.kt`) is a 108-line custom interceptor that duplicates what the official `HttpLoggingInterceptor` provides, with worse defaults (uses `Log.e` instead of `Log.d`).

Dependencies declared but unused: `retrofit`, `converter-scalars`, `converter-gson`.

## Changes

### 1. Delete files

| File | Reason |
|------|--------|
| `core/http/LoggerInterceptor.kt` | Replaced by official `HttpLoggingInterceptor` |
| `data/di/NetworkModule.kt` | No `@Inject` callers for its providers |

### 2. Remove dependencies

From `libs.versions.toml` and `build.gradle.kts`:

- `retrofit` — dead code, no callers
- `retrofit-scalars` (converter-scalars) — dead code
- `retrofit-gson` (converter-gson) — dead code

Keep `okhttp`, `okhttp-logging`, `jsoup`, `gson`.

### 3. Simplify NetClient

**Remove:**
- `strConv`, `jsonConv` (Converter.Factory instances)
- `getRetrofit()` function
- `defaultXyzUrl`, `xyzHostDomains` (unused config)
- `isNetAvailable()` (unused utility)
- All Retrofit imports (`retrofit2.Converter`, `retrofit2.Retrofit`, etc.)

**Change visibility to private:**
- `apiClient` — only used inside `fetchHtml()`
- `fetchHtml()` — only used inside `fetchDocument()`

**Replace logging:**
- `LoggerInterceptor("OK_HTTP")` → `HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }`

### 4. NetClient public API after cleanup

Only 3 public members:

```
object NetClient {
    var defaultFastUrl: String          // site base URL config
    val glideOkHttpClient: OkHttpClient // shared client for Coil
    suspend fun fetchDocument(url, showAll): Document  // sole network entry point
}
```

### 5. Files not changed

- `data/di/DataModule.kt` — repository bindings, unaffected
- `data/di/DatabaseModule.kt` — database providers, unaffected
- All repositories — already call `NetClient.fetchDocument()` directly, no changes needed
- `AppContext.kt` — already has no network state, unaffected
- `JBusApplication.kt` — uses `NetClient.glideOkHttpClient` and `NetClient.defaultFastUrl`, unaffected

## APK size impact

Retrofit jar removal saves ~300KB from the final APK.

## Future: JSON API

When the project needs JSON API calls, add a `fetchJson()` suspend function to NetClient that deserializes with Gson. Retrofit is unnecessary until there are 5+ typed API endpoints.
