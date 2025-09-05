{ pkgs, lib, config, inputs, ... }:

{
  name = "discodigg";

  env.NIX_ENFORCE_PURITY = 0;
  env.RUST_BACKTRACE = "full";
  env.RUST_LOG = "info";

  packages = [ 
    pkgs.git
  ];

  languages.rust = {
    enable = true;
    channel = "stable";
    version = "1.89.0";
  };

  enterShell = ''
    echo "~~~ discodigg ~~~"
    echo "Rust version: $(rustc --version)"
    echo "Cargo version: $(cargo --version)"
    echo "RUST_SRC_PATH: $RUST_SRC_PATH"
  '';

  enterTest = ''
    cargo test
  '';
}
