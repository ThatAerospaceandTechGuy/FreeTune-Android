package com.freetune.app.playback

/** Pure list operations used by the playback queue, testable without Android. */
object QueueOps {

    fun <T> removeAt(items: List<T>, index: Int): List<T> {
        if (index !in items.indices) return items
        return items.toMutableList().apply { removeAt(index) }
    }

    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}
