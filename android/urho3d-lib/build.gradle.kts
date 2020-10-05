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

import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.os.OperatingSystem

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("android.extensions")
    `maven-publish`
}

android {
    ndkVersion = ndkSideBySideVersion
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(18)
        targetSdkVersion(30)
        versionCode = 1
        versionName = project.version.toString()
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments.apply {
                    System.getenv("ANDROID_CCACHE")?.let { add("-D ANDROID_CCACHE=$it") }
                    add("-D BUILD_STAGING_DIR=$projectDir/$buildStagingDir")
                    // Pass along matching Gradle properties (higher precedence) or env-vars as CMake build options
                    val vars = project.file("../../script/.build-options").readLines()
                    addAll(vars
                        .filter { project.hasProperty(it) }
                        .map { "-D $it=${project.property(it)}" }
                    )
                    addAll(vars
                        .filterNot { project.hasProperty(it) }
                        .map { variable -> System.getenv(variable)?.let { "-D $variable=$it" } ?: "" }
                    )
                }
                targets.add("Urho3D")
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
    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = project.file("../../CMakeLists.txt")
            // Make it explicit as one of the task needs to know the exact path and derived from it
            setBuildStagingDirectory(buildStagingDir)
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDir("../../Source/ThirdParty/SDL/android-project/app/src/main/java")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("stdlib-jdk8", embeddedKotlinVersion))
    implementation("com.getkeepsafe.relinker:relinker:1.4.1")
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

android.libraryVariants.whenObjectAdded {
    val config = name
    packageLibraryProvider.get().apply {
        // Customize bundle task to also zip the headers; and the static archive (for STATIC lib type)
        File(android.externalNativeBuild.cmake.buildStagingDirectory, "cmake/$config").list()?.forEach { abi ->
            listOf("include", "lib").forEach {
                from(File(android.externalNativeBuild.cmake.buildStagingDirectory, "cmake/$config/$abi/$it")) {
                    into("jni/$abi/$it")
                }
            }
        }
    }
}

tasks {
    register<Delete>("cleanAll") {
        dependsOn("clean")
        delete = setOf(android.externalNativeBuild.cmake.buildStagingDirectory)
    }
    register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(android.sourceSets.getByName("main").java.srcDirs)
    }
    register<Zip>("documentationZip") {
        archiveClassifier.set("documentation")
        dependsOn("makeDoc")
    }
    register<Exec>("makeDoc") {
        // Ignore the exit status on Windows host system because Doxygen may not return exit status correctly on Windows
        isIgnoreExitValue = OperatingSystem.current().isWindows
        standardOutput = NullOutputStream.INSTANCE
        args("--build", ".", "--target", "doc")
        dependsOn("makeDocConfigurer")
    }
    register<Task>("makeDocConfigurer") {
        dependsOn("generateJsonModelRelease")
        doLast {
            val abi = File(android.externalNativeBuild.cmake.buildStagingDirectory, "cmake/release").list()!!.first()
            val buildTree = File(android.externalNativeBuild.cmake.buildStagingDirectory, "cmake/release/$abi")
            named<Exec>("makeDoc") {
                // This is a hack - expect the first line to contain the path to the CMake executable
                executable = File(buildTree, "build_command.txt").readLines().first().split(":").last().trim()
                workingDir = buildTree
            }
            named<Zip>("documentationZip") {
                from(File(buildTree, "Docs/html")) {
                    into("docs")
                }
            }
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("Urho") {
            groupId = project.group.toString()
            artifactId = "${project.name}-${project.libraryType.toLowerCase()}"
            if (project.hasProperty("ANDROID_ABI")) {
                artifactId = "$artifactId-${(project.property("ANDROID_ABI") as String).replace(',', '-')}"
            }
            afterEvaluate {
                from(components["release"])
            }
            artifact(tasks["sourcesJar"])
            artifact(tasks["documentationZip"])
            pom {
                inceptionYear.set("2008")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://github.com/urho3d/Urho3D/blob/master/LICENSE")
                    }
                }
                developers {
                    developer {
                        name.set("Urho3D contributors")
                        url.set("https://github.com/urho3d/Urho3D/graphs/contributors")
                    }
                }
                scm {
                    url.set("https://github.com/urho3d/Urho3D.git")
                    connection.set("scm:git:ssh://git@github.com:urho3d/Urho3D.git")
                    developerConnection.set("scm:git:ssh://git@github.com:urho3d/Urho3D.git")
                }
                withXml {
                    asNode().apply {
                        appendNode("name", "Urho3D")
                        appendNode("description", project.description)
                        appendNode("url", "https://urho3d.github.io/")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/urho3d/Urho3D")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
