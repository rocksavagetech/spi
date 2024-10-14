#!/bin/sh

git submodule init
cd nix
nix develop --extra-experimental-features 'nix-command flakes' -c $SHELL
cd ..