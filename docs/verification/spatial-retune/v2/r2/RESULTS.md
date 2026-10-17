# B2 r2：冻结后纯数学验证与边界

状态：**设计/纯数学交付，等待独立review；不是r2原生或worldgen gate PASS**。
Refs #36 / #34；Phase 2 NOT_APPROVED。

## 冻结与实现勘误

参数/公式/seed/预算于 `d15138de7889fb034ab0514eb318db083642ff13` 先提交，再执行测试。
manifest LF SHA256 `82a3e9676d7de3e223299feff88c7a9ea67e8e4e1c25bb1f3aa134deb59ec9bf` 保持不变。
[协议](PROTOCOL.md)、[manifest](EXPERIMENT_MANIFEST.json) 与 [摘要](FROZEN_SHA256.txt) 都没有事后改参。

首轮18测试：17 PASS、1 FAIL。失败为单次reversed迭代器在第一个窗口消耗，第二窗口看不到相同dry IDs。
这是原型输入处理错误，不是几何或参数试验失败。修复仅将输入在两个窗口前物化一次；原失败保留。
[源码勘误](PROTOTYPE_SOURCE_REVISIONS.json) 记录冻结代码hash、修正hash及首轮失败；
manifest 中原始源码锁保留为历史值，审计仅对这个明确指定的纯原型文件接受该外部修订，
不允许借此修改r1、生产文件或测试配置。当前修订仍等待review，不是独立批准。

修正后核心18/18 PASS；随后按已冻结公式补充5项二维组合几何测试，最终23/23 PASS。没有升高1/8，没有修改seed/节点分布/评分/退出条件，没有看地形后调参。
证据：[SYNTHETIC_EVIDENCE.json](SYNTHETIC_EVIDENCE.json)。二维组合代码实现冻结后的公式，不修改参数或主原型；来源摘要另列。

## 归因的新增解析证据，未重采样r1

原始r1摘要仍为353/124、365/112、391/86（dry/band优先、随后slope），0/477×3不变。
旧证据没有每节点B、E、极值坐标，不能恢复哪些是实际坡地、哪些只是最坏区间保守，也不能给false-negative百分比。

冻结后进一步仅检查 pinned data/source、运行不含Minecraft依赖的既有 `Interval`/`GradientRoundoff`：
continentalness九个幅度最大2，Perlin正几何归一权重和约1、edge bound2；两层NormalNoise的
valueFactor≤5/3，故有远小于1024的原生输出界（不依赖seed或坐标shift）。取极宽±1024向外传播，
`heightErrorUpper=8.177895168595872e-10`。没有调用continentalness，没有创建RandomState/Level。

于是Hlo≥-7-E、Hhi≤133+E，band下端≥-39-E>-40，上端≤153+E<240。
在这套pinned配置中，原合并拒绝的**band分支可由解析界排除**；353/365/391来自dry区间下界<71。
这是对冻结协议中条件E<1的补充证明，不是修改原始分类字段或用新采样覆盖旧结果。
dry区间失败不等于玩家站位实际在水里：全区域min和N2全域±6仍可造成false negatives。

N2贡献的保守模型L已约.0441667，耗掉1/8预算约35.3%。若Bx=Bz，raw continents每格边差
须约≤.00089309才可能通过其余预算；分量/位置/方向最坏值不必同时发生。
这是为何全域HF界可能苛刻的量级说明，不是实际节点的Bx统计。
真正的宏观问题也仍存在：共享Hc覆盖[-7,133]，海洋不会靠浮点语义修正变为陆地；
真实陡壁、悬崖和无出口区域只能拒绝，不能靠新证书洗白。

## A/B结果与尚不能证明的事

|项|纯数学结果|实际MC状态|
|---|---|---|
|A 有限物理尺度|1/128振幅、1/8周期的解析HF反例：微分坡度>.125，但quarter尺度幅度包络可<.125；大起伏仍失败|实际自然候选yield UNKNOWN|
|B 共享核心|精确半格平面L=0，dry/bin/error未知均拒绝|未接线/未生成|
|B 受控坡道|128格、最多8格高差，L≤15/128；相反且共用两Phase端口是必要条件|实际端口能否满足前提 UNKNOWN|
|B 自然终端|给定range≤.5、gradient≤1/16前提时，接驳L≤13/128；额外8格出口域|原生Hp前提未证明，FULL cave/fluids/实体仍可能拒绝|
|浮点标量误差|ramp≤7.125e-8、terminal≤8.732e-8，均低于冻结2^-20；finite slopes .11719513/.10157013|完整r2 mask/DF/Java25对照未做|
|合成插值|平面线性插值一致；quintic与线性插值不同；后续carver可挖掉正密度支撑|不是NoiseChunk或FULL测试|
|出入口/图|单口、无共同相反对、陡壁环、断连图均有拒绝/反例|不是路线验证；ROUTE_COMPLETED=0|

