# Replace WebView Magnet Loader with Direct HTTP AJAX

## Summary

用两次 HTTP 请求替换 WebView 方案获取磁力链接：先从详情页提取参数，再直接调用 AJAX 接口。

## Current Approach (WebView)

`DefaultLoaderImpl` → `WebViewHtmlContentLoader`：
- 创建 WebView 实例，加载完整页面
- 注入轮询 JS 等待 `#magnet-table` 出现（最多 15 秒）
- CountDownLatch 阻塞线程（30 秒超时）
- 内存开销大、速度慢、依赖主线程

## New Approach (Direct HTTP)

### 步骤

1. 从已获取的详情页 HTML 中正则提取 `gid`、`uc`、`img`
2. 构造 AJAX URL：`{base}/ajax/uncledatoolsbyajax.php?gid={gid}&lang=zh&img={img}&uc={uc}&floor={random}`
3. OkHttp GET 请求（Referer + 已有 Cookie 由 `EXIST_MAGNET_INTERCEPTOR` 注入）
4. Jsoup 解析返回的 HTML 片段中 `#magnet-table`

### 参数提取

详情页 HTML 包含内联 JS：
```javascript
var gid = 30100637207;
var uc = 0;
var img = "https://pics.javbus.info/cover/59pc_b.jpg";
```

正则模式：`var\s+(gid|uc|img)\s*=\s*["']?([^"';\s]+)`

### 文件变更

| 文件 | 操作 |
|------|------|
| `DefaultLoaderImpl.kt` | 重写：WebView → OkHttp + 正则 |
| `WebViewHtmlContentLoader.kt` | 删除 |

### 性能对比

| 指标 | WebView | Direct HTTP |
|------|---------|-------------|
| 耗时 | 15+ 秒 | < 2 秒 |
| 内存 | WebView 实例 | 无额外开销 |
| 线程 | 需主线程 + CountDownLatch | 纯 suspend 协程 |
