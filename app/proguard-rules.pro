# ============================================================
# Kotlin
# ============================================================
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ============================================================
# Gson - reflection-based serialization
# ============================================================
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Gson 2.14 ships consumer rules for TypeToken, @SerializedName, @JsonAdapter
# and adapter constructors. App rules only preserve JSON field names for data
# that is persisted, exported, or restored from cache across releases.

# Kotlin Pair is used as a GSON deserialization target (e.g. Pair<List<ActressInfo>, PageInfo>)
# and must not be obfuscated or its member names renamed.
-keep class kotlin.Pair {
    <fields>;
}
# CacheEnvelope is serialized/deserialized by GSON in cache layer
-keep class me.jbusdriver.modern.core.cache.CacheEnvelope {
    <fields>;
}
-keep class me.jbusdriver.modern.domain.model.Movie {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ActressInfo {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.Header {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.Genre {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.SearchLink {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.PageLink {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.MoviePageResult {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.MovieFilterInfo {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.PageInfo {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.MovieDetail {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ImageSample {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ActressAttrs {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ActressDetail {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.GenreGroup {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumBoardGroup {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumBanner {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumSummaryThread {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumHomeSummary {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumHomeData {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumBoard {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.LastPost {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumThread {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumThreadDetail {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.Comment {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumReply {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumTypeFilter {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ForumThreadPageResult {
    !static !transient <fields>;
}
-keep class me.jbusdriver.modern.domain.model.ContentBlock { *; }
-keep class me.jbusdriver.modern.domain.model.ContentBlock$RichText { !static !transient <fields>; }
-keep class me.jbusdriver.modern.domain.model.ContentBlock$ListBlock { !static !transient <fields>; }
-keep class me.jbusdriver.modern.domain.model.ContentBlock$Image { !static !transient <fields>; }
-keep class me.jbusdriver.modern.domain.model.ContentBlock$Quote { !static !transient <fields>; }
-keep class me.jbusdriver.modern.domain.model.ContentBlock$RestrictedNotice { !static !transient <fields>; }
-keep class me.jbusdriver.modern.domain.model.ContentBlockTypeAdapter { *; }
-keepclassmembers enum me.jbusdriver.modern.domain.model.SearchType {
    *;
}
-keepclassmembers class me.jbusdriver.modern.domain.model.TextPart {
    !static !transient <fields>;
}
-keepclassmembers class me.jbusdriver.modern.domain.model.RichParagraph {
    !static !transient <fields>;
}
-keepclassmembers class me.jbusdriver.modern.domain.model.RichList {
    !static !transient <fields>;
}
-keepclassmembers class me.jbusdriver.modern.domain.model.RichListItem {
    !static !transient <fields>;
}
-keepclassmembers enum me.jbusdriver.modern.domain.model.ForumTextSize {
    *;
}
-keepclassmembers class me.jbusdriver.modern.data.SessionCookieStore$PersistedCookie {
    !static !transient <fields>;
}

# ============================================================
# Room
# ============================================================
# Room runtime and compiler artifacts provide their own consumer rules.
-dontwarn androidx.room.paging.**

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**

# ============================================================
# Jsoup
# ============================================================
# No keep rules needed; Jsoup does not use app-side reflection here.
-dontwarn org.jsoup.**

# ============================================================
# Hilt / Dagger
# ============================================================
# Hilt and Dagger provide consumer rules through their runtime artifacts.

# ============================================================
# Compose - runtime only
# ============================================================
-dontwarn androidx.compose.**
