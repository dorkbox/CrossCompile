/*
 * Copyright 2012 dorkbox, llc
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
package dorkbox.crossCompile

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.Verify
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream
import org.gradle.api.*
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Downloads JDKs and configures the bootstrap classpath when cross-compiling java projects
 */
class PrepareJdk : Plugin<Project> {

    companion object {
        data class JDK(val file: File, val checksum: String)

        private val versionInfo = HashMap<JavaVersion, JDK>()

        private const val compressSuffix = ".pack.lzma"
        private const val jarSuffix = ".jar"

        val logger: Logger = LoggerFactory.getLogger(PrepareJdk::class.java)
    }


    override fun apply(project: Project) {
        project.rootProject.pluginManager.apply(JavaBasePlugin::class.java)
        project.rootProject.pluginManager.apply(JavaBasePlugin::class.java)

        project.afterEvaluate {
            // don't waste time if this is not a java project
            val convention: JavaPluginConvention? = project.convention.plugins["java"] as? JavaPluginConvention
            var needsJdk = false
            if (convention != null) {
                // check if we need to extract anything..

                if (convention.targetCompatibility != JavaVersion.current()) {
                    needsJdk = true
                }

                project.tasks.forEach { task ->
                    if (!needsJdk && task is JavaCompile) {
                        if (JavaVersion.toVersion(task.targetCompatibility) != JavaVersion.current()) {
                            needsJdk = true
                        }
                    }
                }

                if (needsJdk) {
                    // if there is a clean task (usually the first thing to run, if run), run after the clean task, otherwise run first.

                    val hasClean = project.gradle.startParameter.taskNames.filter { taskName ->
                        taskName.toLowerCase().contains("clean")
                    }

                    if (hasClean.isNotEmpty()) {
                        val task = project.tasks.last { task -> task.name == hasClean.last() }

                        task.doLast {
                            setupDownload(project)
                        }
                    }
                    else {
                        setupDownload(project)
                    }
                }
            }
        }
    }

    private fun setupDownload(project: Project) {
        downloadAndExtractJdk(project)

        project.tasks.forEach {
            if (it is AbstractCompile) {
                if (JavaVersion.toVersion(it.targetCompatibility) != JavaVersion.current()) {
                    // only supports JAVA and GROOVY
                    configureTaskBootstrapClassPath(project, it, JavaVersion.toVersion(it.targetCompatibility))
                }
            }
        }
    }

