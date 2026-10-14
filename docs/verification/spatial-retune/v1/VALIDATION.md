# Validation record

## Production regression

`./gradlew coreRegressionAll build --console=plain --no-build-cache`：PASS（2026-10-14，67s）。

| suite | tests | failures/errors |
|---|---:|---:|
| test | 361 | 0 |
| testNoiseProfiler | 15 | 0 |

Dedicated core cold/reload各50个四Phase完整循环，共400次循环内成功传送；含准备/恢复合计456次。
两阶段均PASS，x/z及yaw/pitch最大偏差0、最大y修正1、duplicate player0、掉落测试物品0，
冷启动247/reload248次inventory identity/content检查；reload前恢复terrain/marker/原生player data一致。
传送使用真实ServerPlayer/Level/native pipeline、模拟出站packet；这不是native客户端路线验收。

## New baseline

`./gradlew spatialBaseline`：PASS，三个seed，6m34s（含Gradle/server启动）。
每seed全部五类期望行数匹配、全部raw seeds一致、finite密度、810个gradient replacement归因点通过。
`compilePhaseVerificationJava`：PASS，5s；所有新Java仅phaseVerification source set。

`python -m unittest discover -s tools -p test_spatial_baseline.py`：PASS。
`python tools/spatial_baseline.py --plots --archive`：PASS；numpy2.3.5 / matplotlib3.10.7，未用于mod runtime。

Native client完整结果、严格require-native验证及失败路径补测见下方最终记录。

## Retained failed attempts

- baseline初次：Gradle新入口括号绑定错误，task配置失败、未测世界。
- baseline第二次setup：dev mod classloader无法通过工具类读取main mod资源，资源null导致非零失败。
  改用真实server ResourceManager读取获胜datapack资源；第三次完成。无production fallback/hack。
- native第一次：Fancy preset重置simulation distance到12；主动停止，部分图排除。
  调整为先应用Fancy再覆盖sim5/cloudsOFF/particlesMinimal；实际UI显示CUSTOM并记录所有子设置。
- 这些失败的原始日志/partial outputs留在ignored build/run history，不当成成功观测，不提交run目录。

## Scope guard

`protocol.json`中的所有production源码/资源SHA-256保持不变；未触碰TransferPolicy、
PhaseTransferService、PhaseDefinition、noise kernels、goldens、worldgen presets或SavedData。
基准文件hash不能作为SavedData可信preset fingerprint；不改变自然开场锁定语义。

## Final native / strict tooling checks

- `./gradlew spatialBaselineClient`：原生30张均完成，但完整性审查排除seed0/1的两张角度偏移；不是最终matching PASS。
- `./gradlew runPhaseVerificationSpatialSeed0Client runPhaseVerificationSpatialSeed1Client`：PASS，6m48s，固定相机完整重跑20张；seed2原10张保留。
- `./gradlew testSpatialBaselineTools spatialBaselineReport -PspatialPython=<Python-with-plot-deps>`：PASS，23s。
  最终三个seed全部30张PNG、10个world/window组合/seed、相同pose/time/weather/subsettings及文件完整性校验。
- 六个focused tooling tests：解析矩阵/平面/常量/非有限数，缺report、失败状态、错误seed/protocol、
  重复/缺视角、错误相机/天气/模拟距离/资源包、缺截图及CLI缺失报告非零路径。
- 严格报告任务曾真实返回非零：`SPATIAL_BASELINE_FAIL: Unmatched camera/settings/environment`；
  没有放宽validator阈值或将偏移图片算PASS。
- 制品检查：`build/libs/worldphase-0.1.0-dev.jar` 无verification/SpatialBaseline类；生产输入SHA锁保持一致。

自动化只是unchanged baseline观测；不计新玩法/新地貌/天空renderer/Phase2验收。
