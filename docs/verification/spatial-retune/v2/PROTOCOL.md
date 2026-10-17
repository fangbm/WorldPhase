# WPH-016-02 / Milestone A — r1 实验冻结提案

状态：**FROZEN_PROPOSAL_AWAITING_REVIEW**；关联 #36 / #34，不关闭Issue。
基于 `8fae9d4030b2dd0c9bac95107f81fa2f483143e4`，采用
[已批准C方案](../../../SPATIAL_RETUNE_PLAN.md) §4.0–4.2、4.5、5、8、9。
数值、tags、IDs、源摘要和预算以 [EXPERIMENT_MANIFEST.json](EXPERIMENT_MANIFEST.json) 为准。
本次只有配置、数学证明与合成测试；V2世界、截图、性能、实际节点分布及路线全部NOT_RUN。
A review通过后才进入B实现，B实现门禁通过后才允许生成数据。

## 1. 身份、对照与隔离

MC26.1 / NeoForge26.1.0.19-beta / Java25.0.3+9；海平面63，高度[-64,320)，
noise cell4×8×4。开发seed固定123456789、8675309、20261011；窗口固定
forest(-384,896)、highland(1344,1200)，不替换水域、普通或失败样本。
holdout606102026、-77331057、424242424242保持密封，本轮仅保存ID；
开发实验后另行review解封，不用于调参。

| arm | 输入 | 版本化settings |
|---|---|---|
| LEGACY_V1 | 合并基线原样 | worldphase:smooth/ridge |
| C1_R1 | 仅Smooth amplitude .10→.11、Ridge 1→1.10 | worldphase:prototype_c1/r1/smooth、ridge |
| V2_R1 | 本文新密度架构 | worldphase:prototype_v2/r1/smooth、ridge |

沿用母计划§8：**Level key仍是worldphase:smooth/ridge**，仅新实验存档的隔离datapack
把dimension的settings引用指向版本化资源。否则新prototype Level key会被现有服务正确拒绝为
INVALID_SOURCE，无法验收正式Shift。PhaseCatalog/Definition/Resolver/TransferService不改。
生产dimension文件、旧settings和原始V1证据不改；本轮连测试override pack也不生成。
C1克隆S/R递归资源并重定向引用，规范化结构diff只允许引用重命名及两处amplitude；
frequency/octaves、offset/factor/jaggedness、caves、surface均保持旧版。
母计划Cell/Fracture的C1倍率留给后续四Phase实验，本次不实例化。

每arm/seed一个独立新目录：`run/spatial-retune-v2/r1/<arm>/seed-<seed>/world`，
已有目录则CREATE_NEW_OR_FAIL；save/reload只允许完全匹配同一manifest的该组世界。
manifest按CRLF→LF规范化后的UTF8字节SHA256在外部报告记录，不自引用写回文件；同时记录冻结commit、
实现head、最终pack优先级和资源摘要。任何参数、求值顺序、hash、归一化变更都须r2、新key、
新世界和review，不能观察结果后覆盖r1。全量Minecraft data条目摘要锁定递归依赖，
源码文本CRLF统一LF后hash；ZIP元数据不参与。这不是SavedData的可信preset fingerprint。

## 2. 求值、坐标与完整密度表达

p=(x,z)，长度/高度为blocks，D无量纲，**正实体、负空、零仅界面**。
Java25 binary64，exp/sin/cos/sqrt用StrictMath，不用FMA，按下列括号次序求值。
`S(a,b,v)=t*t*(3-2*t)`，`t=clamp((v-a)/(b-a),0,1)`。
M/h/u为manifest展开的64位wrapping hash；负ID先按two-complement编码，unsigned >>>。
所有N2用既有`GradientNoise(1.0).sample(M(rawSeed xor roleTag),scaledX,scaledZ)`，
先缩放坐标，不修改kernel/goldens。N3用各自命名NormalNoise holder，参数0/[1.0]，
除以**实际已接线NoiseHolder.maxValue**至[-1,1]；非有限/未接线/非正界必须失败。
NormalNoise不是天然单位范围，本配置原生界约10/3。无seed+phaseIndex或可变Random。

