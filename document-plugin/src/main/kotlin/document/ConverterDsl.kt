package document

import org.asciidoctor.SafeMode
import org.gradle.api.provider.Property
import document.security.IncludeGuardMode
import document.validation.HtmlLinkLintMode
import document.xref.XrefValidationMode

/**
 * DSL block `converter { }` for conversion options.
 */
class ConverterDsl(
    val safeMode: Property<SafeMode>,
    val includeGuard: Property<IncludeGuardMode>,
    val xrefValidation: Property<XrefValidationMode>,
    val htmlLinkLint: Property<HtmlLinkLintMode>
)