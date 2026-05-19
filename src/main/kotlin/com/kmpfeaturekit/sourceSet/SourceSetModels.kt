package com.kmpfeaturekit.sourceSet

data class SourceSetInfo(
    val name: String,
    val kotlinPath: String,
    val exists: Boolean
)

data class SourceSetScanResult(
    val moduleRoot: String,
    val sourceSets: List<SourceSetInfo>
) {
    fun has(name: String): Boolean = sourceSets.any { it.name == name && it.exists }
}