### 2.1 Hc、Dcommon、m、P

Vij取raw-seed接线后的`minecraft:overworld/continents`在整数(i,0,j)的raw DF。
先展开holder/marker为scalar delegate，不能携带foreign NoiseChunk缓存或插值状态。
非整数p用bx=floor(x)、bz=floor(z)、tx=x-bx、tz=z-bz，四角双线性扩展：

```text
V = (((1-tx)*(1-tz)*V00 + tx*(1-tz)*V10) + (1-tx)*tz*V01) + tx*tz*V11
C = clamp(V,-1,1)
Hc = (63+64*C) + 6*N2_common(x/900,z/900)
Dcommon = (Hc-y)/16
Dterrain = (1-m)*Dcommon + m*DpCore
Py = (1-S(16,32,Hc-y))*(1-S(4,20,y-Hc))
P = (1-m)*Py
```

Hc∈[-7,133]，Dcommon不含独立3D细节；该参考是明确的C0扩展，不冒充原生插值/FULL。
节点核心半径16，外加collar8，最后8格淡出：m=S(24,32,d)，d≥32取m=1。
Py在[Hc−16,Hc+4]为1，在y≤Hc−32或y≥Hc+20为0，其间平滑。

```text
slide(D): topFactor=clamp((256-y)/16,0,1)
          top=(1-topFactor)*(-0.078125)+topFactor*D
          bottomFactor=clamp((y+64)/24,0,1)
          result=(1-bottomFactor)*0.1171875+bottomFactor*top
postProcess(D) = squeeze(0.64*interpolated(blend_density(D)))
Fcommon = postProcess(slide(Dcommon))
Fnative(D) = min(postProcess(slide(caveBranch(D))), minecraft:overworld/caves/noodle)
Dfinal = (1-P)*Fnative(Dterrain) + P*Fcommon
```

Fnative不是假设存在的公共API。取pinned Vanilla Overworld final_density AST，
替换**全部三处**精确sloped_cheese引用，其余AST不变；JSON pointers和源AST摘要见manifest。
包括range_choice输入、entrances的min及underground的`clamp(1.5-.64*D,0,.5)`。
因此完整caveBranch仍含Vanilla cheese/layer/spaghetti/pillars，阈值−1e6/1.5625不变。
Fcommon无caves/noodle；P选择在最后noodle之后，两臂为相同postProcess输出域。
不给Dfinal整体再套interpolated，否则会把核心外Phase细节重新泄漏进公共区。

核心/collar中央保护带数学上共享Fcommon。原生插值、carver、aquifer、表面、结构、
植被/实体和玩家改造仍可能破坏站立条件，必须在FULL动态验证。
8格混合带可能把几十格高差变成陡壁/围坑；有共享核心不能推导可进入、可离开或路线成立。

### 2.2 Smooth

V为§4的有限谷线图，g为其高斯权重；无相关边时g=0。

```text
g = exp(-(dV/64)*(dV/64))
Hv = (Hc+4*N2_wide(x/900,z/900))-8*g
Hs = Hv+(3*N2_hills(x/220,z/220))*(1-g)
Ds = (Hs-y)/16+0.04*N3_smooth(x/80,y/48,z/80)
```

保守Hs∈[-22,140]；两端eligible也不能证明谷线中间干燥、无洞穴或能步行。

### 2.3 Ridge

```text
theta=PI*u(rawSeed,0,0,directionTag)
n=(cos(theta),sin(theta)); t=(-sin(theta),cos(theta))
u=(x*n.x+z*n.z)/320+0.16*N2_curve(x/600,z/600)
a=sin(PI*u)/0.28; r=exp(-a*a)
Hr=((Hc+48*r)-10*(1-r))+4*N2_along((x*t.x+z*t.z)/600,(x*n.x+z*n.z)/900)
wall=S(.10,.30,r)*(1-S(.70,.90,r))
vertical=1-S(16,32,abs(y-Hr))
Dr=(Hr-y)/16+((0.12*wall)*vertical)*N3_ridge(x/64,y/40,z/64)
```

