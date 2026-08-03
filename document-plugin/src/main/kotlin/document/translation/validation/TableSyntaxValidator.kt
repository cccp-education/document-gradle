package document.translation.validation

import document.translation.Table
import org.slf4j.LoggerFactory

object TableSyntaxValidator {
    private val log = LoggerFactory.getLogger(TableSyntaxValidator::class.java)

    fun validate(
        table: Table,
        articleTitle: String = "",
        tableIndex: Int = 0,
    ): TableValidationResult {
        val colCount = table.cols.size

        if (colCount > 0) {
            val headerColCount = table.header.firstOrNull()?.cells?.size ?: 0
            if (headerColCount > 0 && headerColCount != colCount) {
                return TableValidationResult.Invalid(
                    articleTitle = articleTitle,
                    tableIndex = tableIndex,
                    reason = "cols count ($colCount) does not match header column count ($headerColCount)",
                )
            }
            table.body.forEachIndexed { rowIdx, row ->
                if (row.cells.size != colCount) {
                    return TableValidationResult.Invalid(
                        articleTitle = articleTitle,
                        tableIndex = tableIndex,
                        reason = "cols count ($colCount) does not match body row $rowIdx column count (${row.cells.size})",
                    )
                }
            }
        }

        val headerColCount = table.header.firstOrNull()?.cells?.size ?: 0
        if (headerColCount > 0) {
            table.body.forEachIndexed { rowIdx, row ->
                if (row.cells.size != headerColCount) {
                    return TableValidationResult.Invalid(
                        articleTitle = articleTitle,
                        tableIndex = tableIndex,
                        reason = "column count mismatch: header has $headerColCount columns, body row $rowIdx has ${row.cells.size}",
                    )
                }
            }
        }

        val allRows = table.allRows
        allRows.forEachIndexed { rowIdx, row ->
            row.cells.forEachIndexed { cellIdx, cell ->
                val cellText = cell.inline.joinToString("") {
                    when (it) {
                        is document.translation.PivotInline.Text -> it.text
                        is document.translation.PivotInline.Bold -> it.text
                        is document.translation.PivotInline.Code -> it.text
                        is document.translation.PivotInline.Link -> it.label
                        is document.translation.PivotInline.LineBreak -> ""
                    }
                }
                if (cellText.contains("|===")) {
                    return TableValidationResult.Invalid(
                        articleTitle = articleTitle,
                        tableIndex = tableIndex,
                        reason = "cell [$rowIdx,$cellIdx] contains table delimiter '|==='",
                    )
                }
            }
        }

        if (table.header.isNotEmpty()) {
            table.header.first().cells.forEachIndexed { cellIdx, cell ->
                val cellText = cell.inline.joinToString("") {
                    when (it) {
                        is document.translation.PivotInline.Text -> it.text
                        is document.translation.PivotInline.Bold -> it.text
                        is document.translation.PivotInline.Code -> it.text
                        is document.translation.PivotInline.Link -> it.label
                        is document.translation.PivotInline.LineBreak -> ""
                    }
                }
                if (cellText.isBlank()) {
                    log.warn(
                        "TableSyntaxValidator: header cell [$cellIdx] is empty in article '{}' table #{}",
                        articleTitle,
                        tableIndex,
                    )
                }
            }
        }

        return TableValidationResult.Valid
    }
}
