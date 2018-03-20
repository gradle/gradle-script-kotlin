package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class KotlinSettingsScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `Settings script path is resolved relative to parent script dir`() {

        withFile("gradle/my.settings.gradle.kts", """
            apply(from = "./answer.settings.gradle.kts")
        """)

        withFile("gradle/answer.settings.gradle.kts", """
            gradle.rootProject {
                val answer by extra { "42" }
            }
        """)

        withSettings("""
            apply(from = "gradle/my.settings.gradle.kts")
        """)

        withBuildScript("""
            val answer: String by extra
            println("*" + answer + "*")
        """)

        assertThat(
            build().output,
            containsString("*42*"))
    }

    @Test
    fun `pluginManagement block cannot appear twice in settings scripts`() {

        withSettings("""
            pluginManagement {}
            pluginManagement {}
        """)

        assertThat(
            buildAndFail("help").output,
            containsString("settings.gradle.kts:3:13: Unexpected `pluginManagement` block found. Only one `pluginManagement` block is allowed per script."))
    }
}
