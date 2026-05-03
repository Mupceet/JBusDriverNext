# Directory Restructuring Design

## Problem

项目目录结构与依赖方向存在以下问题：
1. domain 层反向依赖 core/http（HtmlParser、Bean.kt 依赖 NetClient）
2. domain 层反向依赖 data 层（Bean.kt 依赖 LinkItem entity）
3. core/db 与 data/db 职责混淆
4. Bean.kt 混杂 DB 常量、domain 扩展、导航模型三类职责
5. entity 内嵌 JSON 序列化逻辑依赖大量 domain model
6. 小文件过多（ILink.kt 19行、ICollectCategory.kt 17行）
7. core/Global.kt 混合 JSON/URL/文件三类不相关工具

## Goal

依赖方向统一为 `ui → data → domain → core`，每个目录有单一清晰的职责。

## Changes

### 1. 依赖方向修正

#### 1a. HtmlParser.kt 移到 data 层

从 `domain/model/HtmlParser.kt` → `data/parser/HtmlParser.kt`

HtmlParser 是 HTML 解析逻辑，属于 data 层。移到 data 后，对 NetClient 的依赖方向正确（data → core）。

调用方（MovieRepository、MovieDetailRepository、SearchRepository）同属 data 层，仅需更新 import。

#### 1b. Bean.kt 拆分

| 原内容 | 目标文件 |
|---|---|
| DB 类型常量（MovieDBType 等） | `data/db/DBTypes.kt` |
| convertDBItem()、ILink.des/DBtype/uniqueKey | `data/db/LinkMappers.kt` |
| PageLink、SearchLink | `domain/model/PageLink.kt` |
| Expand_Type_* 常量 | 删除或移到使用处 |

#### 1c. Entity 序列化逻辑外移

History.kt 的 `getLinkItem()` 和 LinkItem.kt 的 `getLinkValue()` 移到 `data/db/LinkMappers.kt`。Entity 只保留纯 Room 字段定义。

#### 1d. urlHost/urlPath 移到 domain 层

从 `core/Global.kt` 移到 `domain/model/UrlExt.kt`。这两个扩展只做字符串解析，不依赖基础设施。

MovieDetail.kt 的 checkUrl() 改为从本地 domain/model/UrlExt.kt 导入。

### 2. 目录职责整理

#### 2a. core/db/ 合并到 data/db/

`core/db/SDCardDatabaseContext.kt` → `data/db/SDCardDatabaseContext.kt`

删除空的 `core/db/` 目录。

#### 2b. Magnet 数据模型移到 domain

`data/magnet/Magnet.kt` → `domain/model/Magnet.kt`

Magnet 是纯数据模型（实现 ILink），属于 domain 层。MagnetManager、IMagnetLoader、loaders/ 保持在 data/magnet/。

#### 2c. domain/model/ 小文件合并

| 合并后 | 原文件 |
|---|---|
| `domain/model/ILink.kt` | ILink.kt + ICollectCategory.kt |
| `domain/model/PageLink.kt` | 原 Bean.kt 中的 PageLink + SearchLink |

### 3. core 层精简

#### 3a. Global.kt 拆分

| 原内容 | 目标文件 |
|---|---|
| GSON 实例 + fromJson 扩展 | `core/GsonExt.kt` |
| urlHost、urlPath | `domain/model/UrlExt.kt` |
| createDir() | `core/FileUtil.kt` |

#### 3b. 清理

- 删除 `core/Global.kt`（已拆分）
- 删除 `domain/model/Bean.kt`（已拆分）
- 删除 `domain/model/ICollectCategory.kt`（已合并）
- 确认 `data/remote/` 目录状态，清理空目录

## Target Structure

```
core/
  GsonExt.kt          ← 原 Global.kt GSON 部分
  FileUtil.kt         ← 原 Global.kt createDir
  BaseExtension.kt    ← 不变
  CacheLoader.kt      ← 不变
  FileCache.kt        ← 保持
  JBusManager.kt      ← 不变
  http/
    NetClient.kt      ← 不变

domain/model/
  ILink.kt            ← 合并 ICollectCategory
  Movie.kt            ← 不变
  MovieDetail.kt      ← checkUrl 改用本地 UrlExt
  MoviePageResult.kt  ← 不变
  Magnet.kt           ← 从 data/magnet 移入
  PageLink.kt         ← 合并 PageLink + SearchLink
  Category.kt         ← 不变
  SearchType.kt       ← 不变
  DataSourceType.kt   ← 不变
  UrlExt.kt           ← urlHost/urlPath 从 core 移入

data/
  parser/
    HtmlParser.kt     ← 从 domain/model 移入
  db/
    DB.kt             ← 不变
    DBTypes.kt        ← 原 Bean.kt DB 常量
    LinkMappers.kt    ← 原 Bean.kt + entity 转换逻辑
    SDCardDatabaseContext.kt ← 从 core/db 移入
    JBusDatabase.kt   ← 不变
    CollectDatabase.kt ← 不变
    dao/              ← 不变
    entity/           ← 移除序列化逻辑
  magnet/
    MagnetManager.kt  ← 不变
    IMagnetLoader.kt  ← 不变
    loaders/          ← 不变
  remote/             ← 确认 Retrofit 接口位置
  *.Repository.kt     ← 不变
  di/                 ← 不变

ui/                   ← 不变
```

## What doesn't change

- ui/ 层所有文件不变（仅 import 路径可能变化）
- data/di/ 不变（DatabaseModule、DataModule 的 import 路径随移动更新）
- data/remote/ 内容不变（仅确认位置）
- 仓库接口和实现不变
- Hilt DI 模式不变
- Compose Navigation 不变
