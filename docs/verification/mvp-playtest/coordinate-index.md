# Fixed coordinate index

All 120 columns were measured in real dedicated worlds; all ten x/z in all four Phases have native client screenshots. Base is TerrainSampleService; FULL is MOTION_BLOCKING_NO_LEAVES (water and structures may differ). Cameras use x/z+0.5,y220,yaw135,pitch45,view16; outer streaming edges are not certified terrain defects.

| Seed | Point | x | z | Fracture base/FULL | Smooth base/FULL | Ridge base/FULL | Cell base/FULL |
| --- | ---: | ---: | ---: | --- | --- | --- | --- |
| 123456789 | 0 | 464 | -128 | 62/63 | 54/63 | 64/65 | 35/63 |
| 123456789 | 1 | 480 | -160 | 54/63 | 54/63 | 65/66 | 35/63 |
| 123456789 | 2 | -400 | -1312 | 123/124 | 71/72 | 104/105 | 54/63 |
| 123456789 | 3 | -1376 | -80 | 107/108 | 61/63 | 69/70 | 42/63 |
| 123456789 | 4 | 416 | -1392 | 11/63 | 59/63 | 42/63 | 159/160 |
| 123456789 | 5 | 1280 | -1488 | -11/63 | 45/63 | 20/63 | 137/138 |
| 123456789 | 6 | 1344 | 1200 | 63/64 | 85/86 | 119/120 | 160/161 |
| 123456789 | 7 | -128 | -656 | 85/86 | 77/78 | 102/103 | 173/174 |
| 123456789 | 8 | -384 | 896 | 67/68 | 63/64 | 88/89 | 113/114 |
| 123456789 | 9 | 128 | 128 | 70/71 | 66/67 | 67/68 | 41/63 |
| 8675309 | 0 | 464 | -128 | 86/87 | 51/63 | 58/63 | 48/63 |
| 8675309 | 1 | 480 | -160 | 76/77 | 55/63 | 63/64 | 79/80 |
| 8675309 | 2 | -400 | -1312 | 79/80 | 58/63 | 99/100 | 39/63 |
| 8675309 | 3 | -1376 | -80 | 3/63 | 56/63 | 55/63 | 109/110 |
| 8675309 | 4 | 416 | -1392 | 55/63 | 77/78 | 118/119 | 48/63 |
| 8675309 | 5 | 1280 | -1488 | 116/117 | 63/64 | 54/63 | 41/63 |
| 8675309 | 6 | 1344 | 1200 | 38/63 | 54/63 | 41/63 | 66/67 |
| 8675309 | 7 | -128 | -656 | -3/63 | 54/63 | 22/63 | 37/63 |
| 8675309 | 8 | -384 | 896 | 80/81 | 79/80 | 88/89 | 170/171 |
| 8675309 | 9 | 128 | 128 | 26/63 | 62/63 | 79/80 | 85/86 |
| 20261011 | 0 | 464 | -128 | -1/63 | 52/63 | 65/66 | 36/63 |
| 20261011 | 1 | 480 | -160 | 4/63 | 51/63 | 51/63 | 45/63 |
| 20261011 | 2 | -400 | -1312 | -12/63 | 66/67 | 52/63 | 164/165 |
| 20261011 | 3 | -1376 | -80 | -1/63 | 71/72 | 64/65 | 43/63 |
| 20261011 | 4 | 416 | -1392 | 73/74 | 64/65 | 60/63 | 37/63 |
| 20261011 | 5 | 1280 | -1488 | 32/63 | 62/63 | 91/92 | 40/63 |
| 20261011 | 6 | 1344 | 1200 | 86/87 | 56/63 | 37/63 | 91/92 |
| 20261011 | 7 | -128 | -656 | 91/92 | 64/65 | 85/86 | 46/63 |
| 20261011 | 8 | -384 | 896 | 15/63 | 55/63 | 90/91 | 39/63 |
| 20261011 | 9 | 128 | 128 | 11/63 | 55/63 | 109/110 | 38/63 |

Ignored full archive: `run/mvp-playtest/seed-N-client/screenshots/wph015-SEED-point-POINT-PHASE.png`. Curated native images here use point0 for123456789, point9 for8675309, point6 for20261011 plus the six village cameras. Only lossless PNG compression changes; no reconstructed images or raw recordings.

## Actual column surface snapshots

