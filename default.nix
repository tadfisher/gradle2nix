{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  buildGradle = callPackage ./gradle-env.nix {};

  gradle2nix = buildGradle {
    envSpec = ./gradle-env.json;

    src = lib.cleanSourceWith {
      filter = lib.cleanSourceFilter;
      src = lib.cleanSourceWith {
        filter = path: type: let baseName = baseNameOf path; in !(
          (type == "directory" && (
            baseName == "build" ||
            baseName == ".idea" ||
            baseName == ".gradle"
          )) ||
          (lib.hasSuffix ".iml" baseName)
        );
        src = ./.;
      };
    };

    gradleFlags = [ "installDist" ];

    installPhase = ''
      mkdir -p $out
      cp -r app/build/install/gradle2nix/* $out/
    '';

    passthru = {
      plugin = "${gradle2nix}/share/plugin.jar";
    };
  };

in gradle2nix
