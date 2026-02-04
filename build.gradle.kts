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

plugins {
    java
    `java-gradle-plugin`

    id("com.gradle.plugin-publish") version "2.0.0"

    id("com.dorkbox.GradleUtils") version "4.8"
    id("com.dorkbox.Licensing") version "3.1"
    id("com.dorkbox.VersionUpdate") version "3.0"

    kotlin("jvm") version "2.3.0"
}


///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load {
    group = "com.dorkbox"
    id = "CrossCompile"
    description = "Plugin to auto-configure cross-compilation builds for java projects"
    name = "Gradle CrossCompile Plugin"
    version = "2.0"
    vendor = "Dorkbox LLC"
    url = "https://git.dorkbox.com/dorkbox/CrossCompile"
}
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_25)

licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        url(Extras.url)
        author(Extras.vendor)
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
            id = Extras.groupAndId
            implementationClass = "dorkbox.crossCompile.PrepareJdk"
            displayName = Extras.name
            description = Extras.description
            version = Extras.version
            tags.set(listOf("crosscompile", "compile", "java", "kotlin"))
        }
    }
}
