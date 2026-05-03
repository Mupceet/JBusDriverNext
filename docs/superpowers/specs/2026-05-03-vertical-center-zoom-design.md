# Vertical Center Zoom Design

## Problem

ImageViewScreen 使用 Telephoto 的 `zoomable()` modifier，双指缩放和双击缩放以触控中心为锚点，而预期行为是缩放时始终以屏幕纵向中心为锚点（保持垂直居中）。

## Solution

移除 Telephoto 依赖，实现自定义 `Modifier.verticalCenterZoom()`，以屏幕纵向中心为缩放锚点，并处理 HorizontalPager 手势冲突。

## Changes

**File**: `app/src/main/java/me/jbusdriver/modern/ui/image/ImageViewScreen.kt`

### 1. ZoomState

`rememberZoomState()` 管理：
- `scale: Float` — 当前缩放比例，范围 [1f, 3f]
- `offsetX: Float` / `offsetY: Float` — 平移偏移
- `Animatable` 驱动双击缩放动画

### 2. Modifier.verticalCenterZoom(state)

通过 `Modifier.pointerInput` 处理：
- **双指缩放**：`detectTransformGestures` 跟随手势，锚点为屏幕纵向中心
- **双击切换**：在 1x 和 2.5x 之间切换，带 `animateFloatTo` 动画
- **平移边界**：缩放后平移不超过图片可视区域边缘，无回弹
- 变换通过 `Modifier.graphicsLayer { scaleX; scaleY; translationX; translationY }` 应用

### 3. HorizontalPager 手势冲突

- `userScrollEnabled = (state.scale == 1f)` 绑定 PagerState
- `scale > 1` 时：水平手势平移图片；平移到边缘后，继续滑动 → 正常翻页
- `scale == 1` 时：水平手势正常翻页

### 4. 移除 Telephoto

- 删除 `telephoto-zoomable-image` 依赖
- 删除相关 import

## What doesn't change

- `AsyncImage` + `ContentScale.Fit`
- TopAppBar 页码指示器
- 导航参数 `images`, `startIndex`, `onBack`
