import org.eclipse.jgit.api.Git

buildscript {
    repositories { jcenter() }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:4.9.0.201710071750-r")
    }
}

plugins {
    java
}

group = "org.gradle.kotlin.dsl.samples.source-control"
version = "1.0"

tasks {
    create("generateGitRepo") {

        inputs.dir("src")
        inputs.files("build.gradle.kts", "settings.gradle.kts")

        val gitRepoDir = file("$buildDir/git-repo")
        outputs.dir(gitRepoDir)

        doLast {
            delete(gitRepoDir)
            delete(temporaryDir)

            val bareUri = Git.init().setDirectory(gitRepoDir).setBare(true).call().use { bare ->
                bare.repository.directory.toURI().toString()
            }

            Git.cloneRepository().setURI(bareUri).setDirectory(temporaryDir).call().use { clone ->
                copy {
                    from(".") {
                        include("src/**")
                        include("*.gradle.kts")
                    }
                    into(temporaryDir)
                }

                clone.add()
                    .addFilepattern("build.gradle.kts")
                    .addFilepattern("settings.gradle.kts")
                    .addFilepattern("src")
                    .call()
                clone.commit()
                    .setMessage("Initial import")
                    .setAuthor("name", "email")
                    .call()

                clone.push().call()
            }
        }
    }
}