Hr∈[-21,185]；wall∈[0,1]且C1连续，r≤.10或≥.90为0，.30≤r≤.70为1。
所有Phase项先进入DpCore，再统一m/P；方向为全局seed方向，不按chunk重抽。
对曲线坐标变换(320u,dot(p,t))，N2梯度范数界53/8给出Jacobian扰动范数
≤320*.16*(53/8)/600≈.565334<1，因此法向坐标严格单调，不因该扰动折回。
这不证明最终山脊不会被洞穴/节点切断；Fracture的warp及其.35门槛不属于本臂。

### 2.4 bounds与preliminary surface

| 量 | 世界高度及顶端插值节点的保守界 | 注册叶全intY界 |
|---|---|---|
| Dcommon | [-20.4375,12.3125] | [-134217731,134217741] |
| Ds | [-21.415,12.79] | 同上 |
| Dr | [-21.4325,15.6825] | 同上 |
| m、P | [0,1] | [0,1] |
| Dfinal | [-.5,.5] | 原生组合节点推导并交叉检查 |

高度型叶宽注册界覆盖任意int blockY：H∈[-22,185]及≤.12细节，不把仅适用世界高度
的bound冒充通用min/max。只支持合法水平世界坐标和准备好的ROI，越域硬失败。
squeeze对有限值界为±11/24；noodle_thickness=-.075-.025*N，单octave界约10/3，
故该项≥−.159，加非负ridge项后仍高于−11/24，final的±.5是保守界。
这些是解析界，B须核对实际wired holders/子节点的声明及浮点误差包络。

preliminary_surface_level固定`minecraft:find_top_surface`：
`density=(1-P)*postProcess(slide(Dterrain))+P*Fcommon`，无洞穴/noodle；
lower_bound=-64，cell_height=8。上界用全空间保守式
`ceil8(max(Hc_max,Hp_max)+16*A3+8)`，Smooth152、Ridge200。
8格余量和全局高度界覆盖所有插值角点；二者低于top slide240，往上两臂均负，
top的负目标不会另造高层实体。核心仍用Fcommon，只沿用相应Phase的保守扫描上界。
26.1 FindTopSurface从floor(upper/8)*8向下每8格用SinglePointContext检查density>0；
NoiseInterpolator对foreign context委托raw filler。这是参考扫描，必须与base column、
原生插值和FULL高度分别报告，不能用其当aquifer或玩家安全地表。

## 3. Hc证书、dry graph与计算预算

节点ID=(i,j)，中心`512*(i+.5,j+.5)+64*(2*u_x-1,2*u_z-1)`。
eligible是两Phase共用的每ID常量：在闭圆盘半径32上证明Hlo≥71、Hlo−32≥−40、
Hhi+20≤240及共享面slope≤1/8；unknown记录原因并禁用，不随query点/Phase临时切换。

保守包络算法：枚举中心±32包围方形的所有整数角点，floor(min)..ceil(max)每轴≤66，
至多4356次raw V调用。双线性凸组合与clamp单调保证Cmin/Cmax不超出角点clamp后极值；
N2全局[-1,1]给出`Hlo=63+64*Cmin−6`、`Hhi=63+64*Cmax+6`。
每覆盖单元以未clamp V求`Bx=max(|V10-V00|,|V11-V01|)`、
`Bz=max(|V01-V00|,|V11-V10|)`，取所有单元最大。clamp为1-Lipschitz。
GradientNoise归一化前梯度分量≤1、fade'≤15/8、两角dot差≤3，
归一化后分量导数界`G=53/(8*sqrt(2))`；既有INV_SQRT_TWO不大于精确1/sqrt(2)。
因此`Lx=64*Bx+6*G/900`、`Lz=64*Bz+6*G/900`，只在sqrt(Lx²+Lz²)≤1/8时启用。
相邻单元C0，统一导数界给出跨格的Lipschitz界，负坐标同样成立。

