# Header Long-Press Copy Design

## Problem

MovieDetailScreen 的 Header 区域使用 `SelectionContainer` 包裹所有 header 行，导致：
1. 长按选择文本时容易跨行选中多行内容，难以精确选中单条 value
2. 需要拖拽滑块来选择，操作不便利
3. 用户通常只需要复制某个 header 的完整 value，不需要部分选择

## Solution

移除 `SelectionContainer`，改为长按 header 行时弹出 `AlertDialog`，提供一键复制功能。

## Changes

**File**: `app/src/main/java/me/jbusdriver/modern/ui/detail/MovieDetailScreen.kt`

### 1. Remove SelectionContainer

将 headers 区域（当前 lines 268-297）的 `SelectionContainer` 移除，直接使用 `Column`。

### 2. Add long-press interaction per row

每个 header 的 `Row` 添加 `combinedClickable`：
- **click**: 有 link 时触发 `onHeaderClick(header)`，无 link 时无操作
- **longClick**: 设置 `selectedHeader` state 触发 AlertDialog

### 3. AlertDialog

- 标题：`header.name`（如"識別碼"、"日期"）
- 内容：`header.value`，用 `SelectionContainer` 包裹以支持弹窗内手动选择
- "复制"按钮：调用 `context.copy(header.value)` + Toast + 关闭弹窗
- "关闭"按钮：关闭弹窗

### 4. State

在 `DetailContent` 中添加：
```kotlin
var selectedHeader by remember { mutableStateOf<HeaderUiModel?>(null) }
```

## What doesn't change

- 有 link 的 header 的短按跳转逻辑不变
- 其他区域（封面、截图、演员、类别、磁力链接）不变
- `HeaderUiModel` 数据模型不变
- ViewModel 不变
