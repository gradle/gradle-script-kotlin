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

package org.gradle.kotlin.dsl

import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.file.FileCollection

import org.gradle.api.initialization.dsl.ScriptHandler

import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal

import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.PluginAware

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.PropertyState

import org.gradle.api.tasks.TaskContainer

import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf
import org.gradle.kotlin.dsl.support.configureWith

import java.io.File

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


fun Project.buildscript(action: ScriptHandlerScope.() -> Unit): Unit =
    project.buildscript.configureWith(action)


/**
 * Sets the default tasks of this project. These are used when no tasks names are provided when
 * starting the build.
 */
@Suppress("nothing_to_inline")
inline fun Project.defaultTasks(vararg tasks: Task) {
    defaultTasks(*tasks.map { it.name }.toTypedArray())
}


/**
 * Applies the plugin of the given type [T]. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see [PluginAware.apply]
 */
inline fun <reified T : Plugin<out Project>> Project.apply() =
    (this as PluginAware).apply<T>()


/**
 * Executes the given configuration block against the [plugin convention]
 * [Convention.getPlugin] or extension of the specified type.
 *
 * @param T the plugin convention type.
 * @param configuration the configuration block.
 * @see [Convention.getPlugin]
 */
inline fun <reified T : Any> Project.configure(noinline configuration: T.() -> Unit): Unit =
    typeOf<T>().let { type ->
        convention.findByType(type)?.let(configuration)
            ?: convention.findPlugin<T>()?.let(configuration)
            ?: convention.configure(type, configuration)
    }


/**
 * Returns the plugin convention or extension of the specified type.
 */
inline fun <reified T : Any> Project.the(): T =
    typeOf<T>().let { type ->
        convention.findByType(type)
            ?: convention.findPlugin(T::class.java)
            ?: convention.getByType(type)
    }


/**
 * Returns the plugin convention or extension of the specified type.
 */
fun <T : Any> Project.the(extensionType: KClass<T>): T =
    convention.findByType(extensionType.java)
        ?: convention.findPlugin(extensionType.java)
        ?: convention.getByType(extensionType.java)


/**
 * Creates a [Task] with the given [name] and [type], configures it with the given [configuration] action,
 * and adds it to this project tasks container.
 */
inline fun <reified type : Task> Project.task(name: String, noinline configuration: type.() -> Unit) =
    task(name, type::class, configuration)


/**
 * Creates a [Task] with the given [name] and [type], and adds it to this project tasks container.
 *
 * @see [Project.getTasks]
 * @see [TaskContainer.create]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified type : Task> Project.task(name: String) =
    tasks.create(name, type::class.java)


fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    createTask(name, type, configuration)


/**
 * Creates a [Task] with the given [name] and [DefaultTask] type, configures it with the given [configuration] action,
 * and adds it to this project tasks container.
 */
fun Project.task(name: String, configuration: Task.() -> Unit): DefaultTask =
    createTask(name, DefaultTask::class, configuration)


fun <T : Task> Project.createTask(name: String, type: KClass<T>, configuration: T.() -> Unit): T =
    tasks.create(name, type.java, configuration)


/**
 * Configures the repositories for this project.
 *
 * Executes the given configuration block against the [RepositoryHandler] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.repositories(configuration: RepositoryHandler.() -> Unit) =
    repositories.configuration()


fun ScriptHandler.repositories(configuration: RepositoryHandler.() -> Unit) =
    repositories.configuration()


/**
 * Configures the dependencies for this project.
 *
 * Executes the given configuration block against the [DependencyHandlerScope] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.dependencies(configuration: DependencyHandlerScope.() -> Unit) =
    DependencyHandlerScope(dependencies).configuration()


/**
 * Locates a property on [Project].
 */
operator fun Project.provideDelegate(any: Any?, property: KProperty<*>): PropertyDelegate =
    propertyDelegateFor(this, property)


/**
 * Creates a [Property] that holds values of the given type [T].
 *
 * @see [ObjectFactory.property]
 */
@Incubating
inline fun <reified T> ObjectFactory.property(): Property<T> =
    property(T::class.java)


/**
 * Creates a [ListProperty] that holds values of the given type [T].
 *
 * @see [ObjectFactory.listProperty]
 */
@Incubating
inline fun <reified T> ObjectFactory.listProperty(): ListProperty<T> =
    listProperty(T::class.java)


/**
 * Creates a [PropertyState] that holds values of the given type [T].
 *
 * @see [Project.property]
 */
@Incubating
@Deprecated("Will be removed in 1.0", replaceWith = ReplaceWith("objects.property()"))
inline fun <reified T> Project.property(): PropertyState<T> =
    property(T::class.java)


/**
 * Creates a dependency on the API of the current version of the Gradle Kotlin DSL.
 *
 * Includes the Kotlin and Gradle APIs.
 *
 * @return The dependency.
 */
fun Project.gradleKotlinDsl(): Dependency =
    DefaultSelfResolvingDependency(
        fileCollectionOf(
            gradleKotlinDslOf(project),
            "gradleKotlinDsl") as FileCollectionInternal)


@Deprecated("Will be removed in 1.0", ReplaceWith("gradleKotlinDsl()"))
fun Project.gradleScriptKotlinApi(): Dependency =
    gradleKotlinDsl()


private
fun fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    DefaultFileCollectionFactory().fixed(name, files)
