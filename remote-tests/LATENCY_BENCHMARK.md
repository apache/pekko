# Artery latency benchmark

`LatencySpec` is a two-node measurement tool, not a transport correctness or
capacity guarantee. Run it with `remote-tests / MultiJvm / testOnly org.apache.pekko.remote.artery.LatencySpec` and use the settings documented in its
`MultiNodeConfig` to choose duration, repetitions, and message representation.

Each round sends at fixed deadlines relative to its start. A delayed generator
does not move later deadlines: it catches up without waiting until it reaches the
schedule again. This deliberately models an open-loop offered load and can
produce a burst after a pause. Compare achieved send rate with the requested
rate; increasing repetitions does not adapt the requested rate.

Three distributions are reported in microseconds:

- **RTT**: from the actual start of message construction/sending until the echo
  is processed. This remains the metric used by the CSV plots.
- **Scheduled-to-echo**: from the planned send deadline until the echo is
  processed. This includes sender scheduling lateness that RTT alone does not.
- **Send lateness**: from the planned deadline until message construction/sending
  actually starts. This helps distinguish generator stalls from the round trip.

The distributions retain long-pause samples rather than dropping samples beyond
a fixed histogram limit. The extra timestamp bookkeeping and receiver-side histogram updates add some
measurement overhead; do not interpret a difference from older benchmark runs
as a transport performance change without controlling for that overhead.

With `real-message=on`, the existing `TestMessageSerializer` is configured and
echoed `TestMessage` IDs must arrive in sequence; a gap,
duplicate, or invalid payload size fails the round rather than publishing its
results. The default raw byte mode validates payload length but still associates
samples by arrival order. It does **not** validate payload contents or detect
same-size duplicate messages. Each repetition uses a fresh receiver.

The focused `LatencyMeasurementsSpec` tests exercise the timing arithmetic and
sequence checks using explicit times, without relying on machine load or sleeps.
A real two-node run remains necessary for performance conclusions.
