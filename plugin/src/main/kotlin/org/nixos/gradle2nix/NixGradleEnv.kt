package org.nixos.gradle2nix

import com.squareup.moshi.JsonWriter
import okio.buffer
import okio.sink
import okio.source
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty

open class NixGradleEnv : DefaultTask() {

    @InputFiles
    val inputEnvs = project.objects.fileCollection()

    @Internal
    val outputDir = project.objects.directoryProperty()

    @OutputFile
    val outputFile = project.objects.fileProperty()
        .convention(outputDir.file("gradle-env.json"))

    @TaskAction
    fun run() {
        val envsByPath = inputEnvs.map { file ->
            file.source().buffer().use {
                moshi.adapter(BuildEnv::class.java).fromJson(it)
                    ?: throw IllegalStateException(
                        "Failed to load build env from ${file.path}."
                    )
            }
        }.groupBy(BuildEnv::path)

        val outFile = outputFile.get().asFile.also { it.parentFile.mkdirs() }

        JsonWriter.of(outFile.sink().buffer()).use { writer ->
            val adapter = moshi.adapter(BuildEnv::class.java).indent("  ")
            writer.indent = "  "
            writer.beginObject()
            for ((path, envs) in envsByPath) {
                writer.name(path)
                writer.beginObject()
                for (env in envs) {
                    writer.name(env.env)
                    adapter.toJson(writer, env)
                }
                writer.endObject()
            }
            writer.endObject()
        }
    }
}