这是解析场证明。B仍须实现**含旧kernel求值误差的向外舍入区间证书**；
纯数学fixture不证明浮点程序已完成。无法建立保守包络时V2数据臂明确BLOCKED，
不得把稀疏样本极值称为证明、生成首图后再放宽门槛。

冻结dev preflight准备ROI为两个中心各±1536，仅读取接线后的纯DF，不请求FULL。
每轴floor(p/512)最多7种，加§4的[-6,+7]端点范围，最多20×20节点/窗口；
两窗口并集≤800，最坏3,484,800次raw C调用/seed。每tick最多512次，目标25ms；
记录全部准备CPU/tick/wall，超预算阻断，不删节点/缩邻域。warm和包含准备的cold总成本均报告。
这是解析操作上限，**没有性能实测结果**。
证书容量1024、列信号缓存16384；key含seed、manifest摘要、全局ID或精确x/z。
entry不可变，缓存/并发/驱逐只影响成本；不用Level/玩家或可变Random做缓存输入。
越过准备ROI报CONTEXT_NOT_PREPARED终止probe，不在生成线程偷偷做巨量准备。
本适配器首先用于有界研究区域，不是已批准的开放世界生产generator。

manifest单列每个analytic leaf的冷计算预算：common需要4次raw C/1次N2；
两core各4次raw C/3次N2/1次N3，Smooth另外至多338条边/196个已缓存证书；
m最多9个节点，P另外需要Hc。运行期不求节点包络。
这些是单叶上限，完整原生树重复引用、插值及参考扫描成本须另累计，不借缓存命中率缩小声明。

纯合成全干平面有连续x链，全湿图为空，岛状dry mask可完全断连；z边概率不能修复任意缺口。
实际开发seed的dry比例/连通分量保持NOT_SAMPLED。512间距理想core≈.3068%，
总影响≈1.2272%，dry禁用会减少；core/影响分别统计。保留256间距半径8/16/24的
.31%/1.23%/2.76%修正，不把5–15%当核心目标。

## 4. 节点和谷线的有限邻域证明

令b=floor(p/512)。节点b±1共9个ID足以包含全部32格影响节点：
遗漏中心至少一轴距p≥1.5*512−64=704>32；没有影响节点m=1，不无限找最近eligible节点。
距离为全局欧氏min，等距诊断ID用字典序，跨chunk连续。

边只连轴向相邻eligible节点：x边全保留，z边按manifest hEdge/u<.35；端点ID排序。
点到有限线段距离用`q=clamp(dot(p-a,b-a)/dot(b-a,b-a),0,1)`，
再求|p−(a+q*(b−a))|，不取无限直线。长度至少384，不存在零分母。

dry mask可以有任意长空缺，**全局最近eligible边的精确距离不能用小邻域保证**。
Smooth只使用g：binary64中d≥2048时(d/64)²≥1024，exp(-1024)为0。
e>8/3且(3/8)^1024<2^-1075，低于最小subnormal的一半；Java25 StrictMath验证该下溢。
这保留数值高斯信号，不是在128格任意截断数学式；若改精度、宽度或直接使用dV需新证明。

查询起始ID k∈[bx−6,bx+6]、l∈[bz−6,bz+6]，分别枚举正x/正z边，
最多13×13×2=338条，端点包含在[b−6,b+7]的14×14=196节点内。
遗漏起点至少一轴偏移≤−7或≥7；最近情况是负方向轴向边的正端点，
该轴距离≥(7−1.5)*512−64=2752>2048。线段为两端凸组合，故无法穿过有效邻域。
无候选边或最小d≥2048时g=0，诊断距离为∞/SATURATED，不能冒称精确全球dV。
query格改变时加/减的边贡献均已为0，不产生接缝；固定遍历次序和全局hash保证顺序独立。

