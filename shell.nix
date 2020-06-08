with import <nixpkgs> {};
stdenv.mkDerivation {
  name = "hook";

  # The packages in the `buildInputs` list will be added to the PATH in our shell
  # See https://nixos.org/nixos/packages.html for available packages.
  buildInputs = with pkgs; [
    openjdk11_headless
    clojure
    clj-kondo
    git

    # misc shell
    less  # for git paging
    tree

  ];

  shellHook = ''
  '';
}
