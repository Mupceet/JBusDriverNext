package me.jbusdriver.modern.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局下拉刷新容器。
 *
 * M3 默认指示圈锚在容器顶（y=0），毛玻璃布局下容器顶部藏在玻璃顶栏后面，
 * 指示圈会被玻璃盖住。这里通过 [indicatorInsetTop] 把指示圈整体下移到
 * 玻璃底缘（与列表 contentPaddingTop 传同值），拉动时从玻璃下缘探出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorInsetTop: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = indicatorInsetTop)
            )
        },
        content = content
    )
}
