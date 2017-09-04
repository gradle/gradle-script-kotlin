package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test

class ExtraPropertiesSampleTest : AbstractSampleTest("extra-properties") {

    @Test
    fun `extra properties`() {
        assertThat(
            build("myTask").output,
            allOf(
                containsString("myTask.foo = 42"),
                containsString("Extra property value: 42")))
    }
}