B能在预定义平缓外部场上构造连续可接驳的核心；对40格环墙、过湿、过大终点高差、梯度超限或误差未知会拒绝。
因此这是**有条件可行的数学机制**，不是保证三个真实seed必然产出节点。
实际外部场若处处违反终端条件，r2必须报告BLOCKED，不能再增高坡度/放宽水域或扩大随机池。

另一个关键约束：谷线图先依赖dry anchor，端口验证随后执行；不让端口成功反过来改变被验证的Smooth地形。
endpoint-only图组件不等于可走通的图；FULL失败也不能反过来改DF/抹掉失败节点或重生成存档。

## 分布与面积

仅对开发seed作hash/格网布局，**没有读取它们的高度或地形**。合成all-dry场的短名单：

|seed|target IDs|合成all-dry shortlist|真实dry/合格节点|
|---|---:|---:|---|
|123456789|132|12|NOT_SAMPLED / UNKNOWN|
|8675309|132|13|NOT_SAMPLED / UNKNOWN|
|20261011|132|13|NOT_SAMPLED / UNKNOWN|

以上12/13/13不能当节点yield成功率。窗口各取前8、合并去重，没有补满16，也没有失败后换点。
中心间距至少768，192支持圆不重叠；理论core约.0767%，collar inclusive约.1726%，
支持圆上界约11.045%，不能混作安全区面积。16个完整支持圆相对两ROI并集的上界约12.23%<15%；
实际需另报窗口内裁切面积与FULL效果传播，不能拿这张表宣称生成图像仍有辨识度。

## 预计工作量与未测时间

- 132 anchors，≤528 raw calls。
- 16节点×4端口×805共用raw顶点+anchors，**≤52048 raw calls/arm/seed**，相比r1实际2077812约39.9倍少。
- A的足迹版本≤8992 calls；B两Phase列work≤95744、Smooth边测试≤4691456。后者可能成为新瓶颈。
- 硬cap65536 raw、每tick2048，同时切片非raw工作；soft8ms/hard25ms；≤256ticks、CPU≤1.5s、wall≤15s。
- raw饱和的26ticks/1.3s只是计数下界，不是运行预测。相比r14059tick，主要结构改善为少调用、共享缓存与缩短调度等待。
- CPU/tick/wall、真实owned cache内存、线程分配、FULL请求及连带生成都**NOT_MEASURED**。
  不把Python测试耗时当作Java/server性能，也不承诺39.9倍提速。超预算必须整体BLOCKED。

## 命令与证据边界

```sh
python -m unittest discover -s tools -p 'test_shared_nodes_r2*.py'    # 23 PASS; original 17/18 failure retained
python tools/shared_nodes_r2.py --write-report                       # PURE_MODEL_AND_ERROR_ONLY_NOT_NATIVE_OR_FULL
python tools/shared_nodes_r2.py --require-native                     # expected exit 1
python -m unittest discover -s tools -p test_spatial_v2_manifest.py    # 15 PASS, r1 unchanged
./gradlew build                                                      # PASS, 15 tasks UP-TO-DATE
```

另对r1 `check_v2_preflight.py --require-pass` 核对继续非零。不重新跑server、生成地形或截屏。
本轮build复用现有编译/测试输出，不能称为新一轮真实服务器验收。23项r2测试是实际执行的纯数学/合成几何测试。
纯JDK band界计算仅读取两份既有数学源码，无MC classpath；source参数读取有摘要归档。
运行日志留在ignored build/tmp，未提交生成目录或世界。

## 结论与下一门禁

本PR提交冻结设计与可复现纯原型，主提案B、A作同分布对照。**不是节点可用性PASS**。
未知：真实dry/port yield、原生Hp终端证书、完整DF浮点/插值、FULL安全/连带生成成本、路线收益与视觉影响。
尤其L1024可能令切换点过稀，Hp终端±.5/1/16可能仍令节点全拒绝；它们都是预注册的可失败条件，
不能借本PR的数学PASS直接批准V2世界。尤其原生Hp不能只套全局GradientNoise最坏导数就宣称端口满足1/16：
需要新的局部包络/导数证书；此原生证书实现、成本和通过率都仍是BLOCKED前提，不在纯输入oracle中伪造。先独立review，再做r2原生预检，另行批准新存档生成。
不改生产worldgen/维度/TransferPolicy，不做Cell/Fracture或天空渲染，不解封holdout。
