package kotlinx.coroutines.flow

interface StateFlow<T>

class MutableStateFlow<T>(var value: T) : StateFlow<T>

fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this
