{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  gradleEnvJson = builtins.fromJSON (builtins.readFile ./gradle/nix/gradle-env.json);
  gradleDist = builtins.fromJSON (builtins.readFile ./gradle/nix/gradle-dist.json);

  mkGradleEnv = callPackage ./gradle-env.nix {};
  gradle = pkgs.gradleGen.gradleGen {
    name = "gradle-dist-${gradleDist.version}-${gradleDist.type}";
    src = pkgs.fetchurl {
      inherit (gradleDist) url sha256;
    };
    inherit (gradleDist) nativeVersion;
  };

  maven = r: ''maven { url = uri("${r}") }'';

  projects = lib.mapAttrsToList (path: envs: {
    inherit path;
    config = ''
      buildscript {
        repositories {
          clear()
          ${maven (mkGradleEnv envs.buildscript)}
        }
      }
      repositories {
        clear()
        ${maven (mkGradleEnv envs.project)}
      }
    '';
  }) gradleEnvJson;

  initScript = pkgs.writeText "init.gradle" ''
    gradle.settingsEvaluated {
      it.pluginManagement.repositories {
        clear()
        ${maven (mkGradleEnv gradleEnvJson.":".plugins)}
      }
    }
    gradle.projectsLoaded {
    ${lib.concatMapStringsSep "\n" (p: ''
      rootProject.project("${p.path}") {
        ${p.config}
      }
    '') projects}
    }
  '';

in stdenv.mkDerivation rec {
  name = "gradle2nix-${version}";
  version = "1.0";

  src = ./.;

  nativeBuildInputs = [ gradle ];

  buildPhase = ''
    export GRADLE_USER_HOME=$(mktemp -d)
    gradle --offline --no-daemon --info --full-stacktrace --init-script ${initScript} installDist
  '';

  installPhase = ''
    mkdir -p $out
    cp -r app/build/install/gradle2nix/* $out/
  '';

  dontStrip = true;
}
