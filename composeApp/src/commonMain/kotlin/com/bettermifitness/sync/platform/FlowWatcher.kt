package com.bettermifitness.sync.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Cancels a [StateFlow] collection started for Swift / non-Compose UI.
 */
fun interface FlowWatcher : AutoCloseable {
    override fun close()
}

/**
 * Collects [flow] on the main dispatcher and invokes [onEach] for every value.
 * Call [FlowWatcher.close] when the Swift view disappears.
 */
fun <T> watchStateFlow(
    flow: StateFlow<T>,
    onEach: (T) -> Unit,
): FlowWatcher {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val job: Job = scope.launch {
        flow.collect { value -> onEach(value) }
    }
    return FlowWatcher {
        job.cancel()
    }
}
