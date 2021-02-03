package org.nixos.gradle2nix

import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.artifacts.RepositoriesSupplier
import org.gradle.kotlin.dsl.newInstance
import org.gradle.util.GradleVersion
import javax.inject.Inject

interface RepositoriesCollector {
    fun collectRepositories(): List<ResolutionAwareRepository>

    companion object {
        fun create(project: Project): RepositoriesCollector =
            if (GradleVersion.current() >= GradleVersion.version("6.8")) {
                project.objects.newInstance<RepositoriesCollector68>()
            } else {
                project.objects.newInstance<RepositoriesCollectorBase>()
            }
    }
}

open class RepositoriesCollectorBase @Inject constructor(
    private val repositories: RepositoryHandler
): RepositoriesCollector {
    override fun collectRepositories(): List<ResolutionAwareRepository> =
        repositories.filterIsInstance<ResolutionAwareRepository>()
}

open class RepositoriesCollector68 @Inject constructor(
    private val repositoriesSupplier: RepositoriesSupplier
): RepositoriesCollector {
    override fun collectRepositories(): List<ResolutionAwareRepository> =
        repositoriesSupplier.get()
}
