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

package org.gradle.kotlin.dsl.tooling.builders


import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectInternal

import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.accessors.AccessorsClassPath
import org.gradle.kotlin.dsl.accessors.accessorsClassPathFor
import org.gradle.kotlin.dsl.provider.KotlinScriptClassPathProvider
import org.gradle.kotlin.dsl.provider.ClassPathModeExceptionCollector
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf
import org.gradle.kotlin.dsl.resolver.SourcePathProvider
import org.gradle.kotlin.dsl.resolver.SourceDistributionResolver
import org.gradle.kotlin.dsl.resolver.kotlinBuildScriptModelTarget
import org.gradle.kotlin.dsl.support.ImplicitImports
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.provider.model.ToolingModelBuilder

import java.io.File
import java.io.Serializable

import kotlin.coroutines.experimental.buildSequence

import kotlin.reflect.KClass


private
class KotlinBuildScriptModelParameter(val scriptPath: String?)


private
data class StandardKotlinBuildScriptModel(
    override val classPath: List<File>,
    override val sourcePath: List<File>,
    override val implicitImports: List<String>,
    override val exceptions: List<Exception>) : KotlinBuildScriptModel, Serializable


internal
object KotlinBuildScriptModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean =
        modelName == "org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel"

    override fun buildAll(modelName: String, modelRequestProject: Project): KotlinBuildScriptModel =
        scriptModelBuilderFor(modelRequestProject, requestParameterOf(modelRequestProject))
            .buildModel()

    private
    fun requestParameterOf(modelRequestProject: Project) =
        KotlinBuildScriptModelParameter(
            modelRequestProject.findProperty(kotlinBuildScriptModelTarget) as? String)

    private
    fun scriptModelBuilderFor(modelRequestProject: Project, parameter: KotlinBuildScriptModelParameter) =
        when {
            parameter.noScriptPath   -> projectScriptModelBuilder(modelRequestProject)
            parameter.settingsScript -> settingsScriptModelBuilder(modelRequestProject)
            else                     -> resolveScriptModelBuilderFor(parameter.scriptFile!!, modelRequestProject)
        }

    private
    fun resolveScriptModelBuilderFor(scriptFile: File, modelRequestProject: Project) =
        projectFor(scriptFile, modelRequestProject)
            ?.let { projectScriptModelBuilder(it) }
            ?: defaultScriptModelBuilder(modelRequestProject)

    private
    fun projectFor(scriptFile: File, modelRequestProject: Project) =
        modelRequestProject.allprojects.find { it.buildFile == scriptFile }
}


private
fun settingsScriptModelBuilder(project: Project) = project.run {
    KotlinScriptTargetModelBuilder(
        type = Settings::class,
        project = project,
        scriptClassPath = settings.scriptCompilationClassPath,
        sourceLookupScriptHandlers = listOf(settings.buildscript))
}


private
fun projectScriptModelBuilder(project: Project) =
    KotlinScriptTargetModelBuilder(
        type = Project::class,
        project = project,
        scriptClassPath = project.scriptCompilationClassPath,
        accessorsClassPath = { classPath -> accessorsClassPathFor(project, classPath) },
        sourceLookupScriptHandlers = project.hierarchy.map { it.buildscript }.toList())


private
fun defaultScriptModelBuilder(project: Project) =
    KotlinScriptTargetModelBuilder(
        type = Project::class,
        project = project,
        scriptClassPath = project.defaultScriptCompilationClassPath,
        sourceLookupScriptHandlers = listOf(project.buildscript))


private
data class KotlinScriptTargetModelBuilder<T : Any>(
    val type: KClass<T>,
    val project: Project,
    val scriptClassPath: ClassPath,
    val accessorsClassPath: (ClassPath) -> AccessorsClassPath = { AccessorsClassPath.empty },
    val sourceLookupScriptHandlers: List<ScriptHandler>) {

    fun buildModel(): KotlinBuildScriptModel {
        val accessorsClassPath = accessorsClassPath(scriptClassPath)
        val classpathSources = sourcePathFor(sourceLookupScriptHandlers)
        val classPathModeExceptionCollector = project.serviceOf<ClassPathModeExceptionCollector>()
        return StandardKotlinBuildScriptModel(
            (scriptClassPath + accessorsClassPath.bin).asFiles,
            (gradleSource() + classpathSources + accessorsClassPath.src).asFiles,
            implicitImports,
            classPathModeExceptionCollector.exceptions)
    }

    private
    fun gradleSource() =
        SourcePathProvider.sourcePathFor(
            scriptClassPath, rootDir, gradleHomeDir, SourceDistributionResolver(project))

    val gradleHomeDir
        get() = project.gradle.gradleHomeDir

    val rootDir
        get() = project.rootDir

    val implicitImports
        get() = project.scriptImplicitImports
}


private
val KotlinBuildScriptModelParameter.noScriptPath
    get() = scriptPath == null


private
val KotlinBuildScriptModelParameter.settingsScript
    get() = scriptFile?.name == "settings.gradle.kts"


private
val KotlinBuildScriptModelParameter.scriptFile
    get() = scriptPath?.let { canonicalFile(it) }


private
val Settings.scriptCompilationClassPath
    get() = serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf(classLoaderScope)


private
val Settings.classLoaderScope
    get() = (this as SettingsInternal).classLoaderScope


private
val Project.settings
    get() = (gradle as GradleInternal).settings


private
val Project.scriptCompilationClassPath
    get() = serviceOf<KotlinScriptClassPathProvider>().compilationClassPathOf((this as ProjectInternal).classLoaderScope)


private
val Project.defaultScriptCompilationClassPath
    get() = DefaultClassPath.of(project.buildSrcClassPath + gradleKotlinDslOf(rootProject))


private
val Project.buildSrcClassPath
    get() = ClasspathUtil
        .getClasspath(buildscript.classLoader)
        .asFiles
        .filter { it.name == "buildSrc.jar" }


private
val Project.scriptImplicitImports
    get() = serviceOf<ImplicitImports>().list


private
val Project.hierarchy: Sequence<Project>
    get() = buildSequence {
        var project = this@hierarchy
        yield(project)
        while (project != project.rootProject) {
            project = project.parent!!
            yield(project)
        }
    }


private
fun canonicalFile(path: String): File =
    File(path).canonicalFile

