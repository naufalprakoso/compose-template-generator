package org.koin.core.module

class Module {
    inline fun <reified T> single(noinline definition: () -> T) {}
    fun factory(definition: () -> Any?) {}
    fun <T> get(): T = error("stub")
}
