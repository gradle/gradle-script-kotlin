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

import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CacheKeyBuilder.CacheKeySpec

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory

import org.gradle.script.lang.kotlin.KotlinBuildScript
import org.gradle.script.lang.kotlin.cache.ScriptCache

import org.gradle.script.lang.kotlin.support.loggerFor
import org.gradle.script.lang.kotlin.support.ImplicitImports
import org.gradle.script.lang.kotlin.support.KotlinBuildscriptBlock
import org.gradle.script.lang.kotlin.support.KotlinPluginsBlock
import org.gradle.script.lang.kotlin.support.compileKotlinScriptToDirectory
import org.gradle.script.lang.kotlin.support.messageCollectorFor

import org.jetbrains.kotlin.com.intellij.openapi.project.Project

import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies

import java.io.File

import kotlin.reflect.KClass


internal
class CachingKotlinCompiler(
    val scriptCache: ScriptCache,
    val implicitImports: ImplicitImports,
    val progressLoggerFactory: ProgressLoggerFactory) {

    private
    val logger = loggerFor<KotlinScriptPluginFactory>()

    private
    val cacheKeyPrefix = CacheKeySpec.withPrefix("gradle-script-kotlin")

    private
    val cacheProperties = mapOf("version" to "5")

    fun compileBuildscriptBlockOf(
        scriptFile: File,
        buildscript: String,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledScript {

        val scriptFileName = scriptFile.name
        return compileScript(cacheKeyPrefix + scriptFileName + buildscript, classPath, parentClassLoader) { cacheDir ->
            ScriptCompilationSpec(
                KotlinBuildscriptBlock::class,
                scriptFile,
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
        val compiledScript =
            compileScript(cacheKeyPrefix + scriptFileName + plugins, classPath, parentClassLoader) { cacheDir ->
                ScriptCompilationSpec(
                    KotlinPluginsBlock::class,
                    scriptFile,
                    cacheFileFor(plugins, cacheDir, scriptFileName),
                    scriptFileName + " plugins block")
            }
        return CompiledPluginsBlock(lineNumber, compiledScript)
    }

    data class CompiledPluginsBlock(val lineNumber: Int, val compiledScript: CompiledScript)

    fun compileBuildScript(
        scriptFile: File,
        classPath: ClassPath,
        parentClassLoader: ClassLoader): CompiledScript {

        val scriptFileName = scriptFile.name
        return compileScript(cacheKeyPrefix + scriptFileName + scriptFile, classPath, parentClassLoader) {
            ScriptCompilationSpec(
                KotlinBuildScript::class,
                scriptFile,
                scriptFile,
                scriptFileName)
        }
    }

    private
    fun compileScript(
        cacheKeySpec: CacheKeySpec,
        classPath: ClassPath,
        parentClassLoader: ClassLoader,
        compilationSpecFor: (File) -> ScriptCompilationSpec): CompiledScript {

        val cacheDir = cacheDirFor(cacheKeySpec + parentClassLoader) {
            val scriptClass =
                compileScriptTo(classesDirOf(baseDir), compilationSpecFor(baseDir), classPath, parentClassLoader)
            writeClassNameTo(baseDir, scriptClass.name)
        }
        return CompiledScript(classesDirOf(cacheDir), readClassNameFrom(cacheDir))
    }

    data class ScriptCompilationSpec(
        val scriptTemplate: KClass<out Any>,
        val originalFile: File,
        val scriptFile: File,
        val description: String)

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
                    classPath.asFiles,
                    parentClassLoader,
                    messageCollectorFor(logger) { path ->
                        if (path == scriptFile.path) originalFile.path
                        else path
                    })
            }
        }

    private
    fun cacheDirFor(cacheKeySpec: CacheKeySpec, initializer: PersistentCache.() -> Unit): File =
        scriptCache.cacheDirFor(cacheKeySpec, properties = cacheProperties, initializer = initializer)

    private
    fun writeClassNameTo(cacheDir: File, className: String) =
        scriptClassNameFile(cacheDir).writeText(className)

    private
    fun readClassNameFrom(cacheDir: File) =
        scriptClassNameFile(cacheDir).readText()

    private
    fun scriptClassNameFile(cacheDir: File) = File(cacheDir, "script-class-name")

    private
    fun classesDirOf(cacheDir: File) = File(cacheDir, "classes")

    private
    fun cacheFileFor(text: String, cacheDir: File, fileName: String) =
        File(cacheDir, fileName).apply {
            writeText(text)
        }

    private
    fun scriptDefinitionFromTemplate(template: KClass<out Any>) =

        object : KotlinScriptDefinition(template) {

            override fun <TF : Any> getDependenciesFor(
                file: TF,
                project: Project,
                previousDependencies: KotlinScriptExternalDependencies?): KotlinScriptExternalDependencies? =

                object : KotlinScriptExternalDependencies {
                    override val imports: Iterable<String>
                        get() = implicitImports.list
                }
        }

    private
    fun <T> withProgressLoggingFor(description: String, action: () -> T): T {
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
