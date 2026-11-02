package dev.herdr.core.herdr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay

/**
 * 会话状态对账器（D9 / RESULTS §9.3）。
 *
 * 规则：
 * - 事件**只当失效信号**，绝不把事件载荷 patch 到快照上；
 * - 刷新**串行**；刷新期间又收到事件 → 完成后在刷一次；
 * - 每次刷新都用 `session.snapshot` 整体替换；
 * - `events_lost` → 标记 stale，重订阅由 Supervisor 负责，这里只负责重新拉快照。
 */
class SessionReconciler(
    private val scope: CoroutineScope,
    private val fetch: suspend () -> SessionSnapshot,
    private val onStaleChanged: (Boolean) -> Unit = {},
) {
    data class State(
        val panes: List<Pane> = emptyList(),
        val version: String = "",
        val protocol: Int = 0,
        val fetchedAtMs: Long = 0,
        val refreshing: Boolean = false,
        val stale: Boolean = false,
        val lastError: String? = null,
        val refreshCount: Long = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val mutex = Mutex()
    private var dirty = false
    private var drainJob: Job? = null

    /** 收到事件：只标记脏，然后触发一次串行刷新。 */
    fun onEvent(event: HerdrEvent) {
        when {
            event.type == "__started" -> markClean()
            event.type.startsWith("__error:") -> {
                val code = event.type.removePrefix("__error:")
                if (code == "events_lost") markStale("events_lost") else markDirty()
            }
            else -> markDirty()
        }
    }

    fun markDirty() {
        dirty = true
        if (drainJob?.isActive != true) {
            drainJob = scope.launch { drain() }
        }
    }

    fun markStale(reason: String) {
        _state.value = _state.value.copy(stale = true, lastError = reason)
        onStaleChanged(true)
    }

    fun markClean() {
        if (_state.value.stale) {
            _state.value = _state.value.copy(stale = false)
            onStaleChanged(false)
        }
    }

    /** 连接切换 / 手动刷新：清空脏标记并立刻刷新。 */
    fun resetForNewConnection() {
        dirty = true
        _state.value = _state.value.copy(stale = true)
        onStaleChanged(true)
        if (drainJob?.isActive != true) drainJob = scope.launch { drain() }
    }

    fun clear() {
        _state.value = State()
    }

    private suspend fun drain() {
        mutex.withLock {
            while (dirty) {
                dirty = false
                _state.value = _state.value.copy(refreshing = true)
                try {
                    val snap = fetch()
                    _state.value = _state.value.copy(
                        panes = snap.panes,
                        version = snap.version,
                        protocol = snap.protocol,
                        fetchedAtMs = snap.fetchedAtMs,
                        refreshing = false,
                        stale = false,
                        lastError = null,
                        refreshCount = _state.value.refreshCount + 1,
                    )
                    onStaleChanged(false)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(
                        refreshing = false,
                        stale = true,
                        lastError = e.message ?: e.javaClass.simpleName,
                    )
                    onStaleChanged(true)
                    // 失败后不要忙等；交给 Supervisor 的重连/退避
                    delay(500)
                }
            }
        }
    }
}
