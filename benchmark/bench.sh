#!/usr/bin/env bash
# Measures versions of the libraries on a GTFS feed: builds each version from a git reference into one jar of the
# benchmark module with all it depends on, and runs a command of org.naviqore.benchmark.Benchmark on them. The working
# tree is a version too, and the default candidate. See README.md.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
VERSIONS=$ROOT/benchmark/versions
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
HEAP=${BENCHMARK_HEAP:-14g}
MAIN=org.naviqore.benchmark.Benchmark
SUBJECT=benchmark/src/main/java/org/naviqore/benchmark/subject/ServiceSubject.java

usage() {
    cat >&2 <<'EOF'
Usage: benchmark/bench.sh COMMAND [ARGUMENTS] [OPTIONS]

  compare BASELINE [CANDIDATE]   the answers of two versions, query by query
  timing BASELINE [CANDIDATE]    the times of two versions, interleaved, in both load orders
  profile [VERSION]              where a version spends the time of a workload, recorded with JFR
  load [VERSION]                 how long a version takes from the feed to a ready service, step by step
  run [VERSION]                  the times of a version, query by query
  jfr RECORDING [METHOD...]      the hot methods of a recording, or the samples inside methods

A version is a git reference (a branch, a tag or a commit); without one, the working tree.
Options:     --feed DATASET|ZIP  --workloads NAME,...  --rounds N  --warmups N  --runs N  --output DIRECTORY
Environment: JAVA_HOME (a JDK 25), BENCHMARK_HEAP (default 14g), MAVEN_ARGS (e.g. -o to build offline)
EOF
    exit 1
}

# builds the working tree, and prints the path of its jar: a copy of its own for this run, so that building again
# meanwhile does not change the classes under a running benchmark
build_working_tree() {
    echo "Building the working tree" >&2
    "$ROOT/mvnw" -q -f "$ROOT/pom.xml" package -pl benchmark -am -Dmaven.test.skip=true >&2
    mkdir -p "$RUN"
    cp "$ROOT"/benchmark/target/naviqore-benchmark-*-version.jar "$RUN/working-tree.jar"
    echo "$RUN/working-tree.jar"
}

# builds a git reference in a worktree of its own, once per commit, and prints the path of its jar; a commit from
# before the benchmark gets the subject and the benchmark module of the working tree
build_reference() {
    local commit jar worktree version
    commit=$(git -C "$ROOT" rev-parse --verify --quiet "$1^{commit}") || {
        echo "No such git reference: $1" >&2
        exit 1
    }
    jar=$VERSIONS/${commit:0:12}.jar
    if ! git -C "$ROOT" cat-file -e "$commit:$SUBJECT" 2>/dev/null; then
        jar=$VERSIONS/${commit:0:12}-$(cd "$ROOT" && find benchmark/pom.xml benchmark/src/main -type f | sort |
            xargs cat | shasum | cut -c1-8).jar
    fi
    if [[ -f $jar ]]; then
        echo "$jar"
        return
    fi

    echo "Building $1 (${commit:0:12})" >&2
    worktree=$(mktemp -d)
    git -C "$ROOT" worktree add --quiet --detach "$worktree" "$commit"
    if [[ ! -f $worktree/$SUBJECT ]]; then
        rm -rf "$worktree/benchmark/src"
        cp -R "$ROOT/benchmark/src" "$ROOT/benchmark/pom.xml" "$worktree/benchmark/"
        version=$(cd "$worktree" && ./mvnw -q -N help:evaluate -Dexpression=project.version -DforceStdout)
        perl -0pi -e "s{(<parent>.*?<version>)[^<]*(</version>)}{\${1}$version\${2}}s" "$worktree/benchmark/pom.xml"
    fi
    (cd "$worktree" && ./mvnw -q package -pl benchmark -am -Dmaven.test.skip=true) >&2
    mkdir -p "$VERSIONS"
    cp "$worktree"/benchmark/target/naviqore-benchmark-*-version.jar "$jar"
    git -C "$ROOT" worktree remove --force "$worktree"
    echo "$jar"
}

# the jar of a version: a git reference, linked under its name for this run, which outputs show; or the working tree
# if none is given
build_version() {
    if [[ -z ${1:-} ]]; then
        build_working_tree
        return
    fi
    local jar
    jar=$(build_reference "$1")
    mkdir -p "$RUN"
    ln -sf "$jar" "$RUN/${1//\//-}.jar"
    echo "$RUN/${1//\//-}.jar"
}

benchmark() {
    local heap=$1
    shift
    cd "$ROOT"
    "$JAVA" "-Xms$heap" "-Xmx$heap" -cp "$DRIVER" "$MAIN" "$@"
}

[[ $# -ge 1 ]] || usage
command=$1
shift
RUN=$VERSIONS/run-$$
trap 'rm -rf "$RUN"' EXIT

# the versions given before the options
references=()
while [[ $# -gt 0 && $1 != --* ]]; do
    references+=("$1")
    shift
done

case $command in
compare | timing)
    [[ ${#references[@]} -ge 1 && ${#references[@]} -le 2 ]] || usage
    baseline=$(build_version "${references[0]}")
    candidate=$(build_version "${references[1]:-}")
    DRIVER=$candidate
    benchmark "$HEAP" "$command" "$baseline" "$candidate" "$@"
    ;;
profile | load | run)
    [[ ${#references[@]} -le 1 ]] || usage
    version=$(build_version "${references[0]:-}")
    DRIVER=$version
    benchmark "$HEAP" "$command" "$version" "$@"
    ;;
jfr)
    [[ ${#references[@]} -ge 1 ]] || usage
    DRIVER=$(build_working_tree)
    benchmark 4g jfr "${references[@]}" "$@"
    ;;
*)
    usage
    ;;
esac
