# ulid

A small monotonic ULID generator for Clojure.

## Usage

```clojure
(require '[hden.ulid :refer [ulid]])

(ulid)
;; => "01..."
```

Clojure CLI:

```clojure
com.github.hden/ulid {:mvn/version "0.1.0"}
```

## Testing deterministic behavior

`with-context` can override the clock and random source. Each context has an
independent monotonic state.

```clojure
(require '[hden.ulid :refer [ulid with-context]])
(import '[java.time Clock Instant ZoneOffset]
        '[java.util Random])

(with-context
  {:clock (Clock/fixed (Instant/ofEpochMilli 0) ZoneOffset/UTC)
   :random (Random. 0)}
  (ulid))
```

The defaults are `Clock/systemUTC` and `SecureRandom`.

Monotonicity is scoped to one JVM generation context. Overlapping concurrent
calls are linearized by an Atom CAS; their wall-clock return order is not a
defined ordering. Separate JVMs are not coordinated.

## Development

```shell
clojure -X:test
clojure -T:build jar
```

To publish to Clojars, set `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` (a deploy
token), update `VERSION`, then run:

```shell
clojure -T:build deploy
```

## License

MIT
