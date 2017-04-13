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

import org.gradle.cache.CacheRepository
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheKeyBuilder
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.script.lang.kotlin.KotlinBuildScript

import org.gradle.script.lang.kotlin.support.loggerFor
import org.gradle.script.lang.kotlin.support.ImplicitImports
import org.gradle.script.lang.kotlin.support.KotlinBuildscriptBlock
import org.gradle.script.lang.kotlin.support.KotlinPluginsBlock
import org.gradle.script.lang.kotlin.support.compileKotlinScriptToDirectory
import org.gradle.script.lang.kotlin.support.compileToDirectory
import org.gradle.script.lang.kotlin.support.messageCollectorFor

import org.jetbrains.kotlin.com.intellij.openapi.project.Project

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies

import java.io.File

import kotlin.reflect.KClass


class CachingKotlinCompiler(
    val cacheKeyBuilder: CacheKeyBuilder,
    val cacheRepository: CacheRepository,
    val progressLoggerFactory: ProgressLoggerFactory,
    val recompileScripts: Boolean) {

    private val logger = loggerFor<KotlinScriptPluginFactory>()

    private val cacheKeyPrefix = CacheKeySpec.withPrefix("gradle-script-kotlin")

    fun compileBuildscriptBlockOf(
        scriptFile: File,
        buildscript: String,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledScript {

        val scriptFileName = scriptFile.name
        return compileScript(cacheKeyPrefix + scriptFileName + buildscript, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinBuildscriptBlock::class,
                scriptFile.path,
                cacheFileFor(buildscript, cacheDir, scriptFileName),
                scriptFileName + " buildscript block")
        }
    }

    data class CompiledScript(val location: File, val className: String)

    fun compilePluginsBlockOf(
        scriptFile: File,
        lineNumberedPluginsBlock: Pair<Int, String>,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledPluginsBlock {

        val (lineNumber, plugins) = lineNumberedPluginsBlock
        val scriptFileName = scriptFile.name
        val compiledScript = compileScript(cacheKeyPrefix + scriptFileName + plugins, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinPluginsBlock::class,
                scriptFile.path,
                cacheFileFor(plugins, cacheDir, scriptFileName),
                scriptFileName + " plugins block")
        }
        return CompiledPluginsBlock(lineNumber, compiledScript)
    }

    data class CompiledPluginsBlock(val lineNumber: Int, val compiledScript: CompiledScript)

    fun compileBuildScript(
        scriptFile: File,
        script: String,
        additionalSourceFiles: List<File>,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledScript {

        val scriptFileName = scriptFile.name
        return compileScript(cacheKeyPrefix + scriptFileName + script, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinBuildScript::class,
                scriptFile.path,
                cacheFileFor(script, cacheDir, scriptFileName),
                scriptFileName,
                additionalSourceFiles)
        }
    }

    fun compileLib(sourceFiles: List<File>, classPath: ClassPath): File =
        withCacheFor(cacheKeySpecOf(sourceFiles, classPath)) {
            withProgressLoggingFor("Compiling Kotlin build script library") {
                compileToDirectory(baseDir, sourceFiles, logger, classPath.asFiles)
            }
        }

    private
    fun cacheKeySpecOf(sourceFiles: List<File>, classPath: ClassPath): CacheKeySpec {
        require(sourceFiles.isNotEmpty()) { "Expecting at least one Kotlin source file, got none." }
        return sourceFiles.fold(cacheKeyPrefix + classPath, CacheKeySpec::plus)
    }

    private
    fun compileScript(
        cacheKeySpec: CacheKeySpec,
        classPath: ClassPath,
        parentClassLoader: ClassLoader,
        compilationSpecFor: (File) -> ScriptCompilationSpec): CompiledScript {

        val cacheDir = withCacheFor(cacheKeySpec + parentClassLoader) {
            val scriptClass =
                compileScriptTo(classesDirOf(baseDir), compilationSpecFor(baseDir), classPath, parentClassLoader)
            writeClassNameTo(baseDir, scriptClass.name)
        }
        return CompiledScript(classesDirOf(cacheDir), readClassNameFrom(cacheDir))
    }

    private
    fun withCacheFor(cacheKeySpec: CacheKeySpec, initializer: PersistentCache.() -> Unit): File =
        cacheRepository
            .cache(cacheKeyFor(cacheKeySpec))
            .withProperties(mapOf("version" to "3"))
            .let { if (recompileScripts) it.withValidator { false } else it }
            .withInitializer(initializer)
            .open().run {
                close()
                baseDir
            }

    data class ScriptCompilationSpec(
        val scriptTemplate: KClass<out Any>,
        val originalFilePath: String,
        val scriptFile: File,
        val description: String,
        val additionalSourceFiles: List<File> = emptyList())

    private
    fun compileScriptTo(
        outputDir: File,
        spec: ScriptCompilationSpec,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): Class<*> =

        spec.run {
            withProgressLoggingFor(description) {
                logger.debug("Kotlin compilation classpath for {}: {}", description, classPath)
                compileKotlinScriptToDirectory(
                    outputDir,
                    scriptFile,
                    scriptDefinitionFromTemplate(scriptTemplate),
                    additionalSourceFiles,
                    classPath.asFiles,
                    parentClassLoader,
                    messageCollectorFor(logger) { path ->
                        if (path == scriptFile.path) originalFilePath
                        else path
                    })
            }
        }


    private fun cacheKeyFor(spec: CacheKeySpec) = cacheKeyBuilder.build(spec)

    private fun writeClassNameTo(cacheDir: File, className: String) =
        scriptClassNameFile(cacheDir).writeText(className)

    private fun readClassNameFrom(cacheDir: File) =
        scriptClassNameFile(cacheDir).readText()

    private fun scriptClassNameFile(cacheDir: File) = File(cacheDir, "script-class-name")

    private fun classesDirOf(cacheDir: File) = File(cacheDir, "classes")

    private fun cacheFileFor(text: String, cacheDir: File, fileName: String) =
        File(cacheDir, fileName).apply {
            writeText(text)
        }

    private fun scriptDefinitionFromTemplate(template: KClass<out Any>) =

        object : KotlinScriptDefinition(template) {

            override fun <TF : Any> getDependenciesFor(
                file: TF,
                project: Project,
                previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? =

                object : KotlinScriptExternalDependencies {
                    override val imports: Iterable<String>
                        get() = ImplicitImports.list
                }
        }

    private fun <T> withProgressLoggingFor(description: String, action: () -> T): T {
        val operation = progressLoggerFactory
            .newOperation(this::class.java)
            .start("Compiling script into cache", "Compiling $description into local build cache")
        try {
            return action()
        } finally {
            operation.completed()
        }
    }
}
