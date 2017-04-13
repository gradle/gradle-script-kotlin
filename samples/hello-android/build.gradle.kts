import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultProductFlavor
import com.android.builder.model.ApiVersion

import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

buildscript {
    //Temporary hack until Android plugin has proper support
    System.setProperty("com.android.build.gradle.overrideVersionCheck",  "true")

    repositories {
        jcenter()
        gradleScriptKotlin()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:2.2.0")
        classpath(kotlinModule("gradle-plugin"))
    }
}

repositories {
    jcenter()
    gradleScriptKotlin()
}


apply {
    plugin<AppPlugin>()
    plugin<KotlinAndroidPluginWrapper>()
}

android {
    buildToolsVersion("23.0.3")
    compileSdkVersion(23)

    defaultConfig {
        setMinSdkVersion(15)
        setTargetSdkVersion(23)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    compile("com.android.support:appcompat-v7:23.4.0")
    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha8")
    compile(kotlinModule("stdlib"))
}

//Extension functions to allow comfortable references
fun Project.android(configuration: AppExtension.() -> Unit) = configure(configuration)

fun DefaultProductFlavor.setMinSdkVersion(value: Int) = setMinSdkVersion(value.asApiVersion())

fun DefaultProductFlavor.setTargetSdkVersion(value: Int) = setTargetSdkVersion(value.asApiVersion())

fun Int.asApiVersion(): ApiVersion = DefaultApiVersion.create(this)

fun KotlinDependencyHandler.debugCompile(dependencyNotation: String) = dependencies { "debugCompile"(dependencyNotation) }

fun KotlinDependencyHandler.androidTestCompile(dependencyNotation: String) = dependencies { "androidTestCompile"(dependencyNotation) }

fun KotlinDependencyHandler.releaseCompile(dependencyNotation: String) = dependencies { "releaseCompile"(dependencyNotation) }
