# WPH-016-02 B3：r2 Native Preflight — BLOCKED

Refs #36 / #34。**不具备进入 V2 地形生成实验的条件**；Draft，等待独立 review。
本报告为负面预检交付，不是完整数值认证、节点安全或地貌验收成功。

## 来源、环境与冻结

- 基线 merge：`5a1edecdce908debe1e12dde3c818e579610772f`。
- 三个冷 worker 与专用服务器测量源码：`c6a256fa5f6b45183aa839dfb8e6b2f6d70b21ce`。
  实测于 **2026-10-17**；中断后的收尾不将旧测量改写成新日期的原生运行。
- r2 manifest LF SHA256：`82a3e9676d7de3e223299feff88c7a9ea67e8e4e1c25bb1f3aa134deb59ec9bf`。
  `execution_authorized=false`、参数、seed、窗口、排名及端口规则均未更改。
- Minecraft 26.1 / NeoForge 26.1.0.19-beta / Java 25.0.3+9-LTS。
  Windows 11 Pro Insider Preview 10.0.28120，Ryzen 9 8945HX（16核/32线程），可见RAM约31.2GiB。
- 三组开发 seed：123456789 / 8675309 / 20261011；forest=(-384,896)，highland=(1344,1200)。
  三个 holdout 未计算布局或地形。未重跑/覆盖 r1 的原始预检证据。
- 主提案 B `CONTROLLED_PATCH_B_R2`；A仍未运行，不声称已经完成 A/B 地貌比较。

[证据索引](B3_EVIDENCE.json) 保存每份原始报告、运行源码、旧证据和冻结输入的LF SHA256。
六份报告按原始字节归档在 [b3/](b3/)；132个节点逐个记录坐标、状态、Anchor区间、
端点区间、程序误差字段、端口拒绝原因、检查过的终端单元数以及图组件。
`UNEXAMINED` 或零个终端单元时，默认的0字段不是高度/梯度已经证明为0。

## 状态分层

|门禁/状态|结果|
|---|---|
|真实服务器 PREPARATION_COMPLETED|3/3完成，全部132 Anchor检查完毕|
|独立冷 worker 准备|3/3在初始化25ms硬限处失败；未开始Anchor采样|
|MATH_NODE_READY|三个seed均0；六个seed/window组合全部FAIL|
|Codec/seed/mapAll/native算子与冻结几何一致性测试|通过，范围见下文|
|NATIVE_NUMERIC_VALIDATED|**未通过**；完整DF误差证书尚未完成，没有真实合格节点可作正向验证|
|FULL_PAIR_VALIDATED / SHIFT_POSITION_FEASIBLE|NOT_RUN|
|B3 ROUTE_COMPLETED / 地形视觉|0 / NOT_RUN|
|V2世界、holdout、Phase 2|未运行 / 密封 / NOT_APPROVED|

服务器准备完成仅表示所有预注册检查已结束，允许以0个合格节点完成，不能替代产出率门禁。
冷worker的0个已发布节点表示**初始化超预算而未完成搜索**，不当作另一组真实零产出率实验。

## 原生实现与证明范围

全部新增引擎在 `src/phaseVerification/.../v2/r2`；没有生产资源或维度映射。

1. 使用实际seed接线后的 `minecraft:overworld/continents`，通过既有 `R1.rawReference`
   去除holder/marker，按冻结顺序做整数原始值的双线性扩展，再调用未修改的GradientNoise。
   原生整数输出的binary64位模式作为参考模型常量，不用离散高度差冒充连续场导数。
2. `Interval` 向外舍入；`Jet` 同时传播实数区间、x/z导数区间和程序误差。
   原生双线性每个完整整数单元的范围、边差导数，以及GradientNoise每个相交lattice的
   八梯度多项式，分别进行包络。负坐标、格边、chunk边界和缓存查询顺序有测试。
   Anchor只有在含程序误差的区间拥有唯一floor bin时才半格量化；dry/band/border分别检查。
