package com.aladin.foreground2.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class Fg2RuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "fg2"

    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                HardcodedComposeString(config),
                InlineDesignValue(config),
            ),
        )
}
