#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"
mkdir -p bin
javac -cp 'lib/*' -d bin src/*.java
worker_dir="${WORKER_DIR:-$project_dir/worker-data}"
mkdir -p "$worker_dir"
cd "$worker_dir"
exec java -Djava.awt.headless=true -cp "$project_dir/bin:$project_dir/lib/*" WorkerClient "${1:-6003}" "${2:-0.0.0.0}"