3. Smooth仅由ANCHOR_DRY构造谷线图。投影clamp、最短线段和Gaussian使用区间/导数包络；
   切换最近边时取导数hull。Ridge使用冻结的方向性sin/exp表达。
   对终端闭矩形覆盖整数单元；必须全部满足range、gradient和error条件才可接受。
   当前所有需要进入终端域的端口在第一个单元就未能认证，记录后立即结束该端口，未做稀疏采样判定。
4. `|delta|<=8`、range<=0.5、gradient<=1/16、Hp error<=2^-24及既有2^-20物理误差cap保持不变。
   实数坡道/终端的15/128、13/128与d>=0.25的 `L*d+2E` 仍沿用B2证明。
   E/W优先、N/S回退，仅选定对参与几何；不因FULL失败改选。

这些是局部候选包络及实现，不是整个r2管线已经获得可信数值证书。
实测端点的实现误差字段最大约2.464e-11；这不代替独立审查：种子方向框架中绑定常量的
实数解释、StrictMath误差前提，以及完整径向/端口mask、N3 detail和原生插值的统一误差界
仍需审核。尤其不能仅凭保护权重的理想区间[0,1]推导整个binary64程序的全域metadata界。
没有把未完成的证明标记为NATIVE_NUMERIC_VALIDATED，也没有启用真实节点或生成世界。

### 实际26.1接线

- `DensityFunction`、`MapCodec`、`KeyDispatchDataCodec`；dev-only type
  `worldphase:prototype_v2/analytic_r2` 注册到 `Registries.DENSITY_FUNCTION_TYPE`。
- strict schema：profile固定 `worldphase:prototype_v2/shared_r2`；field为
  `smooth_core|ridge_core|smooth_controlled|ridge_controlled|smooth_protection|ridge_protection`；
  reference必须为命名的 `minecraft:overworld/continents`。
  非protection字段要求对应的 `worldphase:prototype_v2/{smooth|ridge}/detail_r1` holder，参数0/[1]；
  r2未改变r1 core N3定义，所以不另造一套噪声配置。未知key/profile/field等拒绝，不编码seed/context。
- 隔离的RandomState构造visitor注入raw long seed；子DF和NoiseHolder均mapAll。
  两种与生产seed visitor的嵌套顺序得到相同接线；实际holder maxValue约10/3，按其真实值归一化。
  未绑定/未准备的context明确抛错，不偷偷使用seed=0。
- controlled分支按冻结的b/q/T/W/P公式计算；detail直接传递，不由已加上大基值的density反推。
  在Vanilla final AST三处sloped_cheese引用替换Dctrl；Fsolid=`post(slide(Dctrl))`；
  **最后的noodle之后**才混合保护分支，外面不增加interpolated包装。
  preliminary surface取同一无洞穴受控分支。
- 核心/controlled叶子的宽界为±134217750，涵盖所有int blockY与冻结高度/细节范围；
  protection当前原型声明理想[0,1]，计算时越界拒绝。该保护metadata的全域浮点证明仍是阻塞项，
  不能把已采样点通过误读为全域min/max证书。生产环境不使用此原型。

原生算子测试使用真实MC registry、NoiseHolder、RandomState、NoiseChunk插值和base-column代码；
在明确的测试接缝中向router注入**合成、手工激活的已准备上下文**，没有创建Level或FULL chunk。
这些测试验证Codec/组合层次/插值，不证明真实seed具备合格节点。
[b3/synthetic-native-probes.json](b3/synthetic-native-probes.json) 记录6组raw、interpolated、delta、
preliminary与生成列顶面；它们不是实际r2地貌样本。
另外66组 [合成几何探针](b3/synthetic-geometry-probes.json) 与**未修改的**B2 Python模型交叉核对
选定对、Hctrl、density、P及qsum。最后数bit的hypot差异仅采用8ulp一致性容差，
这不是连续误差证明，也没有替代2^-20物理cap。

