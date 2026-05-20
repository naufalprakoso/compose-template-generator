package kotlinx.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface CoroutineScope {
    val coroutineContext: CoroutineContext
}

fun CoroutineScope(context: CoroutineContext): CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = context
}

object Dispatchers {
    val Main: CoroutineContext = EmptyCoroutineContext
}

fun SupervisorJob(): CoroutineContext = EmptyCoroutineContext
fun CoroutineScope.launch(block: suspend () -> Unit) {}