## 5. 26.1 codec、seed与router接入计划

核对实际patched源码DensityFunction(s)、NoiseRouter(Data)、RandomState、NoiseChunk、
NormalNoise/PerlinNoise，摘要在manifest。以下尚未注册/实现：

- 唯一新类型worldphase:prototype_v2/analytic_r1，实现DensityFunction，有child，不用SimpleFunction。
  `KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec(...))`；
  `DeferredRegister<MapCodec<? extends DensityFunction>>`到Registries.DENSITY_FUNCTION_TYPE，
  只放verification mod bus，生产注册不变。
- 固定profile及五种field，reference用HOLDER_HELPER_CODEC限定continents，
  detail用NoiseHolder.CODEC，仅对应core字段必需；不建自定义registry/万能DSL。
  无JSON runtimeSeed/自由数值覆盖；未知、多余、缺失、非法参数在codec/严格fixture预检拒绝。
  fillArray走fillAllDirectly；mapAll递归映射真实children，保留不可变seed，再visitor.apply重建叶。
- RandomState构造器在NoiseRouter.mapAll(NoiseWiringHelper)接线noises。
  dev-only WrapOperation组合V2 visitor：delegate Vanilla/legacy visitor后绑定新叶到raw构造器seed，
  visitNoise恰好委托一次，用holder resource key；生产visitor只认识旧叶，不能指望自动生效。
  两种wrapper嵌套顺序、重复绑定和后续NoiseChunk visitor必须集成验证；不改生产Mixin，
  不用全局currentSeed/ThreadLocal。raw reference展开marker后仍保留真正noise child接线。
  未绑定compute抛明确异常，不返回0/固定seed。
- NoiseRouter仅final_density和preliminary_surface_level改变；全部气候、四aquifer槽、
  三ore-vein槽逐字保留，dimension type仍Overworld，biome source/surface/carvers/features/structures原生。
  不重写ChunkGenerator，不在sample中读Level/加载chunk。

## 6. 三层验收与固定观测

沿用[V1协议](../v1/PROTOCOL.md)的33²/step64高度、17²/step16 fine、±512/step8列剖面、
density planes/volume。另加所有三组相同的129²/step8/radius512形态格网，满足母计划≤8格要求，
不能拿64格图当连续脊/谷证明。每个窗口三Level(S/R/Overworld)，每arm18个统一视角。
固定feet y240、yaw135/pitch60、noon6000/clear、1280×720/FOV70/render16/sim5、Fancy基础、
cloudsOFF/gamma.5/particlesMINIMAL/AO开/mipmap4/biomeblend2/Vanilla资源。
同V1最少360ticks、inner10及compiled-frame稳定检查、2400tick超时；遮挡不调整镜头。

1. TERRAIN/VISUAL：每seed Smooth≥2段宽32长256连续缓谷且有出口、保留丘陵水体；
   Ridge≥2脊长256、长宽比4且有谷/鞍部。格网及FULL逐段确认、坐标和测量线归档。
   12张S/R图去标签，按SHA256(experiment_id+image_id)打乱，至少两名独立评审；
   两类总体≥80%、各类≥70%，完整混淆矩阵按seed/窗口/水域分层报告。
   无人工标NOT_HUMAN_EVALUATED；S/R结果不冒充母计划四分类通过。
2. SHIFT_POSITION_FEASIBLE：真实FULL两Level，原有LandingSafety检查完整AABB、实体、支撑、
   水/熔岩/伤害、边界，双向正式BOUNDED_LANDING±4、精确x/z；修改方块/动态环境后重验。
   density相同或baseHeight接近不能代替此项。
