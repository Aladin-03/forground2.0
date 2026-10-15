package com.aladin.foreground2.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Flags inline dimensions (`16.dp`, `14.sp`) and colours (`Color(0xFF...)`, `Color.Red`) outside the
 * design system package. Use the tokens from core/designsystem instead.
 */
class InlineDesignValue(
    config: Config,
) : Rule(config) {
    override val issue =
        Issue(
            id = javaClass.simpleName,
            severity = Severity.Style,
            description = "Inline dp/sp/colour value. Use a token from core/designsystem.",
            debt = Debt.FIVE_MINS,
        )

    private val allowedPackages: List<String> by config(listOf("com.aladin.foreground2.core.designsystem"))

    private var skipFile = false

    override fun visitKtFile(file: KtFile) {
        val pkg = file.packageFqName.asString()
        skipFile = allowedPackages.any { pkg == it || pkg.startsWith("$it.") }
        super.visitKtFile(file)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (skipFile) return
        val receiver = expression.receiverExpression
        val selector = expression.selectorExpression?.text
        when {
            receiver is KtConstantExpression && selector in UNITS -> flag(expression, "dimension")
            receiver.text == "Color" && selector in NAMED_COLOURS -> flag(expression, "colour")
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (skipFile) return
        if (expression.calleeExpression?.text == "Color" && expression.valueArguments.isNotEmpty()) {
            flag(expression, "colour")
        }
    }

    private fun flag(
        element: KtElement,
        kind: String,
    ) {
        report(CodeSmell(issue, Entity.from(element), "Inline $kind ${element.text}. Use a design system token."))
    }

    private companion object {
        val UNITS = setOf("dp", "sp", "em")
        val NAMED_COLOURS =
            setOf(
                "Black",
                "DarkGray",
                "Gray",
                "LightGray",
                "White",
                "Red",
                "Green",
                "Blue",
                "Yellow",
                "Cyan",
                "Magenta",
            )
    }
}
