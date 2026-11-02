package dev.herdr.core.data.runtime

import dev.herdr.core.herdr.t

import dev.herdr.core.data.Approvals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 一条结构化审批（文件 IPC，带 nonce；不是读屏猜出来的）。 */
data class ApprovalItem(val hostId: String, val hostLabel: String, val request: Approvals.Request)

/** 审批结果：写入成功 ≠ agent 已经继续，只代表回答文件已落盘。 */
sealed interface ApprovalOutcome {
    data class Written(val allow: Boolean) : ApprovalOutcome
    data class Failed(val reason: String) : ApprovalOutcome
    data object Expired : ApprovalOutcome
}

/**
 * 轮询各主机沙箱里的审批目录（只在 App 前台时跑，3 秒一次）。
 * 目录不存在的主机直接返回空列表，不报错。
 */
class ApprovalCenter(
    private val fleet: Fleet,
    private val scope: CoroutineScope,
    private val dirFor: (user: String) -> String = { u -> "/home/$u/spike-sandbox/approvals" },
) {
    private val _items = MutableStateFlow<List<ApprovalItem>>(emptyList())
    val items: StateFlow<List<ApprovalItem>> = _items.asStateFlow()

    private val _audit = ArrayList<String>()
    val audit: List<String> get() = synchronized(_audit) { _audit.toList() }

    private var job: Job? = null

    fun setActive(active: Boolean) {
        if (active && job?.isActive != true) {
            job = scope.launch {
                while (isActive) {
                    pollOnce()
                    delay(POLL_MS)
                }
            }
        } else if (!active) {
            job?.cancel(); job = null
        }
    }

    suspend fun pollOnce() {
        val all = fleet.home.value.hosts.flatMap { h ->
            val cfg = fleet.config(h.hostId) ?: return@flatMap emptyList()
            val conn = fleet.connection(h.hostId) ?: return@flatMap emptyList()
            runCatching { Approvals(conn, dirFor(cfg.user)).poll() }.getOrDefault(emptyList())
                .filter { !it.expired }
                .map { ApprovalItem(h.hostId, h.label, it.copy(hostId = h.hostId)) }
        }
        _items.value = all
    }

    suspend fun answer(item: ApprovalItem, allow: Boolean): ApprovalOutcome {
        if (item.request.expired) {
            drop(item)
            return ApprovalOutcome.Expired
        }
        val cfg = fleet.config(item.hostId) ?: return ApprovalOutcome.Failed(t("主机已移除", "Host was removed"))
        val conn = fleet.connection(item.hostId) ?: return ApprovalOutcome.Failed(t("主机未连接", "Host not connected"))
        val ok = runCatching { Approvals(conn, dirFor(cfg.user)).answer(item.request, allow) }.getOrDefault(false)
        synchronized(_audit) {
            _audit += "${System.currentTimeMillis()} ${item.hostId}/${item.request.requestId} " +
                "decision=${if (allow) "allow" else "deny"} written=$ok nonce=${item.request.nonce.take(6)}"
        }
        if (ok) drop(item)
        return if (ok) ApprovalOutcome.Written(allow) else ApprovalOutcome.Failed(t("回答写入失败", "Failed to write the answer"))
    }

    private fun drop(item: ApprovalItem) =
        _items.update { l -> l.filterNot { it.hostId == item.hostId && it.request.requestId == item.request.requestId } }

    companion object { const val POLL_MS = 3_000L }
}
