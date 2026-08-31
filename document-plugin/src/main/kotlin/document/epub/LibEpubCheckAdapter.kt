package document.epub

import com.adobe.epubcheck.api.EPUBLocation
import com.adobe.epubcheck.api.EpubCheck
import com.adobe.epubcheck.api.MasterReport
import com.adobe.epubcheck.messages.Message
import com.adobe.epubcheck.messages.Severity
import java.io.File

/**
 * Adapter bridging the real `org.w3c:epubcheck` library (5.2.1) to the
 * [EpubCheckRunner] port (DOC-EPUBCHECK).
 *
 * The epubcheck API reports through a [MasterReport] callback; this adapter
 * collects every message as `ID severity: text (path:line)`, filters out
 * SUPPRESSED/USAGE/INFO severities (only WARNING/ERROR/FATAL are EPUB issues)
 * and derives the sealed result from the counts. A missing/unreadable file is
 * reported as [EpubValidationResult.Invalid] (never an exception — the port
 * contract).
 */
class LibEpubCheckAdapter : EpubCheckRunner {

    /** Collecting report — one line per epubcheck message (WARN/ERROR/FATAL). */
    private class CollectingReport : MasterReport() {
        val messages = mutableListOf<String>()

        override fun message(message: Message, location: EPUBLocation, vararg args: Any?) {
            val severity = message.severity
            if (severity == Severity.SUPPRESSED || severity == Severity.USAGE || severity == Severity.INFO) {
                return
            }
            val id = message.id
            val text = message.message.format(*args)
            val at = buildString {
                append(location.path)
                if (location.line > 0) {
                    append(":").append(location.line)
                }
            }
            messages += "$id $severity: $text ($at)"
        }

        // The MasterReport.message(MessageId, ...) overload already resolves the
        // id through the dictionary and delegates to message(Message, ...) —
        // overriding only the Message overload captures every message exactly once.

        // feature/value can be null from the Java side — nullable to avoid
        // a Kotlin NullPointerException on the callback boundary.
        override fun info(feature: String?, featureReport: com.adobe.epubcheck.util.FeatureEnum, value: String?) {
            // Informations are not EPUB issues — ignored.
        }

        override fun generate(): Int = 0

        override fun initialize() {
            // No pre-initialization needed for the in-memory collector.
        }
    }

    override fun validate(file: File): EpubValidationResult {
        if (!file.exists() || !file.isFile) {
            return EpubValidationResult.Invalid(listOf("<epub-file-missing> ${file.name}"))
        }
        val report = CollectingReport()
        return try {
            val exit = com.adobe.epubcheck.api.EpubCheck(file, report).doValidate()
            if (exit == 0 && report.messages.isEmpty()) {
                EpubValidationResult.Valid
            } else {
                EpubValidationResult.Invalid(report.messages.distinct().sorted())
            }
        } catch (e: Exception) {
            EpubValidationResult.Invalid(
                listOf("<epub-check-failed> ${e.javaClass.simpleName}: ${e.message}"),
            )
        }
    }
}