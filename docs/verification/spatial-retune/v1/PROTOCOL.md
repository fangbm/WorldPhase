# WPH-016-01 legacy baseline protocol (frozen before measurement)

Base main17f45a5, MC26.1/NeoForge26.1.0.19-beta/Java25.0.3+9. This is a
measurement-only baseline; no new terrain, environment or route utility is claimed.
`protocol.json` fixes seeds, windows, grids, camera and all production input hashes.
Never replace failed/visually bland samples. Holdouts606102026,-77331057,
424242424242 are reserved for later accepted prototype PRs, not tuned here.

Three seeds:123456789,8675309,20261011. Two fixed windows per seed at the same
coordinates in Smooth/Ridge/Cell/Fracture and Overworld control:
forest(-384,896), highland(1344,1200). Both contain earlier observations, including
failures; they are not selected after seeing these metrics. Whole2048-square grids
remain in the results, including water/boring regions. Report a separate inland
stratum using **Overworld** base height>=70, never filtering by the tested Phase.
This stratum is a dry-height proxy, not a complete biome/continentalness classifier.

- Coarse base height:33x33,64-block step. Fine local height:17x17,16-block step.
  Real TerrainSampleService for Phases; native generator for Overworld control.
  Heights exclude surfaces/features/structures, measured top solid y, not water.
- Base-column x/y and z/y sections:1024 blocks,8-horizontal/1-vertical resolution,
  full -64..319. Native getBaseColumn, no FULL requests. Keep air/water/lava/solid
  distinct; this is not a decorated/carved FULL world or actual safe-landing proof.
- Wired final-router density planes: same horizontal extent/8 step, y step8.
  Local3D density volume:128x128,-32..160,8-block samples. Plot orthogonal planes
  with common density scale and zero contour. **Raw router compute bypasses the
  NoiseChunk interpolation wrappers**, so label it separately from base blocks.
-27 diagnostic probes per world/window use native getInterpolatedNoiseValue at
  dx/dz1,17,33 and y33,65,97. Record raw/interpolated differences and source API.
- Algebra attribution at nine offsets(-512,0,512)^2 and y=baseHeight+(-8,0,8):
  independently wired G0,Gphase,B3 and Tphase verify gradient replacement. Never
  infer the Vanilla contribution from the name/addition alone.
- Cached repeated coordinates, sequential server-thread operations,512-item and
  25ms target per-tick budget. A single native column may exceed the target;
  report maximum work tick and wall/CPU time. No background Level sampling.

Metrics fixed before data: common-scale height/slope maps, p5/p50/p95/std/relief,
16/64-block finite differences, local3x3 relief, height correlation and absolute
pairwise delta/<=4 proxy (not legal Shift rate); gradient tensor anisotropy and
high-ground connected components; plateau/flat-edge fraction; steep-edge density;
base-column solid/air transitions; raw3D volume sign fractions and interpolation
probe differences. No existing-world safety or completed-route claim from these.

Native capture:30 images,2 cameras x5 worlds x3 seeds. Fixed y240,yaw135,pitch60,
FOV70,1280x720,noon6000,clear/frozen day/weather,view16/simulation5,Fancy基础配置（额外覆盖后UI显示CUSTOM）,cloudsOFF,
gamma0.5,particlesMinimal,Vanilla resources/no shader. New isolated worlds only.
At least360 client ticks, inner10-chunk circle loaded, render compilation settled
20 ticks. Capture actual settings/time/weather/position. Timeout or occluded
camera is a failed observation, never silently changed per Phase. In that event
retain failure and propose a new whole-window camera protocol for review.

Native images are evidence, plots are quantitative diagnostics, future architecture
schematics are proposals. Keep these categories labelled. Environmental identity
and completed gameplay routes have independent future gates. Current WPH-015
RETUNE/0 ROUTE_COMPLETED/Phase2NOT_APPROVED remains unchanged.

Implementation clarification (no coordinate/workload/threshold change): the 26.1
Fancy preset resets simulation distance to12. The first native attempt was stopped
and excluded; its partial images/report remain in ignored run history. Apply Fancy
first, then override simulation5/cloudsOFF/particlesMINIMAL. The UI consequently
reports CUSTOM; archive the actual subsettings rather than relabelling it FANCY.

Camera coordinates specify the native player pose in first-person spectator mode:
y240 is feet position, with the ordinary standing eye offset (not a detached
free-camera or a terrain-relative height). All worlds use the same pose/rotation.
