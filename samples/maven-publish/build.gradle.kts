import org.gradle.api.tasks.bundling.Jar

plugins {
    kotlin("jvm") version "1.2.41"
    `maven-publish`
}

group = "org.gradle.sample"
version = "1.0.0"

repositories {
    jcenter()
}

dependencies {
    compile(kotlin("stdlib"))
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri("$buildDir/repo")
        }
    }
    (publications) {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}
