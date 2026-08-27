package document

/**
 * Logical partition of a book node — the "matter" classifier.
 *
 * A book is conventionally split into three matters:
 * - [FRONT] — preface, introduction, table of contents;
 * - [BODY] — the actual content (parts, chapters, sections);
 * - [BACK] — glossary, index, appendices.
 *
 * [Matter] is a pure DDD value object. By default a node is classified from
 * the root segment of its `ref` (`0.x` → FRONT, `9.x` → BACK, anything else →
 * BODY), which matches the FPA table-of-contents convention. The prefixes are
 * configurable so the classifier can be tuned without hard-coding a specific
 * TOC shape (see the risk noted in the DOC-BOOK-DOMAIN cadrage).
 *
 * Ink Economy Law: classification is a pure function of the `ref` string — no
 * I/O, fully deterministic and testable in isolation.
 */
enum class Matter {
    FRONT,
    BODY,
    BACK;

    companion object {

        /**
         * Classifies [ref] into a [Matter].
         *
         * @param ref the table-of-contents reference (e.g. `1.0.2.6.4`); the
         *   empty root ref is classified as [BODY]
         * @param frontPrefix root segment that marks FRONT matter (default `0`)
         * @param backPrefix root segment that marks BACK matter (default `9`)
         */
        fun classify(ref: String, frontPrefix: String = "0", backPrefix: String = "9"): Matter {
            if (ref.isEmpty()) return BODY
            return when {
                ref.startsWith("$frontPrefix.") -> FRONT
                ref.startsWith("$backPrefix.") -> BACK
                else -> BODY
            }
        }
    }
}
