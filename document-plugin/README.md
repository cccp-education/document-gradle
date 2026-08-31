# document-gradle (New Orleans)

Gradle plugin for AsciiDoc document creation and multi-format publication
(HTML, PDF, EPUB, DocBook, ManPage) via AsciidoctorJ.

- Group / artifact: `education.cccp:document-plugin`
- Plugin id: `education.cccp.document`
- Latest published version: **0.0.15** (Maven Central + Gradle Plugin Portal)
- License: Apache 2.0

Boundary: **Document = WRITE + PUBLISH** (create, enrich, validate, convert,
publish). Codex (Brooklyn) = READ + RAG. plantuml-gradle (HTOWN) = composition
(diagram is an atomic constituent, `compileOnly`).

## Quick start

```kotlin
plugins {
    id("education.cccp.document") version "0.0.15"
}

document {
    source.set(file("src/docs/article.adoc"))
    outputDir.set(layout.buildDirectory.dir("docs/document"))
}
```

Then run `./gradlew convertDocumentToHtml` (plus `convertDocumentToPdf`,
`convertDocumentToEpub`, `convertDocumentToDocBook`, `convertDocumentToManPage`,
or the compound `bookPipeline` / `validateDocument`).

## Consumer imports — required for the guard DSL

The five guard knobs on the `converter { }` block are **typed enums**: their
imports are NOT resolved implicitly in a consumer `build.gradle.kts`. Without
them the build script fails to compile (pitfall confirmed by an external
0.0.14 consumer, S-239/S-240).

```kotlin
import document.epub.EpubValidationMode        // converter { epubCheck }
import document.security.IncludeGuardMode      // converter { includeGuard }
import document.validation.HtmlLinkLintMode    // converter { htmlLinkLint }
import document.xref.XrefValidationMode        // converter { xrefValidation }
import org.asciidoctor.SafeMode                // converter { safeMode }
```

### Example — all five guards

```kotlin
import document.epub.EpubValidationMode
import document.security.IncludeGuardMode
import document.validation.HtmlLinkLintMode
import document.xref.XrefValidationMode
import org.asciidoctor.SafeMode

plugins {
    id("education.cccp.document") version "0.0.15"
}

document {
    source.set(file("src/docs/article.adoc"))

    converter {
        includeGuard    = IncludeGuardMode.STRICT    // audit include:: traversal
        xrefValidation  = XrefValidationMode.LENIENT // audit <<id>> / xref:id[]
        htmlLinkLint    = HtmlLinkLintMode.STRICT    // audit rendered HTML href="#id"
        epubCheck       = EpubValidationMode.STRICT  // audit the EPUB artifact (epubcheck)
        safeMode        = SafeMode.SERVER            // AsciidoctorJ safe mode
    }
}
```

Severity semantics for every guard: `OFF` (default, no-op, backward-compatible),
`LENIENT` (warn + JSON report), `STRICT` (fail-fast `GradleException` after the
report is written).

## Tasks

| Task | Purpose |
|---|---|
| `generateDocument` | LLM-assisted AsciiDoc skeleton generation |
| `enrichDocument` | PlantUML blocks, images, includes, passthrough |
| `applyDocumentTemplate` | `{{variable}}` substitution (no LLM) |
| `translateDocument` / `translateDocumentBatch` | AsciiDoc translation pipeline |
| `retranslateFrontmatter` | Frontmatter re-translation |
| `convertDocumentToHtml/Pdf/Epub/DocBook/ManPage` | AsciidoctorJ backends |
| `assembleBook` / `bookPipeline` | FPA-style book assembly + full pipeline |
| `validateDocument` | Composite pre-flight: includeGuard + xref + security + htmlLint |
| `validateDocumentXref` / `validateDocumentEpub` | Dedicated xref / epubcheck audits |
| `lintHtmlDocument` / `verifyHtmlLinks` | Rendered-HTML navigability lint |
| `collectDocumentRetrieve` | N3 `metadata.json` (+ `validationStatus`) |
| `serializeDocumentConfig` / `deserializeDocumentConfig` | DSL round-trip |
| `releaseNotesGenerate` | git log → AsciiDoc/Markdown/JSON |

## Precedence

CLI (`-Pdocument.xxx`) > DSL (`document { }` block) > convention (default).

Example: `-Pdocument.epubCheck=STRICT -Pdocument.outputFileName=gone-custom`.