package document

/**
 * Configurable classification policy for the logical partitions of a book.
 *
 * [MatterPolicy] is the DDD value object introduced by DOC-BOOK-MATTER (S-258
 * code-review B3) to replace the blind `0.x` / `9.x` convention hard-coded in
 * [Matter.classify]: the front and back matter roots are an *explicit*,
 * injectable mapping, so a book whose table of contents has no such roots (the
 * real scanned-content TOC only carries `1..4`) is not permanently mis-classified.
 *
 * A policy also *derives the requirement*: a book only requires a FRONT (resp.
 * BACK) section when the policy actually declares a root for it ([requiresFront]
 * / [requiresBack]). This is what removes the permanent false positive of rule
 * S3 in [BookValidator.validateStructure] — the validator enforces a matter only
 * when the policy asks for it.
 *
 * [DEFAULT] keeps the historical `0` / `9` convention (backward compatible);
 * [NONE] classifies everything as [Matter.BODY] and requires nothing, which is
 * the policy for a TOC that carries no explicit matter marker at all.
 *
 * Ink Economy Law: classification is a pure function of the `ref` and the
 * policy — no I/O, fully deterministic and testable in isolation.
 *
 * @property frontRoots root segments of the `ref` that mark FRONT matter
 * @property backRoots root segments of the `ref` that mark BACK matter
 */
data class MatterPolicy(
    val frontRoots: Set<String> = setOf("0"),
    val backRoots: Set<String> = setOf("9"),
) {

    init {
        require(frontRoots.intersect(backRoots).isEmpty()) {
            "front and back roots must not overlap, got: ${frontRoots.intersect(backRoots)}"
        }
    }

    /** `true` when the policy declares at least one FRONT root. */
    val requiresFront: Boolean get() = frontRoots.isNotEmpty()

    /** `true` when the policy declares at least one BACK root. */
    val requiresBack: Boolean get() = backRoots.isNotEmpty()

    /**
     * Classifies [ref] into a [Matter] according to this policy.
     *
     * The root segment is the part of [ref] before the first dot (`5` for
     * `5.1.2`, and the whole string when [ref] has no dot). An empty [ref] is
     * always [Matter.BODY].
     */
    fun classify(ref: String): Matter {
        if (ref.isEmpty()) return Matter.BODY
        val root = ref.substringBefore('.')
        return when {
            root in frontRoots -> Matter.FRONT
            root in backRoots -> Matter.BACK
            else -> Matter.BODY
        }
    }

    companion object {
        /** Historical `0` / `9` convention — backward compatible. */
        val DEFAULT: MatterPolicy = MatterPolicy()

        /**
         * Classifies everything as [Matter.BODY] and requires no matter — the
         * policy for a book whose table of contents carries no matter marker.
         */
        val NONE: MatterPolicy = MatterPolicy(frontRoots = emptySet(), backRoots = emptySet())

        /**
         * Builds an explicit policy from the given front and back roots
         * (named factory mirroring the constructor for readability at call
         * sites that only care about the roots).
         */
        fun of(frontRoots: Set<String>, backRoots: Set<String>): MatterPolicy =
            MatterPolicy(frontRoots = frontRoots, backRoots = backRoots)

        /**
         * Derives a policy from a real table of contents.
         *
         * This is the fix for the permanent false positive of rule S3: the
         * legacy [DEFAULT] convention blindly requires a `0.x` FRONT and a
         * `9.x` BACK, but a real scanned-content TOC may declare neither (the
         * pilot corpus only carries roots `1..4`). [derive] adopts the
         * convention roots **only when the TOC actually declares them**, so the
         * derived policy requires a matter if and only if the book genuinely
         * carries one.
         *
         * An empty [sections] list yields [NONE] (nothing required).
         */
        fun derive(sections: List<BookSection>): MatterPolicy {
            if (sections.isEmpty()) return NONE
            val roots = sections.map { it.ref.substringBefore('.') }.toSet()
            val front = roots.intersect(DEFAULT.frontRoots)
            val back = roots.intersect(DEFAULT.backRoots)
            return MatterPolicy(frontRoots = front, backRoots = back)
        }

        /**
         * Resolves a [MatterPolicy] for [mode] against the real [sections].
         *
         * The single entry point the Gradle wiring uses to turn the DSL/CLI
         * [MatterPolicyMode] knob into a domain policy.
         */
        fun forMode(mode: MatterPolicyMode, sections: List<BookSection>): MatterPolicy =
            when (mode) {
                MatterPolicyMode.DERIVED -> derive(sections)
                MatterPolicyMode.LEGACY -> DEFAULT
                MatterPolicyMode.NONE -> NONE
            }
    }
}
