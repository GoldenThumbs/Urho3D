//
// Copyright (c) 2008-2020 the Urho3D project.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    ndkVersion = ndkSideBySideVersion
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(18)
        targetSdkVersion(30)
        applicationId = "io.urho3d.launcher"
        versionCode = 1
        versionName = project.version.toString()
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments.apply {
                    System.getenv("ANDROID_CCACHE")?.let { add("-D ANDROID_CCACHE=$it") }
                    add("-D BUILD_STAGING_DIR=${findProject(":android:urho3d-lib")!!.projectDir}/$buildStagingDir")
                    add("-D URHO3D_PLAYER=1")
                    // Skip building samples for 'STATIC' lib type to reduce the spacetime requirement
                    add("-D URHO3D_SAMPLES=${if (project.libraryType == "STATIC") "0" else "1"}")
                    // Pass along matching Gradle properties (higher precedence) or env-vars as CMake build options
                    val excludes = listOf("URHO3D_PLAYER", "URHO3D_SAMPLES")
                    val vars = project.file("../../script/.build-options")
                        .readLines()
                        .filterNot { excludes.contains(it) }
                    addAll(vars
                        .filter { project.hasProperty(it) }
                        .map { "-D $it=${project.property(it)}" }
                    )
                    addAll(vars
                        .filterNot { project.hasProperty(it) }
                        .map { variable -> System.getenv(variable)?.let { "-D $variable=$it" } ?: "" }
                    )
                }
            }
        }
        splits {
            abi {
                isEnable = project.hasProperty("ANDROID_ABI")
                reset()
                include(
                    *(project.findProperty("ANDROID_ABI") as String? ?: "")
                        .split(',')
                        .toTypedArray()
                )
            }
        }
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lintOptions {
        isAbortOnError = false
    }
    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = project.file("CMakeLists.txt")
            // Make it explicit as one of the task needs to know the exact path and derived from it
            setBuildStagingDirectory(buildStagingDir)
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(project(":android:urho3d-lib"))
    implementation(kotlin("stdlib-jdk8", embeddedKotlinVersion))
    implementation("androidx.core:core-ktx:1.3.2")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.1")
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

// Ensure IDE "gradle sync" evaluate the urho3d-lib module first
evaluationDependsOn(":android:urho3d-lib")

afterEvaluate {
    android.buildTypes.forEach {
        val config = it.name.capitalize()
        tasks {
            "externalNativeBuild$config" {
                mustRunAfter(":android:urho3d-lib:externalNativeBuild$config")
            }
        }
    }
}

tasks {
    register<Delete>("cleanAll") {
        dependsOn("clean")
        delete = setOf(android.externalNativeBuild.cmake.buildStagingDirectory)
    }
}
