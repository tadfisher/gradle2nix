{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  buildGradle = pkgs.callPackage ./gradle-env.nix {};

in buildGradle {
  envSpec = ./gradle-env.json;

  src = ./.;

  gradleFlags = [ "installDist" ];

  installPhase = ''
    mkdir -p $out
    cp -r app/build/install/gradle2nix/* $out/
  '';
}
