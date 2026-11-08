package me.jbusdriver.modern.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.jbusdriver.modern.data.repository.AppUpdateInfo
import me.jbusdriver.modern.data.repository.AppUpdateRepository
import me.jbusdriver.modern.data.settings.AppUpdateCheckStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateAutoCheckerTest {

    private val lifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used")
    }

    // checker 构造时会触碰 Dispatchers.Main.immediate（生产 scope），测试需先接管 Main
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isAutoCheckDue is due at interval boundary`() {
        assertTrue(isAutoCheckDue(0L, AUTO_CHECK_INTERVAL_MS))
        assertFalse(isAutoCheckDue(0L, AUTO_CHECK_INTERVAL_MS - 1))
        assertTrue(isAutoCheckDue(100L, 100L + AUTO_CHECK_INTERVAL_MS))
        assertFalse(isAutoCheckDue(100L, 100L + AUTO_CHECK_INTERVAL_MS - 1))
    }

    @Test
    fun `first foreground with no record checks and exposes update`() = runTest {
        val repo = FakeUpdateRepository().apply { result = updateInfo() }
        val store = FakeCheckStore()
        val checker = createChecker(repo, store, clock = { AUTO_CHECK_INTERVAL_MS + 10_000L })

        checker.onStart(lifecycleOwner)
        advanceUntilIdle()

        assertEquals(1, repo.callCount)
        assertEquals(updateInfo(), checker.pendingUpdate.value)
        assertEquals(AUTO_CHECK_INTERVAL_MS + 10_000L, store.lastSuccessAt)
    }

    @Test
    fun `cold start checks even within throttle window`() = runTest {
        val repo = FakeUpdateRepository().apply { result = updateInfo() }
        val store = FakeCheckStore().apply { lastSuccessAt = 1_000L }
        val checker = createChecker(repo, store, clock = { 1_000L + AUTO_CHECK_INTERVAL_MS - 1 })

        checker.onStart(lifecycleOwner) // 冷启动：节流窗口内也必查
        advanceUntilIdle()

        assertEquals(1, repo.callCount)
        assertEquals(updateInfo(), checker.pendingUpdate.value)
        assertEquals(1_000L + AUTO_CHECK_INTERVAL_MS - 1, store.lastSuccessAt)
    }

    @Test
    fun `background return within six hours of last success is skipped`() = runTest {
        val repo = FakeUpdateRepository()
        val store = FakeCheckStore().apply { lastSuccessAt = 1_000L }
        val checker = createChecker(repo, store, clock = { 1_000L + AUTO_CHECK_INTERVAL_MS - 1 })

        checker.onStart(lifecycleOwner) // 冷启动必查并刷新成功时间
        advanceUntilIdle()
        assertEquals(1, repo.callCount)

        checker.onStart(lifecycleOwner) // 后台回返：距刚记录的成功不足 6 小时，跳过
        advanceUntilIdle()

        assertEquals(1, repo.callCount)
        assertNull(checker.pendingUpdate.value)
        assertEquals(1_000L + AUTO_CHECK_INTERVAL_MS - 1, store.lastSuccessAt)
    }

    @Test
    fun `background return after six hours checks again and records up to date result`() = runTest {
        val repo = FakeUpdateRepository() // result null = 已是最新
        val store = FakeCheckStore()
        var now = 1_000L
        val checker = createChecker(repo, store, clock = { now })

        checker.onStart(lifecycleOwner) // 冷启动必查，记录成功时间 1_000
        advanceUntilIdle()
        assertEquals(1, repo.callCount)

        now = 1_000L + AUTO_CHECK_INTERVAL_MS // 距成功检查已满 6 小时
        checker.onStart(lifecycleOwner)
        advanceUntilIdle()

        assertEquals(2, repo.callCount)
        assertNull(checker.pendingUpdate.value)
        assertEquals(1_000L + AUTO_CHECK_INTERVAL_MS, store.lastSuccessAt)
    }

    @Test
    fun `failed check records nothing and retries next foreground`() = runTest {
        val repo = FakeUpdateRepository().apply { error = IOException("offline") }
        val store = FakeCheckStore()
        var now = AUTO_CHECK_INTERVAL_MS + 5_000L
        val checker = createChecker(repo, store, clock = { now })

        checker.onStart(lifecycleOwner)
        advanceUntilIdle()

        // 失败不推进成功时间，不弹提示
        assertEquals(0L, store.lastSuccessAt)
        assertNull(checker.pendingUpdate.value)

        // 成功时间未推进，即使同一时刻的下一次前台也允许重试
        repo.apply { error = null; result = updateInfo() }
        checker.onStart(lifecycleOwner)
        advanceUntilIdle()

        assertEquals(2, repo.callCount)
        assertEquals(updateInfo(), checker.pendingUpdate.value)
        assertEquals(AUTO_CHECK_INTERVAL_MS + 5_000L, store.lastSuccessAt)
    }

    @Test
    fun `foreground while check in flight triggers a single check`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeUpdateRepository().apply {
            result = updateInfo()
            this.gate = gate
        }
        val store = FakeCheckStore()
        val checker = createChecker(repo, store, clock = { AUTO_CHECK_INTERVAL_MS + 42L })

        checker.onStart(lifecycleOwner)
        advanceUntilIdle() // 检查被 gate 挂起，处于进行中
        assertEquals(1, repo.callCount)

        checker.onStart(lifecycleOwner) // 进行中的重复前台触发被忽略
        assertEquals(1, repo.callCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repo.callCount)
        assertEquals(updateInfo(), checker.pendingUpdate.value)
        assertEquals(AUTO_CHECK_INTERVAL_MS + 42L, store.lastSuccessAt)
    }

    @Test
    fun `dismiss clears pending update`() = runTest {
        val repo = FakeUpdateRepository().apply { result = updateInfo() }
        val store = FakeCheckStore()
        val checker = createChecker(repo, store, clock = { AUTO_CHECK_INTERVAL_MS })

        checker.onStart(lifecycleOwner)
        advanceUntilIdle()
        assertEquals(updateInfo(), checker.pendingUpdate.value)

        checker.dismiss()
        assertNull(checker.pendingUpdate.value)
    }

    private fun TestScope.createChecker(
        repository: FakeUpdateRepository,
        store: FakeCheckStore,
        clock: () -> Long,
    ): AppUpdateAutoChecker = AppUpdateAutoChecker(repository, store).apply {
        nowMs = clock
        checkScope = CoroutineScope(StandardTestDispatcher(testScheduler))
    }

    private fun updateInfo(version: String = "1.20261101") = AppUpdateInfo(
        versionName = version,
        releaseNotes = "notes",
        downloadUrl = "https://example.com/$version.apk",
        fileName = "jbus_release_v$version.apk",
        fileSizeBytes = 1024L,
    )
}

private class FakeUpdateRepository : AppUpdateRepository {
    var callCount = 0
    var result: AppUpdateInfo? = null
    var error: IOException? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun checkForUpdate(): AppUpdateInfo? {
        callCount++
        gate?.await()
        error?.let { throw it }
        return result
    }
}

private class FakeCheckStore : AppUpdateCheckStore {
    var lastSuccessAt = 0L
    var recordCount = 0

    override fun lastSuccessfulCheckAt(): Long = lastSuccessAt

    override fun recordSuccessfulCheck(atMs: Long) {
        recordCount++
        lastSuccessAt = atMs
    }
}
