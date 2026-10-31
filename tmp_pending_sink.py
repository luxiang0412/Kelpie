import io

# 1) WriteLedger：可选持久化 sink（Unknown 必须跨进程可恢复）+ restore
p = "core/herdr/src/main/kotlin/dev/herdr/core/herdr/WriteLedger.kt"
s = io.open(p, encoding="utf-8").read()

s = s.replace(
    """class WriteLedger(private val clock: () -> Long = System::currentTimeMillis) {

    private val records = LinkedHashMap<String, WriteRecord>()""",
    """/**
 * 写账本持久化出口（M2-C2-V）。
 *
 * 只有【未被确认】的记录（Pending/Unknown）需要落盘：进程被杀后用户仍必须能看到
 * 「结果未知」的操作并自行确认，不能因为进程重建而消失。Accepted/Rejected 不落盘。
 */
interface PendingWriteSink {
    fun onChanged(pending: List<WriteRecord>)
}

class WriteLedger(
    private val clock: () -> Long = System::currentTimeMillis,
    private val sink: PendingWriteSink? = null,
) {

    private val records = LinkedHashMap<String, WriteRecord>()

    /** 进程重建后恢复未确认的写操作（不会被自动重放）。 */
    fun restore(pending: List<WriteRecord>) {
        pending.forEach { rec ->
            if (rec.outcome is WriteOutcome.Pending || rec.outcome is WriteOutcome.Unknown) {
                records[rec.clientRequestId] = rec
            }
        }
        notifySink()
    }

    private fun notifySink() {
        sink?.onChanged(
            records.values.filter {
                it.outcome is WriteOutcome.Pending || it.outcome is WriteOutcome.Unknown
            },
        )
    }""",
)

for old, new in [
    ('        records[id] = rec\n        return rec',
     '        records[id] = rec\n        notifySink()\n        return rec'),
    ('        records[id] = r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)\n    }',
     '        records[id] = r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)\n        notifySink()\n    }'),
    ('        records[id] = r.copy(outcome = WriteOutcome.Rejected(code, message))\n    }',
     '        records[id] = r.copy(outcome = WriteOutcome.Rejected(code, message))\n        notifySink()\n    }'),
    ('        records[id] = r.copy(outcome = WriteOutcome.Unknown(reason))\n    }',
     '        records[id] = r.copy(outcome = WriteOutcome.Unknown(reason))\n        notifySink()\n    }'),
]:
    assert old in s, old[:40]
    s = s.replace(old, new, 1)

# applyEvidence 也要通知
s = s.replace(
    """        records[id] = if (delivered) {
            r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
        } else {
            r.copy(evidence = evidence) // 仍是 Unknown
        }""",
    """        records[id] = if (delivered) {
            r.copy(outcome = WriteOutcome.Accepted, evidence = evidence)
        } else {
            r.copy(evidence = evidence) // 仍是 Unknown
        }
        notifySink()""",
)
io.open(p, "w", encoding="utf-8", newline="\n").write(s)
print("ledger ok")
