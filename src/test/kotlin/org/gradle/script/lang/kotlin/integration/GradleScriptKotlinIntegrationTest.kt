package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.embeddedKotlinVersion
import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.script.lang.kotlin.fixtures.DeepThought

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Assert.assertNotEquals
import org.junit.Test

import java.io.File

class GradleScriptKotlinIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `given a buildscript block, it will be used to compute the runtime classpath`() {
        checkBuildscriptBlockIsUsedToComputeRuntimeClasspathAfter({ it })
    }

    @Test
    fun `given a buildscript block separated by CRLF, it will be used to compute the runtime classpath`() {
        checkBuildscriptBlockIsUsedToComputeRuntimeClasspathAfter({ it.replace("\r\n", "\n").replace("\n", "\r\n") })
    }

    private
    fun checkBuildscriptBlockIsUsedToComputeRuntimeClasspathAfter(buildscriptTransformation: (String) -> String) {

        withClassJar("fixture.jar", DeepThought::class.java)

        withBuildScript("""
            buildscript {
                dependencies { classpath(files("fixture.jar")) }
            }

            task("compute") {
                doLast {
                    val computer = ${DeepThought::class.qualifiedName}()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """.let(buildscriptTransformation))

        assert(
            build("compute").output.contains("*42*"))
    }

    @Test
    fun `given a buildSrc dir, it will be added to the compilation classpath`() {

        withFile("buildSrc/src/main/groovy/build/DeepThought.groovy", """
            package build
            class DeepThought {
                def compute() { 42 }
            }
        """)

        withBuildScript("""
            task("compute") {
                doLast {
                    val computer = build.DeepThought()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """)

        assert(
            build("compute").output.contains("*42*"))
    }

    @Test
    fun `given a Kotlin project in buildSrc, it will be added to the compilation classpath`() {

        withKotlinBuildSrc()

        withFile("buildSrc/src/main/kotlin/build/DeepThought.kt", """
            package build

            class DeepThought() {
                fun compute(handler: (Int) -> Unit) { handler(42) }
            }
        """)

        withFile("buildSrc/src/main/kotlin/build/DeepThoughtPlugin.kt", """
            package build

            import org.gradle.api.*
            import org.gradle.script.lang.kotlin.*

            open class DeepThoughtPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        task("compute") {
                            doLast {
                                DeepThought().compute { answer ->
                                    println("*" + answer + "*")
                                }
                            }
                        }
                    }
                }
            }
        """)

        withBuildScript("""
            buildscript {
                // buildSrc types are available within buildscript
                // and must always be fully qualified
                build.DeepThought().compute { answer ->
                    println("buildscript: " + answer)
                }
            }
            apply<build.DeepThoughtPlugin>()
        """)

        val output = build("compute").output
        assert(output.contains("buildscript: 42"))
        assert(output.contains("*42*"))
    }

    @Test
    fun `given a plugin compiled against Kotlin one dot zero, it will run against the embedded Kotlin version`() {

        withBuildScript("""
            buildscript {
                repositories {
                    ivy { setUrl("${fixturesRepository.toURI()}") }
                    jcenter()
                }
                dependencies {
                    classpath("org.gradle.script.lang.kotlin.fixtures:plugin-compiled-against-kotlin-1.0:1.0")
                }
            }

            apply<fixtures.ThePlugin>()

            tasks.withType<fixtures.ThePluginTask> {
                from = "new value"
                doLast {
                    println(configure { "*[" + it + "]*" })
                }
            }
        """)

        assert(
            build("the-plugin-task").output.contains("*[new value]*"))
    }

    @Test
    fun `can compile against a different (but compatible) version of the Kotlin compiler`() {

        val differentKotlinVersion = "1.0.7"
        val expectedKotlinCompilerVersionString = "1.0.7-release-1"

        assertNotEquals(embeddedKotlinVersion, differentKotlinVersion)

        withBuildScript("""
            import org.jetbrains.kotlin.config.KotlinCompilerVersion
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            buildscript {
                repositories {
                    gradleScriptKotlin()
                }
                dependencies {
                    classpath(kotlinModule("gradle-plugin", version = "$differentKotlinVersion"))
                }
            }

            apply { plugin("kotlin") }

            tasks.withType<KotlinCompile> {
                // can configure the Kotlin compiler
                kotlinOptions.suppressWarnings = true
            }

            task("print-kotlin-version") {
                doLast {
                    val compileOptions = tasks.filterIsInstance<KotlinCompile>().joinToString(prefix="[", postfix="]") {
                        it.name + "=" + it.kotlinOptions.suppressWarnings
                    }
                    println(KotlinCompilerVersion.VERSION + compileOptions)
                }
            }
        """)

        assertThat(
            build("print-kotlin-version").output,
            containsString(expectedKotlinCompilerVersionString + "[compileKotlin=true, compileTestKotlin=true]"))
    }

    @Test
    fun `can apply base plugin via plugins block`() {

        withBuildScript("""
            plugins {
                id("base")
            }

            task("plugins") {
                doLast {
                    println(plugins.map { "*" + it::class.simpleName + "*" })
                }
            }
        """)

        assertThat(
            build("plugins").output,
            containsString("*BasePlugin*"))
    }

    @Test
    fun `can apply plugin portal plugin via plugins block`() {

        withBuildScript("""
            plugins {
                id("org.gradle.hello-world") version "0.2"
            }

            task("plugins") {
                doLast {
                    println(plugins.map { "*" + it.javaClass.simpleName + "*" })
                }
            }
        """)

        assertThat(
            build("plugins").output,
            containsString("*HelloWorldPlugin*"))
    }

    @Test
    fun `can use Closure only APIs`() {

        withBuildScript("""
            gradle.buildFinished(closureOf<org.gradle.BuildResult> {
                println("*" + action + "*") // <- BuildResult.getAction()
            })
        """)

        assert(
            build("build").output.contains("*Build*"))
    }

    @Test
    fun `given an exception thrown during buildscript block execution, its stack trace should contain correct file and line info`() {

        withBuildScript(""" // line 1
            // line 2
            // line 3
            buildscript { // line 4
                throw IllegalStateException() // line 5
            }
        """)

        assertThat(
            buildFailureOutput(),
            containsString("build.gradle.kts:5"))
    }

    @Test
    fun `given a script with more than one buildscript block, it throws exception with offending block line number`() {

        withBuildScript(""" // line 1
            buildscript {}  // line 2
            buildscript {}  // line 3
        """)

        assertThat(
            buildFailureOutput(),
            containsString("build.gradle.kts:3:13: Unexpected `buildscript` block found. Only one `buildscript` block is allowed per script."))
    }

    @Test
    fun `given a script with more than one plugins block, it throws exception with offending block line number`() {

        withBuildScript(""" // line 1
            plugins {}      // line 2
            plugins {}      // line 3
        """)

        assertThat(
            buildFailureOutput(),
            containsString("build.gradle.kts:3:13: Unexpected `plugins` block found. Only one `plugins` block is allowed per script."))
    }

    @Test
    fun `given a buildscript block compilation error, it reports correct error location`() {

        assertCorrectLocationIsReportedForErrorIn("buildscript")
    }

    @Test
    fun `given a plugins block compilation error, it reports correct error location`() {

        assertCorrectLocationIsReportedForErrorIn("plugins")
    }

    private
    fun assertCorrectLocationIsReportedForErrorIn(block: String) {
        val buildFile =
            withBuildScript("""
                $block {
                    val module = "foo:bar:${'$'}fooBarVersion"
                }
            """)

        assertThat(
            buildFailureOutput("tasks"),
            containsString("e: $buildFile:3:44: Unresolved reference: fooBarVersion"))
    }

    @Test
    fun `sub-project build script inherits parent project compilation classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withBuildScript("""
            buildscript {
                dependencies { classpath(files("fixture.jar")) }
            }
        """)

        existing("settings.gradle").appendText("""
            include 'sub-project'
            project(':sub-project').buildFileName = 'build.gradle.kts'
        """)

        withFile("sub-project/build.gradle.kts", """
            task("compute") {
                doLast {
                    val computer = ${DeepThought::class.qualifiedName}()
                    val answer = computer.compute()
                    println("*" + answer + "*")
                }
            }
        """)

        assert(
            build(":sub-project:compute").output.contains("*42*"))
    }

    private
    val fixturesRepository: File
        get() = File("fixtures/repository").absoluteFile
}

