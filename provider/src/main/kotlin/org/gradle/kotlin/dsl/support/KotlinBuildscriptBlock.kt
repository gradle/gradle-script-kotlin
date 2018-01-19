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

package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.KotlinBuildScript
import org.gradle.kotlin.dsl.KotlinSettingsScript
import org.gradle.kotlin.dsl.ScriptHandlerScope

import org.gradle.api.Project
import org.gradle.api.initialization.Settings


/**
 * Base class for `buildscript` block evaluation on scripts targeting Project.
 */
abstract class KotlinBuildscriptBlock(project: Project) : KotlinBuildScript(project) {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    override fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        ScriptHandlerScope(project.buildscript, project).block()
    }
}


/**
 * Base class for `buildscript` block evaluation on scripts targeting Settings.
 */
abstract class KotlinSettingsBuildscriptBlock(settings: Settings) : KotlinSettingsScript(settings) {

    /**
     * Configures the build script classpath for settings.
     *
     * @see [Settings.buildscript]
     */
    override fun buildscript(block: ScriptHandlerScope.() -> Unit) {
        ScriptHandlerScope(settings.buildscript, settings).block()
    }
}
