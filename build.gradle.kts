/*
 * Copyright 2018 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.time.Instant

plugins {
    java
    `java-gradle-plugin`

    id("com.gradle.plugin-publish") version "1.1.0"
    id("com.dorkbox.GradleUtils") version "3.9"
    id("com.dorkbox.Licensing") version "2.19.1"

    kotlin("jvm") version "1.7.0"
}


object Extras {
    // set for the project
    const val description = "Plugin to auto-configure cross-compilation builds for java projects"
    const val group = "com.dorkbox"
    const val version = "1.1"

    // set as project.ext
    const val name = "Gradle CrossCompile Plugin"
    const val id = "CrossCompile"
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/CrossCompile"
    val tags = listOf("crosscompile", "compile", "java", "kotlin", "groovy")
    val buildDate = Instant.now().toString()

    val KOTLIN_VERSION = JavaVersion.VERSION_1_8
}


///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("OpenJDK", License.GPLv2_CLASSPATH) {
            description("Compressed OpenJDK runtimes for cross-target class compilation")
            copyright(1995)
            copyright(2006)
            author("Oracle and/or its affiliates")
            url("https://github.com/dorkbox/JavaBuilder/tree/master/jdkRuntimes")
            url("http://jdk.java.net/")
            url("https://github.com/alexkasko/openjdk-unofficial-builds")
            note(" http://hg.openjdk.java.net/jdk6/jdk6/jdk/file/79b17290a53c/THIRD_PARTY_README")
            note(" http://hg.openjdk.java.net/jdk7/jdk7/jdk/file/9b8c96f96a0f/THIRD_PARTY_README")
            note(" http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/THIRD_PARTY_README")
        }
    }
}

sourceSets {
    main {
        resources {
            setSrcDirs(listOf("jdkRuntimes"))
            exclude("**/*.jar")
        }
    }
}

dependencies {
    // the kotlin version is taken from the plugin, so it is not necessary to set it here
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")

    api("de.undercouch:gradle-download-task:4.1.1")
    api("org.apache.commons:commons-compress:1.21")
    api("org.tukaani:xz:1.9")
    api("org.slf4j:slf4j-api:1.7.30")

    implementation("ch.qos.logback:logback-classic:1.2.3")
}

tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}
//
///////////////////////////////////
//////////    Plugin Publishing + Release
///////////////////////////////////
gradlePlugin {
//    website.set(Extras.url)
//    vcsUrl.set(Extras.url)

    plugins {
        create("CrossCompile") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.crossCompile.PrepareJdk"
            displayName = Extras.name
            description = Extras.description
//            tags = Extras.tags
            version = Extras.version
        }
    }
}
