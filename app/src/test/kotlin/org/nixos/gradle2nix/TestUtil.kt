package org.nixos.gradle2nix

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.buffer
import okio.source
import org.spekframework.spek2.dsl.Root
import strikt.api.expectThat
import strikt.assertions.exists
import strikt.assertions.isNotNull
import strikt.assertions.toPath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory

private val moshi = Moshi.Builder().build()

class Fixture(val project: Path) {
    private val app = Main()

    fun run(vararg args: String) {
        app.main(args.toList() + project.toString())
    }

    fun env(): Map<String, NixGradleEnv> {
        val file = (app.outDir ?: project.toFile()).resolve("${app.envFile}.json")
        expectThat(file).toPath().exists()
        val env = file.source().buffer().use { source ->
            moshi
                .adapter<Map<String, NixGradleEnv>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, NixGradleEnv::class.java)
                ).fromJson(source)
        }
        expectThat(env).isNotNull()
        return env!!
    }
}

@OptIn(ExperimentalPathApi::class)
fun Root.fixture(name: String) {
    val fixture by memoized(
        factory = {
            val url = checkNotNull(Thread.currentThread().contextClassLoader.getResource(name)?.toURI()) {
                "$name: No test fixture found"
            }
            val fixtureRoot = Paths.get(url)
            val dest = createTempDirectory("gradle2nix")
            val src = checkNotNull(fixtureRoot.takeIf { Files.exists(it) }) {
                "$name: Test fixture not found: $fixtureRoot"
            }
            src.toFile().copyRecursively(dest.toFile())
            Fixture(dest)
        },
        destructor = {
            it.project.toFile().deleteRecursively()
        }
    )
}