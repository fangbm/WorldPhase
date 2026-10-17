# B3 native preflight execution plan

Refs #36 / #34. Base: `5a1edecdce908debe1e12dde3c818e579610772f`.
The r2 manifest and protocol are immutable. Digest:
`82a3e9676d7de3e223299feff88c7a9ea67e8e4e1c25bb1f3aa134deb59ec9bf`.
No V2 dimension/settings resources are installed. No holdout query, FULL request or screenshot.

## Execution and stop contract

- Same three development seeds, two windows, 132 IDs, exact dyadic shortlist ranking,
  <=16 detailed nodes, E/W-first then N/S. No replacement after rejection.
- Point Hc uses actual seed-wired native integer continentalness as the stored real-model
  reference, bilinear extension and the existing GradientNoise. A bin is accepted only
  when an outward interval including program error has exactly one floor.
- Terminal proof partitions the entire closed rectangle into covering integer cells.
  Bilinear reference ranges/edge derivatives are continuous-cell bounds, not sparse
  sample slopes. GradientNoise uses a proof-only eight-gradient polynomial with interval
  automatic derivatives and independently inherited roundoff bounds. Actual scalar
  sampling still uses the production kernel unchanged.
- Smooth nearest-segment/Gaussian uses only dry anchors; interval projection/clamp/min
  derivative hulls and error propagation precede port eligibility. No feedback from
  port failures into the valley graph. Ridge uses the frozen directional sin/exp model.
- A failed enclosure is a failure to certify, not proof of actual dangerous terrain.
  Early rejection records how many terminal cells were proved; unexamined cells stay
  unexamined. Accepted ports require all cells and all frozen inequalities.
- Cold RandomState/context initialization is recorded and checked as its own first
  slice. Work slices soft-stop at 8ms, hard-stop at 25ms; max 2048 raw/slice,
  CPU 1500ms, scheduled wall 15000ms, 256 slices including initialization,
  allocation 256MiB, retained owned graph 32MiB. Per-call overrun is detected on return.
- Profile failure invalidates all provisional ready nodes. No further certification
  runs for that profile. Other seeds are separate predeclared profiles, not retries.
- Retained memory uses Instrumentation object sizes plus identity traversal of the owned
  graph; borrowed native reference, Thread and clock are excluded. Shared immutable
  metadata is included conservatively. Diagnostic graph measurement after failure is
  labelled separately and does not resume preparation. Whole-process heap delta is not
  used as retained context memory.
- Server CPU sums only preparation invocations/initialization, not unrelated legacy
  server ticks. Scheduled wall includes tick gaps. Cold workers have no server tick gaps;
  their slices must not be called real server ticks. Registry/lookup/bootstrap time is
  separately disclosed, not silently labelled profile CPU.

## Numerical/native evidence limits

The dev-only `worldphase:prototype_v2/analytic_r2` MapCodec is strict. It retains the
unchanged r1 core detail NoiseHolders (0/[1]) because r2 changes shared-node geometry,
not the core N3 noise definition. Raw seed/context are not encoded. Child/NoiseHolder
mapAll and two visitor orders use the native RandomState constructor wrapper.

Fnative substitutes Dctrl at the three pinned sloped_cheese references. The protected
solid branch is post(slide(Dctrl)), selected AFTER noodle. Preliminary surface uses
that same no-cave controlled branch. No outside interpolated wrapper is introduced.

Native operator tests may inject an explicitly synthetic prepared context into an
in-memory RandomState router. Real native noise holders, interpolation and base columns
remain real; this proves operator/codec conformance only, never real node yield or
FULL safety. Full radial/mask/detail/interpolation program-error certification remains
an independent gate; tests alone cannot declare it proven.

Every seed/window needs >=2 published MATH_NODE_READY nodes. Incomplete preparation
is FAIL_INCOMPLETE_PREPARATION, not a measured zero natural yield. Full safety,
actual +/-4 Shift, routes and visual acceptance are NOT_RUN. No outcome changes
execution_authorized=false or grants world-generation permission.

## Reproduction (source commit required)

```sh
./gradlew test --tests '*R2MathTest' --tests '*R2NativeTest'
# Each invocation starts a fresh test worker; do not reuse an existing report path.
./gradlew test --tests '*R2ColdPreflightTest' -Pr2ColdSeed=0 -Pr2SourceCommit=<source>
./gradlew test --tests '*R2ColdPreflightTest' -Pr2ColdSeed=1 -Pr2SourceCommit=<source>
./gradlew test --tests '*R2ColdPreflightTest' -Pr2ColdSeed=2 -Pr2SourceCommit=<source>
./gradlew runPhaseVerificationR2PreflightServer -Pr2SourceCommit=<source>
./gradlew coreRegressionAll build
python tools/check_v2_preflight.py --require-pass  # r1 stays nonzero
```

Outputs: ignored `build/reports/r2-native-preflight/`, and
`run/r2-native-preflight/legacy-host/r2-reports/`. Existing reports must be archived
before another run; negative attempts are never overwritten. The dedicated server
creates ONLY an unchanged legacy host world; the three seed profiles use in-memory
vanilla RandomStates and do not install V2 settings or request FULL chunks.
