package document.translation.tree

import contracts.plantuml.PlantUmlBlock
import contracts.plantuml.PlantUmlClassifier
import contracts.plantuml.PlantUmlStrategy
import contracts.plantuml.PlantUmlSyntaxValidator as N0PlantUmlSyntaxValidator
import contracts.plantuml.SyntaxValidationResult
import document.translation.PivotBlock

/**
 * GBL-010 G-2b — derives a PlantUML leaf status from the N0 plantuml contracts.
 *
 * The status is *delegated*: `document` maps the facts produced by the plantuml
 * borough ([PlantUmlClassifier] and [N0PlantUmlSyntaxValidator]) onto the article
 * tree vocabulary. It never re-implements label extraction, classification or
 * PlantUML parsing.
 *
 * Mapping order matters:
 * 1. a blank block is [ArticleLeafStatus.MISSING] (nothing to classify);
 * 2. an N0 [SyntaxValidationResult.Invalid] is [ArticleLeafStatus.BROKEN];
 * 3. the N0 classifier [PlantUmlStrategy.PRESERVE] is
 *    [ArticleLeafStatus.TRANSLATED] (nothing pending — ink economy);
 * 4. [PlantUmlStrategy.TRANSLATE] or [PlantUmlStrategy.BORROW] is
 *    [ArticleLeafStatus.PENDING] (the plantuml leaf still has work).
 */
class PlantUmlLeafStatusResolver(
    private val validator: N0PlantUmlSyntaxValidator,
    private val classifier: PlantUmlClassifier = PlantUmlClassifier(),
) {
    fun resolve(source: PivotBlock.Source): ArticleLeafStatus {
        if (source.content.isBlank()) return ArticleLeafStatus.MISSING

        if (validator.validate(source.content) is SyntaxValidationResult.Invalid) {
            return ArticleLeafStatus.BROKEN
        }

        return when (classifier.classify(PlantUmlBlock(source.content))) {
            PlantUmlStrategy.PRESERVE -> ArticleLeafStatus.TRANSLATED
            PlantUmlStrategy.TRANSLATE,
            PlantUmlStrategy.BORROW,
            -> ArticleLeafStatus.PENDING
        }
    }
}
