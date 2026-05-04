# Magnet Link: Open Download App + Copy

## Summary

点击磁力链接时，同时复制到剪贴板并尝试跳转到下载 App（迅雷、IDM 等）。

## Current Behavior

`MagnetItem` composable（`MovieDetailScreen.kt:664-707`）点击时仅复制到剪贴板：

```kotlin
clickable {
    context.copy(magnet.link)
    Toast.makeText(context, "已複製磁力連結", Toast.LENGTH_SHORT).show()
}
```

## New Behavior

1. 静默复制磁力链接到剪贴板（复用 `Context.copy()`）
2. 构建 `Intent(ACTION_VIEW, Uri.parse(magnetLink))`
3. `resolveActivity` 检查：
   - 有 App → `startActivity(intent)`，Toast "已複製並打開下載器"
   - 无 App → 仅 Toast "已複製磁力連結"（回退到纯复制）
4. `try-catch ActivityNotFoundException` 防护

## Scope

仅修改 `MovieDetailScreen.kt` 中的 `MagnetItem` composable，不涉及 ViewModel 或数据层。
