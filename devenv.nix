{ pkgs, lib, config, inputs, ... }:

let
  pkgs-unstable = import inputs.nixpkgs-unstable {
    system = pkgs.stdenv.system;
  };
in

{
  name = "ar0";
  env.AR0_ENV = "development";
  env.AR0_ENDPOINT = "http://localhost:7780/";

  packages = [
    pkgs.yq-go
    pkgs.git
    pkgs-unstable.k9s
    pkgs-unstable.kubectl
    pkgs-unstable.kubectx
    pkgs-unstable.just
    pkgs-unstable.scala-cli
  ];

  languages.java = {
    enable = true;
    jdk.package = pkgs-unstable.jdk25_headless;
  };

  env = {
    JAVA_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED ";
    SBT_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED ";
    KUBECONFIG="./ogrodje-one-config";
  };

  enterShell = ''
    echo JAVA_HOME=$JAVA_HOME
    export PATH=$PATH
    kubens discodigg-prod
  '';

  enterTest = ''
  '';
}