    private fun downloadAndExtractJdk(project: Project) {
        val outputDir = File(project.buildDir, "jdkRuntimes")
        if (!outputDir.exists()) outputDir.mkdirs()

        logger.info("Preparing cross-compile environment")

        // download the JDK runtimes from github
        val prefix = "https://github.com/dorkbox/JavaBuilder/raw/master/jdkRuntimes"

        versionInfo[JavaVersion.VERSION_1_6] = JDK(File(outputDir, "openJdk6_rt.jar.pack.lzma").absoluteFile, "313a8b3fe4736520f7a4b6de37f1c80698502ee7")
        versionInfo[JavaVersion.VERSION_1_7] = JDK(File(outputDir, "openJdk7_rt.jar.pack.lzma").absoluteFile, "b42aa62d1772d1f2e8f93664c1e8cb866374e511")
        versionInfo[JavaVersion.VERSION_1_8] = JDK(File(outputDir, "openJdk8_rt.jar.pack.lzma").absoluteFile, "98616c3fc020750dce84f02bc65e5f66b839b29d")

        // if we are offline, DO NOT try to download anything
        if (!project.gradle.startParameter.isOffline) {
            // download JDKS we don't know about
            val downloadJdkList = getDownloadJdkList(prefix)
            val elements = versionInfo.map { it.value.file.name }

            downloadJdkList.removeAll(elements)

            for (jdk in downloadJdkList) {
                try {
                    val version = JavaVersion.toVersion(jdk.substring(7, jdk.indexOf('_')))
                    versionInfo[version] = JDK(File(outputDir, jdk).absoluteFile, "")
                } catch (e: Exception) {
                    logger.error("Unable to parse/download $jdk")
                }
            }

            // download all JDKS ...
            for (jdk in versionInfo.values) {
                downloadJDK(project, prefix, jdk.file, jdk.checksum)
            }
        }


        val jarFiles = getFiles(outputDir, jarSuffix)
        val compressedFiles = getFiles(outputDir, compressSuffix)
        var hasFiles = false


        // discover which files need compressing
        val iterator = jarFiles.iterator()
        while (iterator.hasNext()) {
            val jarFile = iterator.next()
            logger.debug("JarFile $jarFile")
            hasFiles = true

            val file = getCompressedFile(jarFile)

            // Don't always need to compress the jdk files. This checks if the compressed version exists
            if (file.canRead() && file.length() > 0) {
                iterator.remove()
            }
        }


        // discover which files need un-compressing
        val iterator2 = compressedFiles.iterator()
        while (iterator2.hasNext()) {
            val compressedFile = iterator2.next()
            logger.debug("CompressedFile $compressedFile")
            hasFiles = true

            val file = getUncompressedFile(compressedFile)

            // Don't always need to decompress the jdk files. This checks if the extracted version exists
            if (file.canRead() && file.length() > 0) {
                iterator2.remove()
            }
        }


        if (!hasFiles) {
            throw GradleException("Unable to find or extract jar files, none were found in $outputDir")
        }

        if (!compressedFiles.isEmpty() || !jarFiles.isEmpty()) {
            logger.info("Preparing cross compile environment")
        }


        // NOTE: we also compress files so we can automatically create NEW files if we need to
        for (inputFile in jarFiles) {
            // pack200 + LZMA
            logger.debug("Compressing $inputFile")


            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = FileInputStream(inputFile)
                inputStream = BufferedInputStream(inputStream)

                outputStream = FileOutputStream(getCompressedFile(inputFile))
                outputStream = BufferedOutputStream(outputStream)

                // now pack and compress
                outputStream = LZMACompressorOutputStream(outputStream)
                outputStream = Pack200CompressorOutputStream(outputStream)

                inputStream.copyTo(outputStream)
            } catch (e: Exception) {
                logger.error("Error compressing files", e)
            } finally {
                close(outputStream)
                close(inputStream)
            }
        }



        for (inputFile in compressedFiles) {
            // unLZMA + unpack200
            logger.debug("Extracting $inputFile")

            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            try {
                inputStream = FileInputStream(inputFile)
                inputStream = BufferedInputStream(inputStream)

                outputStream = FileOutputStream(getUncompressedFile(inputFile))
                outputStream = BufferedOutputStream(outputStream)

                // now uncompress and unpack
                inputStream = LZMACompressorInputStream(inputStream)
                inputStream = Pack200CompressorInputStream(inputStream)

                inputStream.copyTo(outputStream)
            } catch (e: Exception) {
                logger.error("Error extracting files", e)
            } finally {
                close(outputStream)
                close(inputStream)
            }
        }

