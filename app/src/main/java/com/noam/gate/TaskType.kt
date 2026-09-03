package com.noam.gate

/** What the gate asks of you before it will show the two buttons. */
enum class TaskType(val key: String) {
    BREATH("breath"),
    MATH("math"),
    PSALM("psalm");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: BREATH
    }
}

/**
 * One thing to do on the gate screen. Each implementation owns its own group of
 * views and calls back exactly once, when it is satisfied.
 */
interface GateTask {
    fun start()
    fun cancel()
}
