package org.gradle.kotlin.dsl.support

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat

class EmbeddedKotlinProviderTest : org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest() {

    @org.junit.Test
    fun `no extra dependencies are added to the buildscript classpath`() {

        val result = build("buildEnvironment")

        assertThat(result.output, containsString("No dependencies"))
    }

    @org.junit.Test
    fun `buildscript dependencies to embedded kotlin are resolved without an extra repository`() {

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:${org.gradle.kotlin.dsl.embeddedKotlinVersion}")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:${org.gradle.kotlin.dsl.embeddedKotlinVersion}")
                    classpath("org.jetbrains.kotlin:kotlin-compiler-embeddable:${org.gradle.kotlin.dsl.embeddedKotlinVersion}")
                }
            }
        """)

        val result = build("buildEnvironment")

        listOf("stdlib", "reflect", "compiler-embeddable").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:${org.gradle.kotlin.dsl.embeddedKotlinVersion}"))
        }
    }

    @org.junit.Test
    fun `stdlib and reflect are pinned to the embedded kotlin version`() {
        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-stdlib:1.0")
                    classpath("org.jetbrains.kotlin:kotlin-reflect:1.0")
                }
            }
        """)

        val result = build("buildEnvironment")

        listOf("stdlib", "reflect").forEach { module ->
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$module:1.0 -> ${org.gradle.kotlin.dsl.embeddedKotlinVersion}"))
        }
    }

    @org.junit.Test
    fun `compiler-embeddable is not pinned`() {
        withBuildScript("""
            buildscript {
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0")
                }
            }
        """)

        val result = buildAndFail("buildEnvironment")

        assertThat(result.output, containsString("Could not find org.jetbrains.kotlin:kotlin-compiler-embeddable:1.0"))
    }
}
