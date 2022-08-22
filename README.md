# task-runner

Simple task runner with support for arbitrary grouping, nesting, and parallel execution.

## Requirements

The tool is designed to be run with [Babashka](https://babashka.org/).
Visit https://github.com/babashka/babashka#installation for installation instructions.

### Alternative runtimes

You could, in theory, leverage any Clojure runtime of choice - so long as you're able to satisfy the dependencies. The only package outside of the standard library is (babashka/process)[https://github.com/babashka/process].

## Basic usage

Assuming Babashka is installed and available globally, simply navigate to the runner's directory and run:

```sh
bb runner.bb
```

This will prompt a help doc explaining the syntax.

Before you can actually run any tasks, you must first define them. Either put them in `runner.edn` (`runner.windows.edn` under Windows) inside the runner's directory, or provide them directly using the `--edn` option. Tasks are encoded in [extensible data notation](https://github.com/edn-format/edn). See `runner.example.edn` to learn how to define them, and how they map to command line directives.

### Example

Assuming a following set of tasks:

```edn
{:dev {:clean "some task"
       :build "some other task"}
 :run-tests "another task"}
```

You could run a single task like so:

```runner.bb dev:clean```

An entire category, like so:

```runner.bb dev```

Or even combine the two:

```runner.bb dev run-tests```

---

Happy tasking!
