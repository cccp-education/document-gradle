package document.translation.delta

data class FilesToTranslate(val paths: Set<String>)
data class FilesToPreserve(val paths: Set<String>)

data class I18nDeltaApplicationResult(
    val toTranslate: FilesToTranslate,
    val toPreserve: FilesToPreserve
)

class I18nDeltaApplier(
    private val delta: I18nDelta,
    private val existingTargetFiles: Set<String>
) {
    fun apply(): I18nDeltaApplicationResult {
        val modifiedPaths = delta.modifiedArticles.map { it.path }.toSet()
        val allSourcePaths = delta.updatedChecksums.keys

        val toTranslate = mutableSetOf<String>()
        val toPreserve = mutableSetOf<String>()

        toTranslate.addAll(modifiedPaths)

        for (path in allSourcePaths) {
            if (path in modifiedPaths) continue
            if (path in existingTargetFiles) {
                toPreserve.add(path)
            } else {
                toTranslate.add(path)
            }
        }

        for (path in existingTargetFiles) {
            if (path !in allSourcePaths) {
                toPreserve.add(path)
            }
        }

        return I18nDeltaApplicationResult(
            toTranslate = FilesToTranslate(toTranslate),
            toPreserve = FilesToPreserve(toPreserve)
        )
    }
}
