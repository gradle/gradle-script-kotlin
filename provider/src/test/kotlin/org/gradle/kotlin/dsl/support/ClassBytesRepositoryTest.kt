package org.gradle.kotlin.dsl.support

import org.gradle.api.tasks.javadoc.Groovydoc
import org.gradle.api.tasks.wrapper.Wrapper

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.customInstallation

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.notNullValue

import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

import java.io.FileFilter


class ClassBytesRepositoryTest : AbstractIntegrationTest() {

    @Test
    fun `class file path candidates for source name`() {

        assertClassFilePathCandidatesFor(
            "My",
            listOf("My.class", "MyKt.class"))

        assertClassFilePathCandidatesFor(
            "foo.My",
            listOf("foo/My.class", "foo/MyKt.class", "foo${'$'}My.class", "foo${'$'}MyKt.class"))

        assertClassFilePathCandidatesFor(
            "foo.My.Nested",
            listOf(
                "foo/My/Nested.class", "foo/My/NestedKt.class",
                "foo/My${'$'}Nested.class", "foo/My${'$'}NestedKt.class",
                "foo${'$'}My${'$'}Nested.class", "foo${'$'}My${'$'}NestedKt.class"))
    }

    private
    fun assertClassFilePathCandidatesFor(sourceName: String, candidates: List<String>) {
        assertThat(
            classFilePathCandidatesFor(sourceName).toList(),
            equalTo(candidates))
    }

    @Test
    fun `source name for class file path`() {

        assertKotlinSourceNameOf("My.class", "My")
        assertKotlinSourceNameOf("MyKt.class", "My")

        assertKotlinSourceNameOf("foo/My.class", "foo.My")
        assertKotlinSourceNameOf("foo/MyKt.class", "foo.My")

        assertKotlinSourceNameOf("foo/My${'$'}Nested.class", "foo.My.Nested")
        assertKotlinSourceNameOf("foo/My${'$'}NestedKt.class", "foo.My.Nested")
    }

    private
    fun assertKotlinSourceNameOf(classFilePath: String, expected: String) {
        assertThat(kotlinSourceNameOf(classFilePath), equalTo(expected))
    }

    class SomeKotlin {
        interface NestedType
    }

    @Test
    fun `finds top-level, nested, java, kotlin types in JARs and directories`() {

        val jar1 = withClassJar(
            "first.jar",
            Groovydoc::class.java,
            Groovydoc.Link::class.java,
            DeepThought::class.java)

        val jar2 = withClassJar(
            "second.jar",
            Wrapper::class.java,
            Wrapper.DistributionType::class.java,
            SomeKotlin::class.java,
            SomeKotlin.NestedType::class.java)

        val cpDir = newDir("cp-dir")
        unzipTo(cpDir, jar2)

        classPathBytesRepositoryFor(listOf(jar1, cpDir)).use { repository ->
            assertThat(
                repository.classBytesFor(canonicalNameOf<Groovydoc.Link>()),
                notNullValue())
            assertThat(
                repository.classBytesFor(canonicalNameOf<Wrapper.DistributionType>()),
                notNullValue())
        }

        classPathBytesRepositoryFor(listOf(jar1, cpDir)).use { repository ->
            assertThat(
                repository.allSourceNames,
                hasItems(
                    canonicalNameOf<Groovydoc>(),
                    canonicalNameOf<Groovydoc.Link>(),
                    canonicalNameOf<DeepThought>(),
                    canonicalNameOf<Wrapper.DistributionType>(),
                    canonicalNameOf<Wrapper>(),
                    canonicalNameOf<SomeKotlin.NestedType>(),
                    canonicalNameOf<SomeKotlin>()))
        }
    }

    @Test
    fun `ignores package-info and compiler generated classes`() {

        val jars = customInstallation()
            .resolve("lib")
            .listFiles(FileFilter { it.name.startsWith("gradle-core-api-") })
            .toList()

        classPathBytesRepositoryFor(jars).use { repository ->
            repository.allSourceNames.apply {
                assertTrue(none { it == "package-info" })
                assertTrue(none { it.matches(Regex("\\$[0-9]\\.class")) })
            }
        }
    }

    private
    val ClassBytesRepository.allSourceNames: List<String>
        get() = allClassesBytesBySourceName().map { it.first }.toList()
}


internal
inline fun <reified T> canonicalNameOf(): String =
    T::class.java.canonicalName
