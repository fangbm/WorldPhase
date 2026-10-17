# Milestone B1：r1 连续包络与浮点证明边界

本文件补充被冻结的 [r1 manifest](EXPERIMENT_MANIFEST.json) 和 [协议](PROTOCOL.md)，不改写其历史状态或数值。
配置冻结已由 PR #37 批准；这不等于数值证明、V2 世界生成或 Phase 2 获批。
实现位于 `src/phaseVerification/.../verification/v2`，不会进入生产 JAR。

## 1. 被证明的对象与实际接线

记 `v[i,j]` 为 **当前 raw world seed 接线后** `minecraft:overworld/continents` 在整数 `(i,0,j)`
的有限 binary64 输出。`RandomState` 原生 visitor 接线 NoiseHolder；`R1.rawReference` 展开 holder 和
`MarkerOrMarked` 为 scalar delegate，移除 foreign NoiseChunk 的缓存/插值状态。
整数输出本身定义参考数据；不把 Minecraft 原始连续 continentalness 的导数当作已知。

`V*` 是这些已固定整数值的实数双线性插值，`C*=clamp(V*,-1,1)`。
`N*` 是既有 GradientNoise 的实数多项式延拓，梯度常数仍使用其**已存储**的
`σ=0x1.6a09e667f3bccp-1`，hash 和 role seed 不变。
`H*=(63+64 C*)+6 N*(x/900,z/900)` 是连续模型；`Ĥ` 是冻结括号次序的实际 binary64 程序。
两者有明确区别。本实现分别输出：

- 连续区域上 `H*` 及 `Ĥ` 的保守高度区间；
- 连续模型的全域 Lipschitz 上界 `L*`；
- 实际程序相对模型的均匀高度误差上界 `E`；
- `|Ĥ(p)-Ĥ(q)| <= L* ||p-q|| + 2E`，所有右端运算向外舍入。

最后一个是不等式证书，**不是**把误差忽略后的齐次 Lipschitz 证书。
r1 未批准最小比较距离或允许的附加误差，故不能据此声明 actual rounded Hc 的 slope gate PASS。
即使模型 dry/slope 均合格，节点也返回 `R1_ROUNDED_LIPSCHITZ_UNPROVEN`，不启用共享节点。

## 2. 连续区域覆盖，不是离散采样冒充证明

对每个已存储中心 `c`，用 `BigDecimal(double)` 精确构造 `c±32`，向下/向上取整取得所有整数顶点。
每轴最多 66 个顶点，最多 4356 次 raw reference 调用，覆盖影响圆的外接方形及其全部闭合网格单元。
负坐标使用 floor，恰在网格边缘也取共同顶点；不会按 chunk 截断区域。

在每个单位单元，双线性插值是四顶点凸组合，值域位于其最小/最大值之间。
`∂x V*` 是两条 x 边差的凸组合，`∂z V*` 同理。令全部单元边差绝对值的向外上界为 `Bx,Bz`。
clamp 是 1-Lipschitz，跨单元连续；因此这些分片导数界也约束跨边界的线段积分。

GradientNoise 使用单位梯度与 quintic fade。`0<=fade<=1`、`fade'<=15/8`。
任一方向单元内两角 dot 的差绝对值不超过 3，dot 的该方向导数绝对值不超过 1；
故每分量导数上界取 `G=(53/8)σ` 的向外上界。其跨 lattice 接缝的一阶导数一致。
模型 Hc 的梯度分量界是 `64 Bx+6G/900` 与 `64 Bz+6G/900`，欧氏范数向外取上界得到 `L*`。

`N*` 的保守单位值域亦可直接证明：fade 在 `[0,1/2]` 不大于 t，在另一半由对称性得到反向关系。
每轴插值的平均 L1 位移 `t(1-w)+(1-t)w<=1/2`；两轴之和不超过 1。
每个单位梯度 dot 不超过相应 L1 位移，所以 `|N*|<=σ<1`；加入本实现的舍入误差仍远小于 1。
这为高度包络中的 `±6` 留出余量，而不是假设某些离散噪声样本没有超过 1。

