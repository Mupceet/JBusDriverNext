# JBusDriver (Next)

基于 Jetpack Compose 全面重构的 [JavBus](https://www.javbus.com) 第三方 Android 客户端。

这是原 [Ccixyj/JBusDriver](https://github.com/Ccixyj/JBusDriver) 项目的演进版本——架构、UI 与数据层均推翻重写，仅保留对目标站点 HTML 结构的解析经验。原始代码归档于本仓库的 `archive/original` 分支以备溯源。

## 主要功能

- **影片** — 列表浏览、详情、番号搜索、磁力链接解析
- **演员** — 演员网格与作品列表
- **论坛** — 板块、帖子列表与楼层渲染
- **收藏** — 本地分类收藏，支持导入 / 导出
- 支持、镜像站点切换、主题与列表样式偏好

## 技术栈

| 领域 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + MVVM |
| 依赖注入 | Hilt |
| 网络 | OkHttp + Jsoup（含 WebView 反爬回退） |
| 数据库 | Room (KSP) |
| 缓存 | 两级缓存（内存 LRU + 磁盘），stale-while-revalidate |
| 图片 | Coil + Telephoto（缩放查看） |
| 导航 | Navigation 3 |
| 序列化 | Gson（模型）/ kotlinx-serialization（路由） |
| 偏好 | DataStore Preferences |

## 系统要求

- Android 9 (API 28) 及以上
- Java / JDK 17
- compileSdk 37

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（开启 R8 与资源压缩）
./gradlew assembleRelease
```

产物路径：`app/build/outputs/apk/<buildType>/jbus_<buildType>_v<versionName>.apk`

## 项目结构

业务代码全部位于 `app/src/main/java/me/jbusdriver/modern/`，按 `core / data / domain / ui` 分层。完整的模块说明、构建约定与代码规范见 [AGENTS.md](AGENTS.md)（协作智能体的工作准则，同时也是本项目的架构文档）。

## 声明

本项目为个人学习与技术研究用途，不存储任何受版权保护的媒体内容，所有数据均来自目标站点的公开页面。使用前请遵守当地法律法规。项目与 JavBus 官方无任何关联。