These are FULL-world top-block observations, not AABB/support or route safety certifications. Water at a column is a hazard indicator; dry grass/sand does not certify that a player can walk the surrounding slope.

| Seed | Point | Fracture | Smooth | Ridge | Cell |
| --- | ---: | --- | --- | --- | --- |
| 123456789 | 0 | sand (ocean) | water (ocean) | grass_block (ocean) | water (ocean) |
| 123456789 | 1 | water (ocean) | water (ocean) | grass_block (ocean) | water (ocean) |
| 123456789 | 2 | grass_block (birch_forest) | grass_block (birch_forest) | grass_block (birch_forest) | water (birch_forest) |
| 123456789 | 3 | grass_block (taiga) | water (taiga) | grass_block (taiga) | water (taiga) |
| 123456789 | 4 | water (stony_shore) | water (stony_shore) | water (stony_shore) | stone (stony_shore) |
| 123456789 | 5 | water (deep_ocean) | water (deep_ocean) | water (deep_ocean) | grass_block (deep_ocean) |
| 123456789 | 6 | dripstone_block (dripstone_caves) | stone (dripstone_caves) | grass_block (forest) | grass_block (forest) |
| 123456789 | 7 | grass_block (forest) | stone (dripstone_caves) | grass_block (forest) | grass_block (forest) |
| 123456789 | 8 | grass_block (old_growth_birch_forest) | grass_block (old_growth_birch_forest) | grass_block (old_growth_birch_forest) | grass_block (old_growth_birch_forest) |
| 123456789 | 9 | grass_block (forest) | grass_block (forest) | grass_block (forest) | water (forest) |
| 8675309 | 0 | grass_block (ocean) | water (ocean) | water (ocean) | water (ocean) |
| 8675309 | 1 | grass_block (ocean) | water (ocean) | sand (ocean) | grass_block (ocean) |
| 8675309 | 2 | stone (stony_shore) | water (stony_shore) | stone (stony_shore) | water (stony_shore) |
| 8675309 | 3 | water (lukewarm_ocean) | water (lukewarm_ocean) | water (lukewarm_ocean) | grass_block (lukewarm_ocean) |
| 8675309 | 4 | water (dripstone_caves) | grass_block (savanna) | grass_block (savanna) | water (dripstone_caves) |
| 8675309 | 5 | grass_block (plains) | sand (beach) | water (beach) | water (beach) |
| 8675309 | 6 | water (cold_ocean) | water (cold_ocean) | water (cold_ocean) | grass_block (cold_ocean) |
| 8675309 | 7 | water (river) | water (river) | water (river) | water (river) |
| 8675309 | 8 | grass_block (savanna_plateau) | grass_block (savanna_plateau) | grass_block (forest) | grass_block (savanna_plateau) |
| 8675309 | 9 | water (old_growth_birch_forest) | grass_block (old_growth_birch_forest) | grass_block (old_growth_birch_forest) | grass_block (old_growth_birch_forest) |
| 20261011 | 0 | water (deep_lukewarm_ocean) | water (deep_lukewarm_ocean) | grass_block (deep_lukewarm_ocean) | water (deep_lukewarm_ocean) |
| 20261011 | 1 | water (deep_lukewarm_ocean) | water (deep_lukewarm_ocean) | water (deep_lukewarm_ocean) | water (deep_lukewarm_ocean) |
| 20261011 | 2 | water (forest) | grass_block (forest) | water (forest) | grass_block (forest) |
| 20261011 | 3 | water (forest) | grass_block (forest) | grass_block (forest) | water (forest) |
| 20261011 | 4 | grass_block (plains) | grass_block (plains) | water (plains) | water (plains) |
| 20261011 | 5 | water (plains) | grass_block (plains) | grass_block (plains) | water (plains) |
| 20261011 | 6 | sand (warm_ocean) | water (warm_ocean) | water (warm_ocean) | sand (warm_ocean) |
| 20261011 | 7 | grass_block (sparse_jungle) | grass_block (sparse_jungle) | grass_block (sparse_jungle) | water (sparse_jungle) |
| 20261011 | 8 | water (mushroom_fields) | water (mushroom_fields) | mycelium (mushroom_fields) | water (mushroom_fields) |
| 20261011 | 9 | water (warm_ocean) | water (warm_ocean) | sand (warm_ocean) | water (warm_ocean) |
