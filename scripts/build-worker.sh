#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
stage="$(mktemp -d)"
trap 'rm -rf -- "$stage"' EXIT
mkdir -p "$stage/classes" "$project_dir/dist"
# Keep Weka's classes AND resources; no external classpath is needed at runtime.
(cd "$stage/classes" && jar xf "$project_dir/lib/weka-3.7.0.jar")
javac --release 8 -cp "$project_dir/lib/weka-3.7.0.jar" \
    -sourcepath "$project_dir/src" -d "$stage/classes" \
    "$project_dir/src/WorkerClient.java"
cp "$project_dir/LICENSE" "$stage/classes/META-INF/PLATFORM-LICENSE.txt"
cp "$project_dir/README.md" "$stage/classes/META-INF/PLATFORM-README.md"
jar cfe "$project_dir/dist/worker-client.jar" WorkerClient -C "$stage/classes" .
echo "Built $project_dir/dist/worker-client.jar (Java 8+ runtime; Weka bundled)"
