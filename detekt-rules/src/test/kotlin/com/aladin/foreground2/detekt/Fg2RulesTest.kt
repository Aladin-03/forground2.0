package com.aladin.foreground2.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Fg2RulesTest {
    private val config = TestConfig("active" to true)

    @Test
    fun `flags literal text in a Composable`() {
        val code =
            """
            @Composable
            fun Greeting() {
                Text("Hello")
            }
            """.trimIndent()
        assertEquals(1, HardcodedComposeString(config).lint(code).size)
    }

    @Test
    fun `ignores non-Composables, testTag, annotations and previews`() {
        val code =
            """
            fun tag() = "not ui"

            @Composable
            fun Screen(name: String) {
                Text(stringResource(R.string.app_name), Modifier.testTag("title"))
                Text("${'$'}name")
            }

            @Preview(name = "light")
            @Composable
            fun ScreenPreview() {
                Screen("Sample")
            }
            """.trimIndent()
        assertEquals(0, HardcodedComposeString(config).lint(code).size)
    }

    @Test
    fun `flags inline dp and colours outside the design system`() {
        val code =
            """
            package com.aladin.foreground2.feature.home

            val a = 16.dp
            val b = Color(0xFF000000)
            val c = Color.Red
            val d = Spacing.md
            """.trimIndent()
        assertEquals(3, InlineDesignValue(config).lint(code).size)
    }

    @Test
    fun `allows inline values inside the design system`() {
        val code =
            """
            package com.aladin.foreground2.core.designsystem

            val a = 16.dp
            val b = Color(0xFF000000)
            """.trimIndent()
        assertEquals(0, InlineDesignValue(config).lint(code).size)
    }
}
