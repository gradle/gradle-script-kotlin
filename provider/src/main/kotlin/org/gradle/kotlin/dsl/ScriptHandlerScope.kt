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

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.initialization.dsl.ScriptHandler.CLASSPATH_CONFIGURATION
import org.gradle.api.plugins.PluginAware


/**
 * Receiver for the `buildscript` block.
 */
open class ScriptHandlerScope(scriptHandler: ScriptHandler, pluginAware: PluginAware) :
    ScriptHandler by scriptHandler, PluginAware by pluginAware {

    /**
     * The dependencies of the script.
     */
    val dependencies = DependencyHandlerScope(scriptHandler.dependencies)

    /**
     * Adds a dependency to the script classpath.
     */
    fun DependencyHandler.classpath(dependencyNotation: Any): Dependency =
        add(CLASSPATH_CONFIGURATION, dependencyNotation)
}
