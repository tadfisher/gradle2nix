with (import <nixpkgs> {});
let
  buildGradle = callPackage ./gradle-env.nix {};
in
  buildGradle {
    envSpec = ./gradle-env.json;
    src = ./.;
    gradleFlags = [ "tasks" ];
  }