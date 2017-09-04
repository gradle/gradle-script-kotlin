/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl.plugins.embedded

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

import org.gradle.kotlin.dsl.support.EmbeddedKotlinProvider

import javax.inject.Inject

/**
 * The `embedded-kotlin` plugin.
 *
 * Applies the `org.jetbrains.kotlin.jvm` plugin,
 * adds compile dependencies on `kotlin-stdlib` and `kotlin-reflect`,
 * configures an embedded repository that contains all embedded Kotlin libraries,
 * and pins them to the embedded Kotlin version.
 */
open class EmbeddedKotlinPlugin @Inject internal constructor(
    private val embeddedKotlin: EmbeddedKotlinProvider) : Plugin<Project> {

    override fun apply(project: Project) {
        project.run {

            plugins.apply(KotlinPluginWrapper::class.java)

            embeddedKotlin.addRepositoryTo(repositories)

            val embeddedKotlinConfiguration = configurations.create("embeddedKotlin")
            embeddedKotlin.addDependenciesTo(
                dependencies,
                embeddedKotlinConfiguration.name,
                "stdlib", "reflect")
            configurations.getByName("compile").extendsFrom(embeddedKotlinConfiguration)

            configurations.all {
                embeddedKotlin.pinDependenciesOn(it, "stdlib", "reflect", "compiler-embeddable")
            }
        }
    }
}