StrictMath的确定性说明参考 [JDK25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StrictMath.html)；
另核对了所用JDK `lib/src.zip` 内的FdLibm源码。新AD验证程序为本仓库实现，未复制JDK函数实现；
生产GradientNoise/hash及其既有来源声明保持不变。

## 真实服务器产出率

|seed|Anchor|Dry|shortlist并集|forest dry/shortlist|highland dry/shortlist|两窗口MATH节点|
|---|---:|---:|---:|---:|---:|---|
|123456789|132|58|8|4/4|6/6|0 / 0 FAIL|
|8675309|132|54|6|4/4|2/2|0 / 0 FAIL|
|20261011|132|46|1|0/0|1/1|0 / 0 FAIL|

窗口有重叠，所以每窗口shortlist不能相加当并集。未入选的干Anchor分别50/48/45个，
保留 `DEFERRED_NOT_SHORTLISTED`；wet Anchor分别74/78/86个。
本次无Anchor bin/error/band拒绝，没有用任意新点填补短名单。

|seed / Phase|端口数|delta拒绝|range未认证|gradient未认证|通过|
|---|---:|---:|---:|---:|---:|
|123456789 Smooth|32|19|12|1|0|
|123456789 Ridge|32|22|9|1|0|
|8675309 Smooth|24|11|12|1|0|
|8675309 Ridge|24|15|8|1|0|
|20261011 Smooth|4|2|2|0|0|
|20261011 Ridge|4|3|1|0|0|

不存在共同相反端口对。端口分类有优先级：先delta，再range，再gradient；
range上界超过阈值只说明当前包络不能认证，不证明真实场一定超过该值。
原始报告保留了端点区间、单元数量与上界，不能把首个失败单元当作整个终端已验证。

失败实例：

- seed123456789，ID(-1,-1)，h=82.5；Smooth E端点92.5，delta=+10，拒绝。
- 同节点Smooth N，首单元range上界3.42708、gradient上界0.20193，range优先拒绝。
- 同节点Ridge S，range上界0.31714但gradient上界0.08994>1/16，拒绝。
- seed8675309，ID(-2,0)，Smooth W delta=-14；没有通过延长坡道或改向挽救。
- seed20261011在固定窗口已经只有0/1个可入选dry Anchor；即使终端证明更紧，
  也无法从当前短名单得到每窗口2点。不能把问题仅归咎于浮点保守性。

Anchor valley图分别25/18/17个组件，逐ID/边列表在各报告中。
已发布数学节点/端点图为空；没有把Anchor连边视为玩家已经走过的路线。

## 计算预算：真实tick与冷worker分开

|server seed|raw calls / hits|Hp / Hc区间work|Smooth边测试 / Jet构造|CPU ms|scheduled wall ms|tick数|最长tick ms|分配MiB|测量owned retained MiB|
|---|---|---|---|---:|---:|---:|---:|---:|---:|
|123456789|791 / 361|87 / 219|4410 / 76853|62.5|279.6065|6|8.7336|19.062|2.179|
|8675309|768 / 304|70 / 202|3626 / 66604|15.625|108.4374|3|8.4344|13.253|2.176|
|20261011|571 / 37|11 / 143|588 / 25929|reported 0*|56.2269|2|6.1076|6.933|2.168|

每份报告包含完整tick序列、cache misses/entries、初始化成本和Instrumentation测量。
CPU观测为15.625ms粒度的倍数；reported 0不能解释为没有工作或精确的CPU上界。
CPU是初始化和prepare调用之和，不含无关legacy tick；wall包括调度间隙。
初始化单独占一个tick，包含在256 cap内。单个不可抢占调用返回后若超限，立即停止整个profile。

|cold seed|初始化wall ms|观测CPU ms|已采样Anchor|结果|
|---|---:|---:|---:|---|
|123456789|39.8645|46.875|0|INITIALIZATION_HARD_TICK_25MS|
|8675309|56.4517|46.875|0|INITIALIZATION_HARD_TICK_25MS|
|20261011|42.4872|31.25|0|INITIALIZATION_HARD_TICK_25MS|