3. ROUTE_COMPLETED：先独立冻结具体起点/目标、entry/exit、移动预算及失败标准，再开始路线观察。
   Survival真实行走和合法Shift到达目标，不依赖surface/飞行；S/R目标每seed两个独立案例。
   没节点、进不去、出不来、超预算均留失败，不能无限挑替代坐标。
   母计划四Phase18次路线与holdout门槛不改，本次S/R证据不构成Phase2准入。

性能冻结三轮交错顺序V1/C1/V2、C1/V2/V1、V2/V1/C1，硬件/视距固定；报告worldgen各阶段
CPU/chunk p50/p95/max、准备成本、cold总计/warm、MSPT/TPS、frame time、heap/GC及reload。
目标相对legacy生成CPU p95≤1.5×、steady MSPT p95≤50ms、无持续heap增长；
作为实测评估门槛，不能写成机器相关unit timing assertion或藏掉prewarm成本。

环境方案可以并行review、最小效果另隔离实验；本实验用中性天空，正式renderer/环境发布另review。
环境、地形、Shift、路线分别判定。Phase2仍NOT_APPROVED，既有0 ROUTE_COMPLETED不改写。

## 7. 验证与B阻塞门禁

```sh
python -m unittest discover -s tools -p test_spatial_v2_manifest.py -v
java tools/SpatialV2MathCheck.java 64 2048  # Java25, pure arithmetic
./gradlew build
```

纯测试仅使用合成seed/证书图，不查询Minecraft或开发/holdout地形。
必须在B数据前通过：A精确manifest/hash获批；含浮点误差的Hc证书实现；有限邻域oracle/StrictMath；
codec/seed/mapAll/bounds/native插值集成；preliminary surface/FULL保护层检查；
准备预算与tick切片、ROI越界硬失败、空世界目录/pack/settings/来源摘要核对。
任何证明或实际API接入失败均BLOCKED，先修订协议review，不生成first-look后调参。
尤其保守dry mask可能使节点图稀疏，8格过渡可能造成围坑，冷包络计算可能过重：这些尚无实测保证。
本轮交付是配置冻结提案及数学fixture，不能据此宣布技术原型、地貌或玩法PASS。

## 8. A执行记录（2026-10-17）

Python纯数学/配置/合成几何15/15通过，Java25 StrictMath检查通过。
Linux的`./gradlew build --offline`通过，主测试361、profiler15，零失败/跳过；
Windows当前分支也完成相同361+15测试，build通过。
Windows默认用户缓存中文路径曾导致Gradle worker/attach agent启动失败：最小参数文件探针
证实UTF8/GBK编码差异。最终只在该次进程使用GBK daemon编码、ASCII临时目录、
ASCII路径的既有ByteBuddy agent副本预载；没有修改构建脚本、JDK版本或生产代码。
可复现命令如下（路径为这台验收机，缓存别名仍指向同一用户缓存）：

```powershell
$env:JAVA_HOME='C:\Users\方便面\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2'
$env:GRADLE_USER_HOME='D:\gradle-cache'
New-Item -ItemType Directory .gradle/validation-tmp -Force | Out-Null
$env:TEMP=(Resolve-Path .gradle/validation-tmp).Path; $env:TMP=$env:TEMP
Copy-Item "$env:GRADLE_USER_HOME/caches/modules-2/files-2.1/net.bytebuddy/byte-buddy-agent/1.17.7/fbf3d6d649ed37fc9e9c59480a05be0a26e3c2da/byte-buddy-agent-1.17.7.jar" .gradle/validation-bytebuddy-agent.jar
$env:JAVA_TOOL_OPTIONS='-javaagent:D:/codex/WorldPhase/.gradle/validation-bytebuddy-agent.jar'
.\gradlew.bat build --offline --no-daemon '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=GBK -Djava.io.tmpdir=D:/codex/WorldPhase/.gradle/validation-tmp'
```

这些环境变量只用于一次验证进程；日志、agent副本、缓存和临时目录均ignored，不提交。
没有V2世界、V2地形采样、截图、性能实测、专用服务器试验或holdout观察。
