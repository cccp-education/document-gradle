package document.translation.delta

import java.io.File
import java.security.MessageDigest

object ContentChecksum {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readText().toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun computeChecksums(dir: File): Map<String, String> {
        if (!dir.exists()) return emptyMap()
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .associate { it.relativeTo(dir).path to sha256(it) }
    }
}
