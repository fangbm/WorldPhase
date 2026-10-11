# Global tutorial opening state — WPH-014

This persistence data layer follows the [Hybrid Opening decision](TUTORIAL_FEASIBILITY_DECISION.md):
**searched != shift-feasible != route-validated != selected**. WPH-013's failed
natural useful-majority gate remains failed. This issue implements no gameplay.

## Actual pinned 26.1 API

`WorldPhaseSavedData extends SavedData`, using a private
`SavedDataType<WorldPhaseSavedData>` with identifier `worldphase:tutorial_opening`,
NeoForge's level-aware factory/codec factory overload and null `DataFixTypes`.
A `Codec<TutorialOpeningState>` maps the immutable snapshot to SavedData;
`SavedDataStorage` supplies `RegistryOps<Tag>` over `NbtOps`, wrapping it in
`data` plus Minecraft `DataVersion`. No old `save(CompoundTag)` override,
`SavedData.Context`, custom registry or registration event is used.

`get(server)` / `get(anyServerLevel)` use **only `server.overworld().getDataStorage()`**.
One instance serves every Phase. Actual 26.1 file location:

```text
<world>/dimensions/minecraft/overworld/data/worldphase/tutorial_opening.dat
```

Even Overworld dimension data now lives under `dimensions/minecraft/overworld`.
26.1 also offers `MinecraftServer#getDataStorage()` at `<world>/data/`, recommended
for server-global data in the [official Saved Data docs](https://docs.neoforged.net/docs/datastorage/saveddata/).
We consistently use the requested Overworld ownership. Sources checked in the
pinned generated 26.1.0.19-beta jar: `SavedDataType`, `SavedDataStorage`,
`ServerChunkCache`, `DimensionType`, `MinecraftServer`. No version upgrade/hack.

## Schema 1

JSON representation of the NBT payload (example heights are illustrative, not
goldens or safe landing y coordinates):

```json
{
  "schema_version": 1,
  "tutorial_search_complete": true,
  "opening_choice": "CONTROLLED_OPENING_REQUIRED",
  "provenance": {
    "raw_seed": 123456789,
    "phase_pair": ["fracture", "smooth"],
    "center_x": 0, "center_z": 0,
    "search_config": {"radius": 512, "candidate_count": 64},
    "generation_version": 3, "scoring_protocol_version": 4,
    "minecraft_version": "26.1", "neoforge_version": "26.1.0.19-beta",
    "preset_identity": "UNVERIFIED"
  },
  "natural_candidate": {
    "x": -16, "z": 24,
    "fracture_base_height": 110, "smooth_base_height": 72,
    "evidence": "UNVERIFIED"
  }
}
```

Only the two metadata objects are optional. Completed search requires provenance;
unfinished search cannot carry provenance/candidate. Candidate must be within
the recorded radius. Natural choice requires candidate metadata. Pair is
strictly ordered Fracture/Smooth, radius 64..1024, candidate count 8..256.

Choices: `UNDECIDED`, `CONTROLLED_OPENING_REQUIRED`, `NATURAL_CANDIDATE_SELECTED`.
Evidence: `UNVERIFIED`, `BOUNDED_SHIFT_SNAPSHOT`; **no `ROUTE_VALIDATED` value**.
A bounded snapshot is a diagnostic claim about local checks, not a route or
current safety certificate. Unknown enum/claimed certification is rejected.
Metadata contains coordinates/base heights/provenance/evidence, not player
position/Phase, rotation, inventory, Level, time or cached scores. The SavedData
object retains runtime server thread context, excluded from serialization.

## Explicit transitions and scope

- Fresh: schema 1, incomplete search, UNDECIDED, no metadata.
- `completeSearch(provenance, optionalDiagnostic)` records an explicitly
  submitted completed search while preserving opening choice. Top-1 is never
  automatically selected. Empty results remain UNDECIDED until a separate decision.
- `requireControlledOpening()` explicitly chooses fallback before/after search,
  preserving diagnostics; it does not create a Station.
- Identical updates are no-ops. A different completed search/evidence cannot
  replace the first snapshot without a future explicit review/reset workflow.
- No natural-selection mutator exists: there is no independent full-route
  validation contract/issuer yet. Natural-selected intent can be represented for
  inspection/import, but no update method in this issue emits it. A structurally
  valid imported natural-choice record is retained, never automatically replaced
  with fallback, and remains unusable. `canUseNaturalOpening()` is always false.

Existing `/wp tutorial score/find` remains a development tool; it does not
automatically persist Top-1 or choose an opening. This issue supplies the data
layer and explicit isolated probe callers, not a selection workflow. Reading/
updating never searches, loads chunks, teleports or redirects spawn.

## Thread, dirty and invalidation

Every access/update checks `server.isSameThread()`. Validation precedes one
immutable snapshot swap and `setDirty()`. Initial `SavedDataStorage#set` marks
creation dirty; decoded state is clean. Identical updates and compatibility
queries never dirty. Vanilla schedules writes/clears dirty.

`currentProvenance` reads actual Overworld raw seed and loaded Minecraft/NeoForge
versions, using existing generation 3/protocol 4 constants. It records exact
search center/config. `validationIssues(expected)` reports seed, pair,
center/config, generation, scoring protocol and toolchain mismatches without
mutation. Expected context comes from intended current search inputs, not player
movement. Old evidence stays readable but stale seed/version updates fail, and
conflicting new searches cannot replace completed records.

**Preset identity remains unavailable.** `UNVERIFIED` explicitly records absence
of a trustworthy resolved-preset fingerprint, not a hand-written identity or
match. Even otherwise matching metadata reports `PRESET_IDENTITY_UNVERIFIABLE`
and `ROUTE_VALIDATION_UNAVAILABLE`, and cannot authorize natural gameplay.
Unknown-preset metadata may be saved only as diagnostics with controlled/undecided
intent. Future identity must cover actual resolved datapack/generator dependencies
and invalidate prior evidence through a reviewed schema change. Static terrain
or shift snapshots cannot guarantee future fluids/entities/support/damage;
future gameplay use still needs real-time transfer/landing checks.

Missing file creates safe undecided state. Unknown schema, malformed/incomplete
payload, corrupt optional metadata or unreadable NBT fails loading. Native
26.1 `SavedDataStorage` uses `resultOrPartial`, and returns null for failed or
missing data. The boundary codec strips error-bearing partial results: otherwise
a corrupt optional candidate could disappear during a seemingly successful load.
We use `get`, then create via `set` **only if `Files.notExists(actualOverworldPath)`
positively confirms absence**, avoiding `computeIfAbsent` overwriting failed
loads. Existing/inaccessible files remain untouched; access throws with the file
path. Inspect native errors and manually restore/review; no migration, deletion,
reset, silent re-search or rewrite. This does not promise protection against
external concurrent file edits or arbitrary disk/JVM failure. Vanilla logs write
errors; the real probe checks written bytes and independent reload too.

## Reproduce and evidence

```sh
./gradlew test --tests '*WorldPhaseSavedDataTest' --console=plain
./gradlew coreRegressionAll build --console=plain
./gradlew savedDataVerification --console=plain
```

After accepting Minecraft EULA, set `eula=true` in
`run/saved-data-verification-server/eula.txt`. The task uses loopback 25576,
seed 123456789, fresh isolated save per invocation and two sequential JVMs,
preserving earlier worlds. Ignored reports:
`run/saved-data-verification-server/saved-data-{cold,reload}.json`.
Missing/FAIL report or differing checkpoint causes nonzero Gradle failure.

Units use real codecs/compressed NBT/`SavedDataStorage` with server/Level shells
mocked. The real probe records actual Fracture/Smooth base heights as unverified
diagnostics and explicitly chooses controlled fallback. After save/stop, a new
JVM checks identical candidate/choice/provenance **before any search/update**.
It checks clean reload, all-Phase global identity, unchanged shared spawn, zero
players, unchanged loaded-FULL-chunk counts and byte-identical no-op saves.

This is an explicit dev fixture exercising search-complete/choice persistence,
not a new candidate-search quality experiment or proof of route validation.

### Executed 2026-10-11

Pinned Minecraft 26.1 / NeoForge 26.1.0.19-beta / Temurin 25.0.3+9-LTS.

| Command | Result |
| --- | --- |
| `test --tests '*WorldPhaseSavedDataTest' --console=plain --no-build-cache` | PASS, 31/31, 31s |
| `coreRegressionAll build savedDataVerification --console=plain --no-build-cache` | PASS, 3m30s; 361 MC tests + 15 plain dev tests, zero failures/errors/skips |

Existing core server: cold/reload 50 complete cycles each, 400 loop transfers /
456 total successes, 495 inventory checks, max x/z and yaw/pitch deviation 0,
max y correction 1, no duplicate player or dropped test item, persisted terrain/
marker/player restored. The production gameplay and scoring behavior is unchanged.

New SavedData server: cold and independent-JVM reload PASS at seed 123456789.
Exact retained snapshot: center(0,0), config(512,64), diagnostic(-16,24),
Fracture base height62 / Smooth60, evidence UNVERIFIED, search complete,
CONTROLLED_OPENING_REQUIRED, generation3/protocol4, preset UNVERIFIED. Clean
reload and byte-identical no-op saves passed. All four Phase accesses plus
Overworld returned the same instance; zero additional loaded FULL chunks, zero
players, shared spawn unchanged, natural opening unusable. No claimed safe
candidate or selected natural opening was manufactured for this test.

Ignored evidence: `build/wph-014-focused.log`, `build/wph-014-focused-results.xml`,
`build/wph-014-acceptance.log`, `build/test-results/{test,testNoiseProfiler}`,
`build/core-regression/summary.json`, and the two SavedData reports above.
Kernel/goldens/presets/domain/transfer/scoring math were not changed. No client
GUI acceptance or rerun of the negative 15-seed experiment is claimed/required.
