package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class GenerateProjectSchemaTest : AbstractIntegrationTest() {

    @Test
    fun `writes multi-project schema to gradle slash project dash schema dot json`() {

        withBuildScript("""
            plugins { java }
        """)

        build("kotlinDslAccessorsSnapshot")

        val generatedSchema =
            loadMultiProjectSchemaFrom(
                existing("gradle/project-schema.json"))

        val expectedSchema =
            mapOf(
                ":" to ProjectSchema(
                    extensions = mapOf(
                        "defaultArtifacts" to "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
                        "ext" to "org.gradle.api.plugins.ExtraPropertiesExtension",
                        "reporting" to "org.gradle.api.reporting.ReportingExtension"),
                    conventions = mapOf(
                        "base" to "org.gradle.api.plugins.BasePluginConvention",
                        "java" to "org.gradle.api.plugins.JavaPluginConvention"),
                    configurations = listOf(
                        "apiElements", "archives", "compile", "compileClasspath", "compileOnly", "default",
                        "implementation", "runtime", "runtimeClasspath", "runtimeElements", "runtimeOnly",
                        "testCompile", "testCompileClasspath", "testCompileOnly", "testImplementation",
                        "testRuntime", "testRuntimeClasspath", "testRuntimeOnly")))

        assertThat(
            generatedSchema,
            equalTo(expectedSchema))
    }
}
