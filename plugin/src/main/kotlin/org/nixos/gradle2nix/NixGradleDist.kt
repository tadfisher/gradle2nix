package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import java.io.Serializable

open class NixGradleDist : DefaultTask() {
    @Input
    internal val gradleDist = project.objects.property<GradleDist>()

    @OutputDirectory
    val outputDir = project.objects.directoryProperty()

    @OutputFile
    val outputFile = project.objects.fileProperty()
        .convention(outputDir.file("gradle-dist.json"))

    @TaskAction
    fun run() {
        if (gradleDist.isPresent) {
            outputFile.asFile.get().also { it.parentFile.mkdirs() }.sink().buffer().use { out ->
                moshi.adapter(GradleDist::class.java)
                    .indent("  ")
                    .toJson(out, gradleDist.get())
            }
        }
    }
}

@JsonClass(generateAdapter = true)
internal data class GradleDist(
    val version: String,
    val type: String,
    val url: String,
    val sha256: String,
    val nativeVersion: String
) : Serializable