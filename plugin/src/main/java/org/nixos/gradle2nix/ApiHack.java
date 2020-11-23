package org.nixos.gradle2nix;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;

import javax.annotation.Nullable;

/**
 * Workarounds for APIs improperly marked with @NonNullApi.
 */
interface ApiHack {
    static Dependency defaultExternalModuleDependency(String group, String name, @Nullable String version) {
        return new DefaultExternalModuleDependency(group, name, version);
    }
}
