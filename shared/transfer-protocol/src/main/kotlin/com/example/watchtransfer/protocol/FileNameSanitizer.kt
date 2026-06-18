package com.example.watchtransfer.protocol

object FileNameSanitizer {
    private const val MaxLength = 120
    private val illegalCharacters = Regex("""[\\/:*?"<>|]""")

    fun sanitize(rawName: String): String {
        val leafName = rawName
            .replace('\\', '/')
            .split('/')
            .lastOrNull()
            .orEmpty()
            .trim()

        val cleaned = leafName
            .replace(illegalCharacters, "_")
            .trim('.')
            .ifBlank { "received-file" }

        return trimPreservingExtension(cleaned)
    }

    private fun trimPreservingExtension(name: String): String {
        if (name.length <= MaxLength) return name

        val dotIndex = name.lastIndexOf('.')
        val extension = if (dotIndex in 1 until name.lastIndex) name.substring(dotIndex) else ""
        val baseLimit = MaxLength - extension.length
        val base = name.substring(0, baseLimit.coerceAtLeast(1))
        return base + extension
    }
}
