package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.provider.KotlinScriptTemplateClassPathModel
import org.gradle.script.lang.kotlin.resolver.KotlinBuildScriptDependenciesResolver

import org.gradle.tooling.GradleConnector

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.fixtures.customInstallation
import org.gradle.script.lang.kotlin.fixtures.withDaemonRegistry

import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.concurrent.CompositeStoppable

import org.junit.Test

import java.io.File


class KotlinScriptTemplateClassPathModelIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can load script template using classpath model`() {

        withBuildScript("")

        val model = fetchKotlinScriptTemplateClassPathModelFor(projectRoot)

        loadClasses(model.classPath,
                    KotlinBuildScript::class.qualifiedName!!,
                    KotlinBuildScriptDependenciesResolver::class.qualifiedName!!)
    }


    private
    fun fetchKotlinScriptTemplateClassPathModelFor(projectDir: File) =
        withDaemonRegistry(File("build/custom/daemon-registry")) {
            val connection = GradleConnector.newConnector()
                .forProjectDirectory(projectDir)
                .useGradleUserHomeDir(File(projectDir, "gradle-user-home"))
                .useInstallation(customInstallation())
                .connect()
            try {
                connection.getModel(KotlinScriptTemplateClassPathModel::class.java)
            } finally {
                connection.close()
            }
        }


    private
    fun loadClasses(classPath: List<File>, vararg classNames: String) {
        val loader = DefaultClassLoaderFactory().createIsolatedClassLoader(DefaultClassPath(classPath))
        try {
            classNames.forEach {
                loader.loadClass(it)
            }
        } finally {
            CompositeStoppable().add(loader).stop()
        }
    }
}
