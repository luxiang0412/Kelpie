package io.github.luxiang0412.kelpie.core.data

import io.github.luxiang0412.kelpie.core.herdr.AgentStatus
import io.github.luxiang0412.kelpie.core.herdr.Pane
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 把 herdr 的 snapshot 写进 Room，并把缓存吐给 UI。
 * 离线时 UI 仍然能看到最后一次快照（标 stale）。
 */
class BoardRepository(private val dao: BoardDao) {

    data class CachedBoard(
        val panes: List<Pane>,
        val version: String,
        val protocol: Int,
        val fetchedAtMs: Long,
        val stale: Boolean,
        val lastError: String?,
    )

    fun observe(hostId: String, session: String): Flow<CachedBoard> =
        kotlinx.coroutines.flow.combine(
            dao.observePanes(hostId, session),
            dao.observeMeta(hostId, session),
        ) { panes, meta ->
            CachedBoard(
                panes = panes.map { it.toModel() },
                version = meta?.version ?: "",
                protocol = meta?.protocol ?: 0,
                fetchedAtMs = meta?.fetchedAtMs ?: 0,
                stale = meta?.stale ?: true,
                lastError = meta?.lastError,
            )
        }

    suspend fun replace(
        hostId: String,
        session: String,
        panes: List<Pane>,
        version: String,
        protocol: Int,
        fetchedAtMs: Long,
        stale: Boolean,
        lastError: String?,
    ) {
        val now = System.currentTimeMillis()
        dao.replaceSnapshot(
            hostId, session,
            panes.map { it.toEntity(hostId, session, now) },
            SessionMetaEntity(hostId, session, version, protocol, fetchedAtMs, stale, lastError),
        )
    }

    suspend fun markStale(hostId: String, session: String, stale: Boolean, error: String?) {
        val meta = dao.observeMeta(hostId, session)
        // 用 upsert 覆盖：读不到就直接写一条最简 meta
        dao.upsertMeta(SessionMetaEntity(hostId, session, "", 0, 0, stale, error))
    }

    private fun PaneEntity.toModel() = Pane(
        paneId = paneId,
        terminalId = terminalId,
        workspaceId = workspaceId,
        tabId = tabId,
        agent = agent,
        status = AgentStatus.fromWire(status),
        title = title,
        cwd = cwd,
        revision = revision,
        agentSessionPath = agentSessionPath,
        agentSessionId = agentSessionId,
    )

    private fun Pane.toEntity(hostId: String, session: String, now: Long) = PaneEntity(
        hostId = hostId,
        session = session,
        paneId = paneId,
        terminalId = terminalId,
        workspaceId = workspaceId,
        tabId = tabId,
        agent = agent,
        status = status.wire,
        title = title,
        cwd = cwd,
        revision = revision,
        agentSessionPath = agentSessionPath,
        agentSessionId = agentSessionId,
        updatedAtMs = now,
    )
}
