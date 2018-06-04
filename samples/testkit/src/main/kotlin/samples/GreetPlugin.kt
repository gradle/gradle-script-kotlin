package samples

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*


class GreetPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val greeting = extensions.create("greeting", Greeting::class.java)
        tasks {
            create("greet") {
                doLast {
                    println(greeting.message)
                }
            }
        }
    }
}


open class Greeting {
    var message = "Hello!"
}

