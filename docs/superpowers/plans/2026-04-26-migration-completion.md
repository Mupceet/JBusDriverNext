# JBus 功能迁移补全 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 JBus 迁移后遗留的 4 处功能断路，使磁链搜索入口可用、权限处理逻辑正确、磁链源可配置、热推荐显示空状态。

**Architecture:** 4 处改动均为局部修改，不涉及架构调整。Fix 1 将 stub toast 替换为直接 Activity 跳转；Fix 2 在现有权限回调中补充结果检查；Fix 3 用 Material Dialogs multiChoice 接入已实现的 MagnetManager/Configuration；Fix 4 用空列表调用替换注释掉的网络请求。

**Tech Stack:** Kotlin、AndroidX、Material Dialogs 3.3.0 (core)、MagnetManager（`me.jbusdriver.magnet`）、Configuration（`me.jbusdriver.magnet`）

---

## 文件变更范围

| 文件 | 操作 | 说明 |
|------|------|------|
| `app/src/main/java/me/jbusdriver/ui/activity/MovieDetailActivity.kt` | 修改第 147–150 行 | toast → MagnetPagerListActivity.start() |
| `app/src/main/java/me/jbusdriver/ui/activity/SplashActivity.kt` | 修改第 57–62 行 | 补充 grantResults 检查 |
| `app/src/main/java/me/jbusdriver/ui/activity/SettingActivity.kt` | 修改第 184–186 行（loadMagNetConfig 方法体）+ 补充 imports | 接入 MagnetManager + Configuration |
| `app/src/main/java/me/jbusdriver/mvp/presenter/HotRecommendPresenterImpl.kt` | 修改第 25–60 行（loadData4Page 方法体） | 注释代码 → 空状态调用 |

---

## Task 1：Fix 1 — 磁链入口断路

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/ui/activity/MovieDetailActivity.kt:147-150`

- [ ] **Step 1：替换 toast 为 MagnetPagerListActivity.start()**

  在 `MovieDetailActivity.kt` 找到第 147–150 行：

  ```kotlin
          setOnClickListener {
              val code = movie?.code?.replace("-", " ") ?: url?.urlPath.orEmpty()
              toast("Magnet search for: $code")
          }
  ```

  替换为：

  ```kotlin
          setOnClickListener {
              val code = movie?.code?.replace("-", " ") ?: url?.urlPath.orEmpty()
              MagnetPagerListActivity.start(this@MovieDetailActivity, code, movie?.link.orEmpty())
          }
  ```

  `MagnetPagerListActivity` 已在同包（`me.jbusdriver.ui.activity`）下，无需添加 import。

- [ ] **Step 2：验证编译通过**

  ```bash
  ./gradlew compileDebugKotlin
  ```

  期望输出末尾：`BUILD SUCCESSFUL`，无编译错误。

- [ ] **Step 3：Commit**

  ```bash
  git add app/src/main/java/me/jbusdriver/ui/activity/MovieDetailActivity.kt
  git commit -m "fix: wire magnet entry to MagnetPagerListActivity instead of toast"
  ```

---

## Task 2：Fix 2 — 权限拒绝后仍继续执行

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/ui/activity/SplashActivity.kt:57-62`

- [ ] **Step 1：补充 grantResults 检查**

  在 `SplashActivity.kt` 找到第 57–62 行：

  ```kotlin
      override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
          super.onRequestPermissionsResult(requestCode, permissions, grantResults)
          if (requestCode == REQUEST_STORAGE) {
              startLoadUrls()
          }
      }
  ```

  替换为：

  ```kotlin
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

  `PackageManager` 已在文件顶部 import（`android.content.pm.PackageManager`，第 7 行），无需新增。

- [ ] **Step 2：验证编译通过**

  ```bash
  ./gradlew compileDebugKotlin
  ```

  期望输出末尾：`BUILD SUCCESSFUL`，无编译错误。

- [ ] **Step 3：Commit**

  ```bash
  git add app/src/main/java/me/jbusdriver/ui/activity/SplashActivity.kt
  git commit -m "fix: check permission grant result in SplashActivity before proceeding"
  ```

---

## Task 3：Fix 3 — 磁链源配置 UI 补全

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/ui/activity/SettingActivity.kt:184-186`（方法体 + imports）

