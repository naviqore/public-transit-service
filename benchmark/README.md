# Naviqore Benchmark

Measures a change of the libraries on a GTFS feed: whether it changes the answers, whether it is faster, where the
time goes, and how long the service takes to start. A change is measured as two versions, a git reference and the
working tree, side by side in one JVM.

## Requirements

- A JDK 25 (`JAVA_HOME`) and git; the script builds versions with the Maven wrapper of the repository.
- Memory: the default heap of 14 GB holds two versions of the Swiss national feed. For a small feed, set
  `BENCHMARK_HEAP=2g`.
- A quiet machine for `timing`: run one benchmark at a time and close what else is busy. The summary warns when the
  load average exceeded the number of processors.

## Quick start

```bash
benchmark/bench.sh compare main    # the answers of main and of the working tree, query by query
benchmark/bench.sh timing main     # their times, interleaved, in both load orders
benchmark/bench.sh profile         # where the working tree spends the time of each workload
benchmark/bench.sh load            # where it spends the time from the feed to a ready service, step by step
```

Run offline with `MAVEN_ARGS=-o`, and on a small feed with `--feed SAMPLE_FEED_1`.

## Commands

| Command                        | What it measures                                                                                                         | Defaults                                    |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| `compare BASELINE [CANDIDATE]` | The answers of two versions to the same queries, query by query                                                          | workload `answers`                          |
| `timing BASELINE [CANDIDATE]`  | The time candidate / baseline with a 95 % confidence interval, in wall and in CPU time                                   | workloads `connections,isolines`, 10 rounds |
| `profile [VERSION]`            | A JFR recording of each workload, with its hot methods                                                                   | workloads `connections,isolines`, 3 rounds  |
| `jfr RECORDING [METHOD...]`    | The hot methods of a recording; or, for each method given (e.g. `RouteScanner.scan`), its callees by line and its leaves | –                                           |
| `load [VERSION]`               | Reading the feed and building the service: time, heap and the steps the libraries log, with a JFR recording              | –                                           |
| `run [VERSION]`                | The time, CPU time, allocation and answers of every query, in several runs                                               | all but `answers`, 3 runs                   |

A version is a git reference: a branch, a tag or a commit. Without one, it is the working tree, which is also the
default candidate.

Options: `--feed DATASET|ZIP`, `--workloads NAME,...`, `--rounds N`, `--warmups N` (2), `--runs N`,
`--output DIRECTORY` (`benchmark/output`). Environment: `JAVA_HOME`, `BENCHMARK_HEAP` (14g), `MAVEN_ARGS`.

## Feeds

`--feed` takes a dataset of `GtfsScheduleDataset` from `naviqore-gtfs-sample` by name: `SWITZERLAND` (the default),
`GERMANY`, `ZURICH`, `ZURICH_TRAMS` or `SAMPLE_FEED_1`. A dataset is downloaded once into
`benchmark/input/<name>.zip`; delete the file to get a newer one. `--feed` also takes the path of any GTFS ZIP file.
The remote datasets always serve the latest feed, so to compare measurements across weeks, keep a file and pass its
path.

## Workloads

The queries are drawn from the timetable of the baseline with fixed seeds, so that every version and every run gets
the same queries: dates from its validity, hubs found by name and stops drawn at random.

| Workload             | Queries                                                                                                                                                                                                                                                  |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `answers`            | 1 504 on the Swiss feed: connections at three times of a winter weekday, a Saturday, a Sunday, New Year's Day, the changes of daylight saving time, the first and last day and a day after; every query configuration; from and to coordinates; isolines |
| `connections`        | 450 connections between random stops on a winter weekday at 07:30, 12:30 and 17:30                                                                                                                                                                       |
| `isolines`           | 20 isolines from random stops on a winter weekday at 08:00                                                                                                                                                                                               |
| `window-isolines`    | 5 isolines with a time window of 60 minutes from hubs, departing and arriving                                                                                                                                                                            |
| `window-connections` | 48 connections with a time window of 60 minutes between hubs and between random stops, departing and arriving                                                                                                                                            |

The hubs are stations of the Swiss feed; on another feed, a name no stop has draws a random stop.

## How it measures

- **Versions.** The script builds a version with Maven into one jar of this module with all it depends on, once per
  commit, cached in `benchmark/versions/`. A commit from before the benchmark is built with the benchmark module of the
  working tree. Each version runs in a class loader of its own and is called through `Subject`, whose parameters and
  results are JDK types only. When the API of the libraries changes, adapt `ServiceSubject` in the same commit.
- **compare** sorts every query into: the same answer; the same answer in another order (equally good connections,
  which also differ between runs of one version); fixed, still failing, failing differently or a new failure; or
  different, and how: stops reached earlier, later, no longer, with fewer walks or on other legs (over a time window,
  faster or slower instead of earlier or later), and connections better, worse, in another number, or at the same
  times shorter or longer. Over a time window, connections are the set of all that no other one beats, and the outcome
  names the connections of the candidate that beat or are beaten by those of the baseline, the new and the missing
  ones, and those beaten by its own or outside the window.
- **timing** runs randomized multiple interleaved trials
  ([Abedi and Brecht, ICPE 2017](https://research.spec.org/icpe_proceedings/2017/proceedings/p287.pdf)): every round
  runs all queries of a workload on both versions, in an order drawn anew each round, after rounds to warm up. Two JVMs
  load the versions in either order, since the version loaded second runs differently. The ratio is the geometric mean
  over both orders, with a 95 % bootstrap interval over the rounds; `~` means that the interval contains 1, as
  [benchstat](https://pkg.go.dev/golang.org/x/perf/cmd/benchstat) reports it. Isolines with a time window route on
  several threads, which only the wall time accounts for.
- **profile** and **load** record JFR with an execution sample every millisecond: `profile` the queries of each
  workload in a recording of its own, after a round to warm up, and `load` reading the feed and building the service.
  `jfr` reads any recording with execution samples, also one made outside the benchmark.

## Outputs

Every command writes to `benchmark/output/<feed>/<timestamp>_<name>`: `compare.csv`; `timing.csv` and the rounds of
each load order; `profile_<workload>.jfr` and its `profile_<workload>_hot_methods.csv`; `load.csv`, `load.jfr` and
`load_hot_methods.csv`; `run_results.csv` and `run_summary.csv`. The log shows a summary of each.

## From an IDE

Run `org.naviqore.benchmark.Benchmark` with the command and its arguments. For the commands of one version, the version
`classpath` is the code of the IDE, without a jar, e.g. `run classpath --feed SAMPLE_FEED_1`. `compare` and `timing`
need versions built by the script.
