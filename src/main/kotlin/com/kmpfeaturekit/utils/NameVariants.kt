package com.kmpfeaturekit.utils

data class NameVariants(
    val pascalCase: String,
    val camelCase: String,
    val snakeCase: String,
    val kebabCase: String
) {
    companion object {
        fun from(raw: String): NameVariants {
            val words = raw
                .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
                .split(Regex("[^A-Za-z0-9]+"))
                .filter { it.isNotBlank() }
                .map { it.lowercase() }

            val pascal = words.joinToString("") { it.replaceFirstChar(Char::uppercase) }
            val camel = pascal.replaceFirstChar(Char::lowercase)
            return NameVariants(
                pascalCase = pascal,
                camelCase = camel,
                snakeCase = words.joinToString("_"),
                kebabCase = words.joinToString("-")
            )
        }
    }
}