- [ ] **Step 1：在 SettingActivity.kt 补充 imports**

  在文件顶部现有 import 块末尾（约第 44 行 `import java.util.concurrent.TimeUnit` 之后）添加：

  ```kotlin
  import me.jbusdriver.magnet.Configuration
  import me.jbusdriver.magnet.MagnetManager
  ```

- [ ] **Step 2：替换 loadMagNetConfig() 方法体**

  找到第 184–186 行：

  ```kotlin
      private fun loadMagNetConfig() {
          tvMagnetSource.text = "磁力源配置暂不可用"
      }
  ```

  替换为：

  ```kotlin
      private fun loadMagNetConfig() {
          val allKeys = MagnetManager.getLoaderKeys()
          tvMagnetSource.text = "已选 ${Configuration.getConfigKeys().size} 个磁力源"
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

  注意：`MaterialDialog` 已在文件中使用（第 22 行 import），`listItemsMultiChoice` 来自 Material Dialogs core 3.3.0，无需额外依赖。

- [ ] **Step 3：验证编译通过**

  ```bash
  ./gradlew compileDebugKotlin
  ```

  期望输出末尾：`BUILD SUCCESSFUL`，无编译错误。

- [ ] **Step 4：Commit**

  ```bash
  git add app/src/main/java/me/jbusdriver/ui/activity/SettingActivity.kt
  git commit -m "fix: implement magnet source configuration UI in SettingActivity"
  ```

---

## Task 4：Fix 4 — 热推荐显示空状态

**Files:**
- Modify: `app/src/main/java/me/jbusdriver/mvp/presenter/HotRecommendPresenterImpl.kt:25-60`

- [ ] **Step 1：替换 loadData4Page() 方法体**

  找到第 25–60 行（`loadData4Page` 方法，包含整段注释的空方法体）：

  ```kotlin
      override fun loadData4Page(page: Int) {
  /*
          RecommendService.INSTANCE.recommends(page)
                  ...（整段注释）...
  */

      }
  ```

  替换为：

  ```kotlin
      override fun loadData4Page(page: Int) {
          mView?.showLoading()
          mView?.showContents(emptyList<ILink>())
          mView?.loadMoreEnd(false)
          mView?.dismissLoading()
      }
  ```

  `ILink` 已在文件顶部 import（第 6 行 `import me.jbusdriver.mvp.bean.ILink`），无需新增。

- [ ] **Step 2：验证编译通过**

  ```bash
  ./gradlew compileDebugKotlin
  ```

  期望输出末尾：`BUILD SUCCESSFUL`，无编译错误。

- [ ] **Step 3：完整构建验证**

  ```bash
  ./gradlew assembleDebug
  ```

  期望输出末尾：`BUILD SUCCESSFUL`。确认所有 4 个 fix 在同一构建中均正常。

- [ ] **Step 4：Commit**

  ```bash
  git add app/src/main/java/me/jbusdriver/mvp/presenter/HotRecommendPresenterImpl.kt
  git commit -m "fix: show empty state in HotRecommend instead of hanging on dead network call"
  ```

---

## Self-Review Checklist

**Spec coverage：**
- Fix 1（磁链入口）→ Task 1 ✅
- Fix 2（权限处理）→ Task 2 ✅
- Fix 3（磁链源配置）→ Task 3 ✅
- Fix 4（热推荐空状态）→ Task 4 ✅

**Placeholder scan：** 无 TBD/TODO，每步均含完整代码。

**Type consistency：**
- `MagnetPagerListActivity.start(context, keyword, link)` — 与 `MagnetPagerListActivity.kt` companion 中签名一致（`Context, String, String`）✅
- `MagnetManager.getLoaderKeys(): List<String>` — 与 `MagnetManager.kt` 实现一致 ✅
- `Configuration.getConfigKeys(): MutableList<String>` — 与 `Configuration.kt` 实现一致 ✅
- `Configuration.saveMagnetKeys(keys: List<String>)` — 与 `Configuration.kt` 实现一致 ✅
- `mView?.showContents(emptyList<ILink>())` — `HotRecommendContract.HotRecommendView` 继承 `BaseView.BaseListWithRefreshView`，`showContents(List<*>)` 存在 ✅
