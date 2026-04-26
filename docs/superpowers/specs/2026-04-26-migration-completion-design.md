# JBus 功能迁移补全设计文档

**日期**: 2026-04-26  
**状态**: 已审批，待实现

---

## 背景

JBus 项目从 JBusDriver 迁移而来，目标是去掉复杂的多模块解耦架构（CC 组件通信框架、Phantom 热插件系统、SQLBrite），整合为单模块工程，并升级技术栈（RxJava3、AndroidX、Room）。

迁移后项目可编译，但存在 4 处功能断路或残缺，需在进入 UI 优化阶段前补全。

---

## 迁移状态总结

### 已完整迁移（无需处理）

- 所有 MVP 层（Presenter / Contract / Model）
- 数据库层（SQLBrite → Room，含 DAOs 和 Entities）
- 磁链模块 UI（`MagnetPagerListActivity` / `MagnetPagersFragment` / `MagnetListFragment`）
- 16 个磁链加载器（Phantom 服务 → `MagnetManager` 直接调用）
- 收藏、历史、类别、女优、搜索等功能
- 插件管理 UI（CC/Phantom 依赖已整体移除，显示"无插件信息"为正确行为）

### 需补全的 4 处问题

---

## Fix 1：磁链入口断路

**文件**: `app/src/main/java/me/jbusdriver/ui/activity/MovieDetailActivity.kt`  
**问题**: `layout_load_magnet` 视图的点击事件被替换为 `toast(...)`，未跳转磁链搜索界面。  
**根因**: 原代码通过 CC 框架调用 `ComponentMagnet.show()`，迁移时未替换为直接调用。

**修复**:

```kotlin
// 修改前（约 147-150 行）
setOnClickListener {
    val code = movie?.code?.replace("-", " ") ?: url?.urlPath.orEmpty()
    toast("Magnet search for: $code")
}

// 修改后
setOnClickListener {
    val code = movie?.code?.replace("-", " ") ?: url?.urlPath.orEmpty()
    MagnetPagerListActivity.start(this@MovieDetailActivity, code, movie?.link.orEmpty())
}
```

需补充 import：`import me.jbusdriver.ui.activity.MagnetPagerListActivity`

---

## Fix 2：权限拒绝后仍继续执行

**文件**: `app/src/main/java/me/jbusdriver/ui/activity/SplashActivity.kt`  
**问题**: `onRequestPermissionsResult` 不检查 `grantResults`，权限被用户拒绝后仍调用 `startLoadUrls()`，逻辑有误。  
**说明**: `WRITE_EXTERNAL_STORAGE` 在 API 29+ 已弱化，权限拒绝不应阻断启动，仅影响 SD 卡备份功能，应给出提示后继续。

**修复**:

```kotlin
// 修改前（约 57-62 行）
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_STORAGE) {
        startLoadUrls()
    }
}

// 修改后
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_STORAGE) {
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("存储权限未授予，备份功能将不可用")
        }
        startLoadUrls()
    }
}
```

---

## Fix 3：磁链源配置 UI 补全

**文件**: `app/src/main/java/me/jbusdriver/ui/activity/SettingActivity.kt`  
**问题**: `loadMagNetConfig()` 仅显示静态文字"磁力源配置暂不可用"，未接入已实现的 `Configuration` 和 `MagnetManager`。

**基础设施**（已存在，可直接使用）:
- `MagnetManager.getLoaderKeys()` — 返回全部可用 loader key 列表
- `Configuration.getConfigKeys()` — 返回当前已选 loader key 列表
- `Configuration.saveMagnetKeys(keys)` — 保存用户选择

**修复**:

```kotlin
private fun loadMagNetConfig() {
    val allKeys = MagnetManager.getLoaderKeys()
    val selectedKeys = Configuration.getConfigKeys()
    tvMagnetSource.text = "已选 ${selectedKeys.size} 个磁力源"
    tvMagnetSource.setOnClickListener {
        val currentSelected = Configuration.getConfigKeys()
        val initialSelection = currentSelected
            .mapNotNull { allKeys.indexOf(it).takeIf { i -> i >= 0 } }
            .toIntArray()
        MaterialDialog(this).show {
            title(text = "选择磁力源")
            listItemsMultiChoice(
                items = allKeys,
                initialSelection = initialSelection
            ) { _, indices, _ ->
                val selected = indices.map { allKeys[it] }
                Configuration.saveMagnetKeys(selected)
                tvMagnetSource.text = "已选 ${selected.size} 个磁力源"
            }
            positiveButton(text = "确定")
            negativeButton(text = "取消")
        }
    }
}
```

需补充 imports：
- `import me.jbusdriver.magnet.Configuration`
- `import me.jbusdriver.magnet.MagnetManager`

---

## Fix 4：热推荐显示空状态

**文件**: `app/src/main/java/me/jbusdriver/mvp/presenter/HotRecommendPresenterImpl.kt`  
**问题**: `loadData4Page()` 整体注释掉，调用后无任何响应，UI 停留在加载状态。  
**决策**: 保留菜单入口，进入后显示空状态（不做网络请求，依赖 BRVAH 空视图）。

**修复**:

```kotlin
// 修改前（约 25-60 行）
override fun loadData4Page(page: Int) {
    /* ... 整段注释 ... */
}

// 修改后
override fun loadData4Page(page: Int) {
    mView?.showLoading()
    mView?.showContents(emptyList<ILink>())
    mView?.loadMoreEnd(false)
    mView?.dismissLoading()
}
```

---

## 迁移原则（约束）

1. 不调整非 UI 部分的逻辑代码，除非版本升级导致 API 差异
2. 每阶段确保工程可正常编译
3. 架构优化建议记录文档，不在本阶段实施

---

## 架构优化建议（记录，暂不实施）

以下为迁移过程中发现的可改进点，留待后续 UI 优化阶段讨论：

- **`SplashActivity` 权限逻辑**：API 33+ 已移除 `WRITE_EXTERNAL_STORAGE`，可考虑整体去掉权限申请，改用 `MediaStore` 或应用专属目录做备份
- **`HotRecommend` 功能**：依赖的第三方推荐接口已下线，长期可完全移除该菜单项
- **磁链加载器并发**：`MagnetManager` 当前是同步调用，可考虑并发加载多个 loader 提升速度
- **`CacheLoader.acache` (ACache.java)**：仍是 Java 文件，可在后续 Kotlin 化时一并处理

---

## 实现范围

| Fix | 文件 | 改动量 |
|-----|------|--------|
| Fix 1：磁链入口 | `MovieDetailActivity.kt` | ~3 行 |
| Fix 2：权限处理 | `SplashActivity.kt` | ~4 行 |
| Fix 3：磁链源配置 | `SettingActivity.kt` | ~20 行 |
| Fix 4：热推荐空状态 | `HotRecommendPresenterImpl.kt` | ~4 行 |