        logger.info("Done preparing cross-compile environment")
    }

    private fun getDownloadJdkList(prefix: String): ArrayList<String> {
        val jdkRuntimes = ArrayList<String>()

        with(URL(prefix).openConnection() as HttpURLConnection) {
            // optional default is GET
            requestMethod = "GET"

            logger.debug("Sending 'GET' request to URL : $url")
            logger.debug("Response Code : $responseCode")


            BufferedReader(InputStreamReader(inputStream)).use {
                val jdkDirName = "jdkRuntimes"
                val lzmaExtension = ".jar.pack.lzma"

                var inputLine = it.readLine()
                while (inputLine != null) {
                    val indexJdk = inputLine.lastIndexOf(jdkDirName)
                    if (indexJdk > -1) {
                        val startIndex = indexJdk + jdkDirName.length + 1
                        val indexLzma = inputLine.indexOf(lzmaExtension, startIndex, false)
                        if (indexLzma > -1) {
                            val message = inputLine.substring(startIndex, indexLzma + lzmaExtension.length)
                            jdkRuntimes.add(message)
                            logger.debug(message)
                        }
                    }

                    inputLine = it.readLine()
                }
            }
        }

        return jdkRuntimes
    }

    private fun configureTaskBootstrapClassPath(project: Project, task: Task, targetVersion: JavaVersion) {
        val location = versionInfo[targetVersion]?.file

        if (location != null) {
            val file = getUncompressedFile(location)

            if (task is KotlinCompile) {
                logger.debug("Configuring task ${task.name} with ${file.absolutePath}")
                task.kotlinOptions.jdkHome = file.absolutePath
            }
            else {
                // java/groovy
                val bootstrapClasspath = project.files(file)
                val bootClasspath = bootstrapClasspath.joinToString(File.pathSeparator)

                if (task is JavaCompile) {
                    logger.debug("Configuring task ${task.name} with $bootClasspath")
                    task.options.bootstrapClasspath = bootstrapClasspath
                }
                else if (task is GroovyCompile) {
                    logger.debug("Configuring task ${task.name} with $bootClasspath")
                    task.options.bootstrapClasspath = bootstrapClasspath
                }
            }
        }
        else {
            logger.error("Unable to determine bootstrap path $targetVersion for ${task.name}")
        }
    }



    private fun getFiles(directory: File, suffix: String): MutableList<File> {
        val outputFiles = ArrayList<File>()

        if (directory.isDirectory) {
            val files = directory.listFiles()
            for (file in files) {
                val name = file.name

                if (name.endsWith(suffix)) {
                    outputFiles.add(file)
                }
            }
        }

        return outputFiles
    }

    private fun close(inputStream: InputStream?) {
        try {
            inputStream?.close()
        } catch (ignored: Exception) {
        }
    }

    private fun close(outputStream: OutputStream?) {
        try {
            outputStream?.close()
        } catch (ignored: Exception) {
        }
    }

    private fun getUncompressedFile(compressedFile: File): File {
        val nameLength = compressedFile.name.length
        val fixedName = compressedFile.name.substring(0, nameLength - compressSuffix.length)

        return File(compressedFile.parentFile, fixedName)
    }

    private fun getCompressedFile(jarFile: File): File {
        return File(jarFile.parentFile, jarFile.name + compressSuffix)
    }


    private fun Gradle.versionGreaterThan(version: String): Boolean = versionCompareTo(version) > 0
    private fun Gradle.versionCompareTo(version: String): Int {
        return GradleVersion.version(gradleVersion).compareTo(GradleVersion.version(version))
    }

    /**
     * checksum can be empty string to do a basic "does this file exist" check to determine if the file needs downloading
     * Download, if necessary, the specified JDK
     */
    private fun downloadJDK(project: Project, url: String, file: File, sha1Checksum: String) {
        var valid = false
        val fileName = file.name

        if (!sha1Checksum.isEmpty()) {
            val verify = project.tasks.create("verify${fileName.capitalize().substringBefore(".")}", Verify::class.java)
            verify.group = "crossCompile"
            verify.src(file)
            verify.algorithm("SHA1")
            verify.checksum(sha1Checksum)

            try {
                verify.verify()
                valid = true
            } catch (ignored: Exception) {
                // verify throws an exception if it cannot verify the file.
            }
        }
        else {
            // lame way to verify, but unable to find or extract jar if there is no checksum...
            valid = file.canRead() && file.length() > 0
        }

        if (!valid) {
            val download = project.tasks.create("download$fileName", Download::class.java)

            download.src("$url/$fileName")
            download.dest(file)
            download.quiet(true)
            download.overwrite(false)

            try {
                download.download()
            } catch (e: Exception) {
                logger.error("Unable to download $url", e)
            }
        }
    }
}
