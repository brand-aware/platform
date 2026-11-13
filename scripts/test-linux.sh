#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
command -v javac >/dev/null || { echo 'Install a Linux JDK first: sudo apt install default-jdk-headless' >&2; exit 1; }
java -version
mkdir -p diagnostics/network-fixes/linux-classes diagnostics/network-fixes/linux-run
javac -cp 'lib/*' -d diagnostics/network-fixes/linux-classes src/*.java diagnostics/network-fixes/NetworkRegression.java diagnostics/network-fixes/SchedulingRegression.java
cd diagnostics/network-fixes/linux-run
java -Djava.awt.headless=true -cp '../linux-classes:../../../lib/*' NetworkRegression "$@" 2>&1 | tee ../linux-test.log
java -Djava.awt.headless=true -cp '../linux-classes:../../../lib/*' SchedulingRegression 2>&1 | tee ../scheduling-test.log
