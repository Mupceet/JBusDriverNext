package me.jbusdriver.modern.core

import me.jbusdriver.modern.KLog

fun <T, K : Any> logListDiff(
    oldItems: List<T>,
    newItems: List<T>,
    context: String,
    tag: String,
    keySelector: (T) -> K,
    describe: (T) -> String = { it.toString() },
    diffFields: (old: T, new: T) -> List<String> = { _, _ -> emptyList() }
) {
    val oldMap = oldItems.associateBy(keySelector)
    val newMap = newItems.associateBy(keySelector)
    val added = newItems.filter { keySelector(it) !in oldMap }
    val removed = oldItems.filter { keySelector(it) !in newMap }
    val changed = newItems.filter { new ->
        val old = oldMap[keySelector(new)]
        old != null && old != new
    }

    KLog.d(
        "[$context] old=${oldItems.size}, new=${newItems.size}, added=${added.size}, removed=${removed.size}, changed=${changed.size}",
        tag
    )
    if (added.isNotEmpty()) {
        KLog.d("[$context] +新增: ${added.map { describe(it) }}", tag)
    }
    if (removed.isNotEmpty()) {
        KLog.d("[$context] -移除: ${removed.map { describe(it) }}", tag)
    }
    if (changed.isNotEmpty()) {
        changed.forEach { new ->
            val old = oldMap[keySelector(new)]!!
            val diffs = diffFields(old, new)
            if (diffs.isNotEmpty()) {
                KLog.d("[$context] ~變動 key=${keySelector(new)} ${diffs.joinToString()}", tag)
            }
        }
    }
    if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
        KLog.d("[$context] 數據完全一致，無任何變化", tag)
    }
}
