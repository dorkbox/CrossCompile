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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Instant
import java.util.*

plugins {
    java
    `java-gradle-plugin`

    id("com.gradle.plugin-publish") version "0.14.0"
    id("com.dorkbox.Licensing") version "2.0"
    id("com.dorkbox.GradleUtils") version "2.1"

    kotlin("jvm") version "1.4.32"
}


// load properties from custom location
val propsFile = File("$projectDir/../../gradle.properties").normalize()
if (propsFile.canRead()) {
    println("Loading custom property data from: $propsFile")

    val props = Properties()
    props.load(propsFile.inputStream())
    props.forEach{(k, v) -> project.extra.set(k as String, v as String)}
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

    val JAVA_VERSION = JavaVersion.VERSION_1_8
    val KOTLIN_VERSION = JavaVersion.VERSION_1_8
}


///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.fixIntellijPaths()


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)

        extra("OpenJDK", License.GPLv2_CLASSPATH) {
            it.description("Compressed OpenJDK runtimes for cross-target class compilation")
            it.copyright(1995)
            it.copyright(2006)
            it.author("Oracle and/or its affiliates")
            it.url("https://github.com/dorkbox/JavaBuilder/tree/master/jdkRuntimes")
            it.url("http://jdk.java.net/")
            it.url("https://github.com/alexkasko/openjdk-unofficial-builds")
            it.note(" http://hg.openjdk.java.net/jdk6/jdk6/jdk/file/79b17290a53c/THIRD_PARTY_README")
            it.note(" http://hg.openjdk.java.net/jdk7/jdk7/jdk/file/9b8c96f96a0f/THIRD_PARTY_README")
            it.note(" http://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/THIRD_PARTY_README")
        }
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))

            // want to include kotlin files for the source. 'setSrcDirs' resets includes...
            include("**/*.kt")
        }

        resources {
            setSrcDirs(listOf("jdkRuntimes"))
            exclude("**/*.jar")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // the kotlin version is taken from the plugin, so it is not necessary to set it here
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")

    implementation("de.undercouch:gradle-download-task:4.1.1")
    implementation("org.apache.commons:commons-compress:1.20")
    implementation("org.tukaani:xz:1.9")
    implementation("org.slf4j:slf4j-api:1.7.30")

    runtime("ch.qos.logback:logback-classic:1.2.3")
}
java {
    sourceCompatibility = Extras.JAVA_VERSION
    targetCompatibility = Extras.JAVA_VERSION
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = true
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = Extras.KOTLIN_VERSION.toString()
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.FAIL
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

/////////////////////////////////
////////    Plugin Publishing + Release
/////////////////////////////////
gradlePlugin {
    plugins {
        create("CrossCompile") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.crossCompile.PrepareJdk"
        }
    }
}

pluginBundle {
    website = Extras.url
    vcsUrl = Extras.url

    (plugins) {
        "CrossCompile" {
            id = "${Extras.group}.${Extras.id}"
            displayName = Extras.name
            description = Extras.description
            tags = Extras.tags
            version = Extras.version
        }
    }
}
