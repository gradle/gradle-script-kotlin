import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories { gradleScriptKotlin() }
    dependencies { classpath(kotlinModule("gradle-plugin")) }
}

allprojects {

    group = "org.gradle.script.kotlin.samples.multiprojectci"

    version = "1.0"

    repositories {
        gradleScriptKotlin()
    }
}

// Apply and configure the Kotlin Gradle plugin on each sub-project
subprojects {

    apply {
        plugin("kotlin")
    }

    dependencies {
        compile(kotlinModule("stdlib"))
    }

    tasks.withType<KotlinCompile> {
        println("Configuring $name in project ${project.name}...")
        kotlinOptions {
            suppressWarnings = true
        }
    }
}

plugins {
    base
}

dependencies {
    // Make the root project archives configuration depend on every subproject
    subprojects.forEach {
        archives(it)
    }
}
