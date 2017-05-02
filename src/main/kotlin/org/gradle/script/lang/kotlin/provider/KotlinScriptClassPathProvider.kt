/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.script.lang.kotlin.provider

import org.gradle.script.lang.kotlin.codegen.generateApiExtensionsJar

import org.gradle.script.lang.kotlin.support.ProgressMonitor

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.SelfResolvingDependency

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactory
import org.gradle.api.internal.initialization.ClassLoaderScope

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.script.lang.kotlin.support.exportClassPathFromHierarchyOf

import org.gradle.util.GFileUtils.moveFile

import java.io.File


internal
typealias JarCache = (String, JarGenerator) -> File


internal
typealias JarGenerator = (File) -> Unit


private
typealias JarGeneratorWithProgress = (File, () -> Unit) -> Unit


internal
typealias JarsProvider = () -> Collection<File>


internal
class KotlinScriptClassPathProvider(
    val classPathRegistry: ClassPathRegistry,
    val gradleApiJarsProvider: JarsProvider,
    val jarCache: JarCache,
    val progressMonitorProvider: JarGenerationProgressMonitorProvider) {

    /**
     * Generated Gradle API jar plus supporting libraries such as groovy-all.jar and generated API extensions.
     */
    val gradleScriptKotlinApi: ClassPath by lazy {
        gradleApi + gradleApiExtensions + gradleScriptKotlinJars
    }

    val gradleApi: ClassPath by lazy {
        DefaultClassPath.of(gradleApiJarsProvider())
    }

    /**
     * Generated extensions to the Gradle API.
     */
    val gradleApiExtensions: ClassPath by lazy {
        DefaultClassPath(gradleScriptKotlinExtensions())
    }

    /**
     * gradle-script-kotlin.jar plus kotlin libraries.
     */
    val gradleScriptKotlinJars: ClassPath by lazy {
        DefaultClassPath.of(gradleScriptKotlinJars())
    }

    fun compilationClassPathOf(scope: ClassLoaderScope): ClassPath =
        gradleScriptKotlinApi + exportClassPathFromHierarchyOf(scope)

    private
    fun gradleScriptKotlinExtensions(): File =
        produceFrom("script-kotlin-extensions") { outputFile, onProgress ->
            generateApiExtensionsJar(outputFile, gradleJars, onProgress)
        }

    private
    fun produceFrom(id: String, generate: JarGeneratorWithProgress): File =
        jarCache(id) { outputFile ->
            val progressMonitor = progressMonitorFor(outputFile, 1)
            progressMonitor.use { progressMonitor ->
                generateAtomically(outputFile, { generate(it, progressMonitor::onProgress) })
            }
        }

    private
    fun generateAtomically(outputFile: File, generate: JarGenerator) {
        val tempFile = tempFileFor(outputFile)
        generate(tempFile)
        moveFile(tempFile, outputFile)
    }

    private
    fun progressMonitorFor(outputFile: File, totalWork: Int): ProgressMonitor =
        progressMonitorProvider.progressMonitorFor(outputFile, totalWork)

    private
    fun tempFileFor(outputFile: File): File =
        createTempFile(outputFile.nameWithoutExtension, outputFile.extension).apply {
            deleteOnExit()
        }

    private
    fun gradleScriptKotlinJars(): List<File> =
        gradleJars.filter {
            it.name.let { isKotlinJar(it) || it.startsWith("gradle-script-kotlin-") }
        }

    private
    val gradleJars by lazy {
        classPathRegistry.getClassPath(gradleApiNotation.name).asFiles
    }
}


internal
fun gradleApiJarsProviderFor(dependencyFactory: DependencyFactory): JarsProvider =
    { (dependencyFactory.gradleApi() as SelfResolvingDependency).resolve() }


private
fun DependencyFactory.gradleApi(): Dependency =
    createDependency(gradleApiNotation)


private
val gradleApiNotation = DependencyFactory.ClassPathNotation.GRADLE_API


private
fun isKotlinJar(name: String): Boolean =
    name.startsWith("kotlin-stdlib-")
        || name.startsWith("kotlin-reflect-")