## 3. binary64 向外处理

`Interval` 的有限端点加/减/乘/除，每个下端取 nextDown、上端取 nextUp；除数跨零、溢出、NaN、
非有限 raw reference、非法区域立即失败。正数范数/误差运算也逐步向外扩张。

`GradientRoundoff` 的每个表达式保存 `(实数区间, 绝对误差)`：加减传播误差之和；乘法传播
`|a| Eb + |b| Ea + Ea Eb`。每个实际运算再加入整个 ulp，覆盖 binade、tie 和 subnormal 边界。
精确 fade/凸组合的范围收紧只收紧**实数值域**，不会抹掉此前误差。
fx/fz 的减法按 ulp(1) 包络；频率为 1 的乘法精确。原始坐标 `/900` 的误差用支持范围
`|x|,|z|<=29,999,984` 上的最大商 ulp 包络，再由上述全局模型导数界传播；跨 lattice 也适用。
双线性表达式逐项按 r1 括号传播，clamp 非扩张，最后加入 63、64、6 的运算误差。

高度包络为 `[63,63]+64 clamp([min v,max v],-1,1)+[-6,6]`，再向外扩张 E。
dry/band/slope 比较向保守方向取值；不确定不会启用节点。
源码及原生 Overworld settings 的摘要与冻结输入核对；证明不是对另一个重写 noise sampler 的测试。

`GradientDecimalOracleTest` 用少量手工坐标、负边界及 BigDecimal 精确多项式核对误差界。
这些测试用于发现实现错误；连续证明依据是上述覆盖/运算规则，不能由测试样本替代。
`HcCertificateTest` 另给出实际 rounded Hc 相邻 double 的跳跃反例，证明不能直接把 L* 当成程序的齐次斜率。

## 4. 就绪、缓存与拒绝契约

每个 RandomState 构造作用域有独立 V2Context，持有 seed、raw scalar reference 和固定两 ROI 的节点集合，
不持有 Level/玩家/世界缓存。477 个节点是两 ROI 及邻域的去重并集，少于 r1 的 800 上限。
准备必须在创建线程逐步进行；最多 512 calls/step，逐调用前后检查 25ms，总上限 3,484,800 calls。
单个原生调用不可抢占；超时只能停止后续调用、标记 FAILED，不能撤销已消耗的时间。

NEW/PREPARING/FAILED/CLOSED、ROI 外、缺证书、预算超限均不得求值。READY 仅表示准备遍历完成，
**不表示存在合格节点或数值门禁通过**。节点证书上限 1024；列 LRU 上限 16384。
context 关闭清空缓存；seed/reference/profile 改变须新建 context，不能复用旧证据。
列结果只缓存确定性 x/z/Phase 高度，不缓存 y/noise/实体状态；遍历顺序只影响性能，不影响值。

## 5. 需独立 review 的 r2 建议，未实施

1. 明确 slope gate 针对连续模型 H*，并另行批准程序误差 E 的安全余量；或定义有物理单位的最小距离及
   `L*+2E/dmin` 有限尺度证书。不能在 r1 内偷偷加 epsilon。
2. 实测三个开发 seed 的 477 节点中，保守 dry/model-slope 同时合格均为零。
   这表示**当前证明/配置无法批准节点**，不等于证明真实地形处处危险。
   应审查 raw continentalness 高频分量、区间过宽和坡度目标是否匹配，不直接调低安全要求。
3. r2 批准后仍须重新验证真实 NoiseChunk 插值、FULL chunk caves/carvers/aquifer/结构、碰撞与 LandingSafety。
   相同 raw density、同一证书、同一 shared node 都不等于安全落点，更不等于 ROUTE_COMPLETED。

本轮停止在预检。没有 V2 世界、地貌截图、holdout 采样、参数调优或 Phase 2 准入。
