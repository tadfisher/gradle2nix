package org.nixos.gradle2nix

import com.squareup.moshi.Moshi
import okio.buffer
import okio.source
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.invocation.Gradle
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import org.gradle.plugin.use.internal.PluginResolverFactory
import java.io.File
import java.net.URL
import java.util.Locale
import javax.inject.Inject

open class Gradle2NixPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        val configurationNames: List<String> =
            System.getProperty("org.nixos.gradle2nix.configurations")?.split(",") ?: emptyList()

        val pluginRequests = collectPlugins(gradle)

        gradle.projectsLoaded {
            val extension = rootProject.extensions.create<Gradle2NixExtension>(
                "gradle2nix",
                rootProject,
                configurationNames
            )

            val gradleEnv = rootProject.tasks.register("nixGradleEnv", NixGradleEnv::class) {
                outputDir.set(extension.outputDir)
            }

            val buildSrcDir = rootProject.projectDir.resolve("buildSrc")
            if (buildSrcDir.exists() && buildSrcDir.isDirectory) {
                val buildSrcEnv =
                    rootProject.tasks.register("nixBuildSrcEnv", NixBuildSrcEnv::class) {
                        dir = buildSrcDir
                        val buildFile = buildSrcDir.listFiles().let { files ->
                            files.find { it.name == "build.gradle.kts" } ?:
                                files.find { it.name == "build.gradle" }
                        }
                        if (buildFile != null) this.buildFile = buildFile
                    }
                gradleEnv.configure {
                    dependsOn(buildSrcEnv)
                }
            }

            val pluginEnv =
                rootProject.tasks.register("nixPluginEnv", NixPluginEnv::class, pluginRequests)
            gradleEnv.configure {
                inputEnvs.from(pluginEnv)
            }

            allprojects {
                val buildscriptEnv = tasks.register("nixBuildscriptEnv", NixBuildscriptEnv::class) {
                    pluginEnvFile.set(pluginEnv.flatMap { it.outputFile })
                }
                val projectEnv = tasks.register("nixProjectEnv", NixProjectEnv::class) {
                    configurations.addAll(extension.configurations)
                }
                gradleEnv.configure {
                    inputEnvs.from(buildscriptEnv)
                    inputEnvs.from(projectEnv)
                }
            }

            resolveGradleDist(gradle, extension, gradleEnv)
        }
    }

    private fun collectPlugins(gradle: Gradle): List<PluginRequest> {
        val pluginRequests = mutableListOf<PluginRequest>()
        gradle.settingsEvaluated {
            pluginManagement.resolutionStrategy.eachPlugin {
                if (requested.id.namespace != null && requested.id.namespace != "org.gradle") {
                    pluginRequests.add(target)
                }
            }
        }
        return pluginRequests
    }

    private fun resolveGradleDist(
        gradle: Gradle,
        extension: Gradle2NixExtension,
        gradleEnv: TaskProvider<NixGradleEnv>
    ) {
        gradle.projectsEvaluated {
            val gradleDist = rootProject.tasks.named("wrapper", Wrapper::class).map {
                GradleDist(
                    version = it.gradleVersion,
                    type = it.distributionType.name.toLowerCase(Locale.US),
                    url = it.distributionUrl,
                    sha256 = it.distributionSha256Sum ?: fetchDistSha256(it.distributionUrl),
                    nativeVersion = gradle.gradleHomeDir?.resolve("lib")?.listFiles()
                        ?.firstOrNull { f -> f.name.matches(nativePlatformJarRegex) }?.let { nf ->
                            nativePlatformJarRegex.find(nf.name)?.groupValues?.get(1)
                        }
                        ?: throw IllegalStateException("""
                            Failed to find native-platform jar in ${gradle.gradleHomeDir}.

                            Ask Tad to fix this.
                        """.trimIndent())
                )
            }
            val gradleDistTask =
                gradle.rootProject.tasks.register("nixGradleDist", NixGradleDist::class) {
                    this.gradleDist.set(gradleDist)
                    outputDir.set(extension.outputDir)
                }
            gradleEnv.configure {
                dependsOn(gradleDistTask)
            }
        }
    }
}

internal val pluginJar by lazy {
    File(Gradle2NixPlugin::class.java.protectionDomain.codeSource.location.toURI()).absoluteFile
}

internal val moshi by lazy { Moshi.Builder().build() }

open class Gradle2NixExtension(project: Project, defaultConfigurations: List<String>) {
    var outputDir: File = project.projectDir.resolve("gradle/nix")
    var configurations: MutableList<String> = mutableListOf<String>().apply {
        addAll(defaultConfigurations)
    }
}

private fun fetchDistSha256(url: String): String {
    return URL("$url.sha256").openConnection().run {
        connect()
        getInputStream().source().buffer().use { source ->
            source.readUtf8()
        }
    }
}

private val nativePlatformJarRegex = Regex("""native-platform-(\d\.\d+)\.jar""")

