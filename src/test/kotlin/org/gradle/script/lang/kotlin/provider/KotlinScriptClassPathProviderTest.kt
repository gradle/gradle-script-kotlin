package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.support.ProgressMonitor

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory.ClassPathNotation.GRADLE_API
import org.gradle.internal.classpath.ClassPath

import org.gradle.script.lang.kotlin.fixtures.TestWithTempFiles

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class KotlinScriptClassPathProviderTest : TestWithTempFiles() {

    @Test
    fun `should report progress based on the number of entries in gradle-api jar`() {

        val gradleApiJar = file("gradle-api-3.1.jar")

        val generatedKotlinExtensions = file("script-kotlin-extensions.jar")

        val kotlinExtensionsMonitor = mock<ProgressMonitor>(name = "kotlinExtensionsMonitor")
        val progressMonitorProvider = mock<JarGenerationProgressMonitorProvider> {
            on { progressMonitorFor(generatedKotlinExtensions, 1) } doReturn kotlinExtensionsMonitor
        }

        val subject = KotlinScriptClassPathProvider(
            classPathRegistry = mock { on { getClassPath(GRADLE_API.name) } doReturn ClassPath.EMPTY },
            gradleApiJarsProvider = { listOf(gradleApiJar) },
            jarCache = { id, generator -> file("$id.jar").apply(generator) },
            progressMonitorProvider = progressMonitorProvider)

        assertThat(
            subject.gradleScriptKotlinApi.asFiles,
            equalTo(listOf(gradleApiJar, generatedKotlinExtensions)))

        verifyProgressMonitor(kotlinExtensionsMonitor)
    }

    private
    fun verifyProgressMonitor(monitor: ProgressMonitor) {
        verify(monitor, times(1)).onProgress()
        verify(monitor, times(1)).close()
    }
}
