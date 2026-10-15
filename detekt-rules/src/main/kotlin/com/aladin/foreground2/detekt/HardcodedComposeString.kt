package com.aladin.foreground2.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Flags string literals with visible text inside @Composable functions. UI text belongs in strings.xml.
 *
 * Annotation arguments are ignored, as are arguments to [allowedCalls] (e.g. `Modifier.testTag("x")`)
 * and functions carrying one of [ignoredAnnotations] (e.g. `@Preview`).
 */
class HardcodedComposeString(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            id = javaClass.simpleName,
            severity = Severity.Style,
            description = "Hardcoded string in a Composable. Put UI text in strings.xml and use stringResource().",
            debt = Debt.FIVE_MINS,
        )

    private val allowedCalls: List<String> by config(listOf("testTag"))
    private val ignoredAnnotations: List<String> by config(listOf("Preview"))

    override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
        super.visitStringTemplateExpression(expression)
        val hasText = expression.entries.any { it is KtLiteralStringTemplateEntry && it.text.isNotBlank() }
        if (!hasText || expression.getStrictParentOfType<KtAnnotationEntry>() != null) return

        val function = expression.getStrictParentOfType<KtNamedFunction>() ?: return
        val annotations = function.annotationEntries.mapNotNull { it.shortName?.asString() }
        if ("Composable" !in annotations || annotations.any { it in ignoredAnnotations }) return

        val call = expression.getStrictParentOfType<KtCallExpression>()
        if (call?.calleeExpression?.text in allowedCalls) return

        report(
            CodeSmell(
                issue,
                Entity.from(expression),
                "Hardcoded string ${expression.text} in @Composable ${function.name}. Use a string resource.",
            ),
        )
    }
}
