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

    id("com.gradle.plugin-publish") version "2.0.0"

    id("com.dorkbox.GradleUtils") version "4.4"
    id("com.dorkbox.Licensing") version "3.1"
    id("com.dorkbox.VersionUpdate") version "3.0"

    kotlin("jvm") version "2.3.0"
}


object Extras {
    // set for the project
    const val description = "Plugin to auto-configure cross-compilation builds for java projects"
    const val group = "com.dorkbox"
    const val version = "2.0"

    // set as project.ext
    const val name = "Gradle CrossCompile Plugin"
    const val id = "CrossCompile"
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/CrossCompile"
    val tags = listOf("crosscompile", "compile", "java", "kotlin", "groovy")
    val buildDate = Instant.now().toString()
}


///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_25)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)
    }
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
    website.set(Extras.url)
    vcsUrl.set(Extras.url)

    plugins {
        register("CrossCompile") {
            id = "${Extras.group}.${Extras.id}"
            implementationClass = "dorkbox.crossCompile.PrepareJdk"
            displayName = Extras.name
            description = Extras.description
            tags = Extras.tags
            version = Extras.version
        }
    }
}