每个seed一个独立新worker，失败后不继续采样。CPU与wall不混用，也不因计时粒度而改写原始值。
三个cold报告的raw/height/edge/Jet计数都是0，分配约6.205MiB，owned retained约2.150MiB。
Registry/bootstrap单独记录；server共享lookup建立未作为每profile成本重复计费。
因此暖态表也不是从全新JVM启动到可用的全成本证明。

owned retained遍历排除输入reference、Thread、clock，包含保守多算的共享不可变metadata；
它不是整个NoiseRouter/registry或JVM的transitive retained总量。失败后的额外图测量有独立耗时字段，
不恢复证书工作。post-report序列化、文件I/O、server save不是可用于宣称25ms准备门禁的CPU数据。

服务器负例准备未超所测profile限额，但**不能**宣称高产出情况下也满足1.5s/15s：
所有端口都提前拒绝，实际只检查23/22/3个终端单元，远小于冻结的最坏95744列work、4691456边测试。
冷启动硬限已经失败；内存及完整数值前提仍需独立review。此处不通过搬走成本或增加预算续跑。

## 回归与失败路径

2026-10-17实际执行并保留证据：

- focused Java：17项（11数学、5原生算子、1合成几何导出）通过。
- 完整JUnit：397项，395通过、2跳过、0失败；另外profiler/core-report 15项通过。
  跳过的是需要显式启用的r1准备计时与本轮cold worker入口；cold三次已独立运行。
- `./gradlew coreRegressionAll build` 最后一次 **PASS，2m31s**，23任务中5执行、18 UP-TO-DATE。
  真实冷/重载服务器100完整循环、400循环Shift、456总成功，最大xz/yaw/pitch偏差0；
  重复玩家/掉落测试物品0，save/reload PASS。
  [原始core摘要](b3/regression/core-summary.json)、[JUnit摘要](b3/regression/junit-summary.json)。
- B2纯数学/几何28项、r1 manifest 15项通过。B3 evidence失败路径8项和Python几何一致性1项通过。
- `check_v2_preflight.py --require-pass` 仍预期exit1；r1冻结报告与原始0/477×3未改。
- `check_r2_native_preflight.py` 验证完整负面证据exit0；`--require-pass` 预期exit1。
  缺报告、source/hash不匹配、部分Ready发布、状态/window不一致或伪报预算内完成均不能通过。

准备代码的新失败历史包括：早期Java参数/方法引用编译错误；首个计量agent与mod类加载冲突；
只对ALL-UNNAMED开放仍无法反射NeoForge命名模块的ArrayList。均在测量源码commit之前修正，
没有靠修改游戏参数/JDK版本绕过。agent现独立编译，只向计量模块开放需要的JDK容器。
测量commit之后没有修改runtime实现或重试失败的cold profile；后续只补充合成一致性测试和证据工具。

## 建议：BLOCKED，停止在预检边界

1. 保留r2这次失败：真实shortlist全部没有共同双端口，20261011还有明确的dry节点数量不足。
   没有依据批准V2地形生成，更不能解封holdout来寻找好看的seed。
2. 独立审查局部区间/导数、原生库误差前提、protection metadata和完整DF数值证书；
   合成匹配或raw/interpolated探针不等于这些证明已经完成。
3. 若继续设计，应先提出独立r3/修订manifest：明确连续过渡如何连接外部自然场，
   避免依赖两Phase在整个32x20终端域几乎同高；重新论证dry空间可用性。
   不直接提高坡度/高度阈值、不增加随机池、不换seed或窗口。
4. 对冷初始化需预注册可切片构造，或明确MC生命周期已有RandomState与模组新增准备的计费边界，
   两者均须测量。不能在r2看到结果后把初始化挪出预算。

本PR只交付B3预检原型和可审核负面证据；FULL、真实Shift、路线和视觉门禁仍未运行。
不修改r2的授权字段，不关闭#36/#34，不启动生产worldgen、Cell/Fracture、天空或Phase 2。
