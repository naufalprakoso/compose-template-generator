package org.koin.dsl

import org.koin.core.module.Module

fun module(block: Module.() -> Unit): Module = Module().apply(block)
