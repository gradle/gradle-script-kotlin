package org.gradle.kotlin.dsl.plugins

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.gradle.util.TextUtil.normaliseFileSeparators

import org.junit.Before

import java.io.File
import java.util.Properties

open class AbstractPluginTest : AbstractIntegrationTest() {

    @Before
    fun setUpTestPluginRepository() {
        val testRepository = normaliseFileSeparators(absolutePathOf("build/repository"))
        val futureVersion = loadTestProperties()["version"]
        withFile("settings.gradle", """
            pluginManagement {
                repositories {
                    maven { url = uri("$testRepository") }
                    maven { url = uri("https://jcenter.bintray.com/") }
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.namespace == "org.gradle.kotlin") {
                            useVersion("$futureVersion")
                        }
                    }
                }
            }
        """)
    }

    private
    fun loadTestProperties(): Properties =
        javaClass.getResourceAsStream("/test.properties").use {
            Properties().apply { load(it) }
        }

    protected
    fun buildWithPlugin(vararg arguments: String) =
        build(*arguments)

    private
    fun absolutePathOf(path: String) =
        File(path).absolutePath
}
