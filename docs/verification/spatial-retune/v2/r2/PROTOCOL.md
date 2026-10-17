# WPH-016-02 B2 — r2 Shared Node 冻结提案

Refs #36 / #34。基于 B1 merge `29d326e7efb06e647d62a268d7a926894ee0387e`。
本轮仅设计、纯数学和合成几何；**不实现 MC 接线，不生成 V2 世界/截图，不读取新 MC 地形**。
[manifest](EXPERIMENT_MANIFEST.json) 是 r2 独立配置；[外部摘要](FROZEN_SHA256.txt) 锁定原始 LF JSON。
状态保持历史冻结提案，review 批准记录在 PR，不修改 payload。r1 全部文件与负面证据不动。
Phase 2 NOT_APPROVED，r1 `--require-pass` 必须继续非零。

## 1. r1 失败归因：已知、可推导与未知

|开发 seed|全部节点|dry/band 优先拒绝|dry/band 通过后 slope 拒绝|通过模型者|
|---|---:|---:|---:|---:|
|123456789|477|353|124|0|
|8675309|477|365|112|0|
|20261011|477|391|86|0|

来源为未改动的 [B1 evidence](../preflight-evidence.json) 与 [结果](../PREFLIGHT_B1_RESULTS.md)。
分类有优先级，不能说只有后列具有陡坡，也不能把拒绝总数当作 FULL 危险点数量。
历史未逐节点归档 V/Bx/Bz/E，无法从现有摘要精确拆分 dry vs band、实际陡峭 vs 区间过宽，
更无法报告缩到身体范围能救回多少真实节点。本轮不补跑 MC 数据来伪造这些归因。

结构性事实：Hc 的模型范围 [-7,133]，若程序误差 E<1，则 Hlo-32>-40、Hhi+20<240；
因此 band 本来有很大余量，dry 下界≥71 是主要嫌疑，精确逐节点分类仍 UNKNOWN。
参考 `63+64*C` 的全局范围本身包含大面积低于海面的区域；这是宏观设计限制，不是舍入问题。
raw continents 包含多 octave 和坐标 shift，先枚举所有整数 V 再双线性扩展并没有滤除高频。
64 倍边差加上 N2 的全局最坏梯度、跨所有单元取最大，可能由远离实际站位的单条边决定失败；
两梯度分量的最大值还可能不在同一点出现。这是保守性来源，但没有证据说所有 slope 拒绝都是 false negative。

中心±32 外接方形面积4096；半径32圆盘约3217，方形多约27.3%。玩家宽0.6的投影仅.36；
它们不能直接相除来降低证明范围，因为还要覆盖完整 AABB、支撑、邻近候选和原生插值足迹。
r2 明确测试宽≤1.2、高≤2 的身体、中心走廊半宽2以及4×8×4 noise cell 的守护范围。
±4 要求的是同 x/z 上两侧真实落点、完整碰撞空间、支撑/液体/危险检查和来源不变；
不要求64×64全域平缓，也不要求无穷小 binary64 斜率。落点检查与可走出节点是独立职责。

## 2. 两个冻结方案，不能只挑成功的一个回报

|方案|机制|优点|硬限制/风险|
|---|---|---|---|
|A LOCAL_PHYSICAL_A_R2|同一 r2 分布；保留自然 Hc，局部足迹有限物理增量+FULL 双侧验证|不造平台；局部证书成本小；不需要 rounded 函数齐次 Lipschitz|不能消除海洋、真实断崖、两 Phase 高差或封闭出口；yield UNKNOWN|
|B CONTROLLED_PATCH_B_R2（主提案）|共享平面核心、有限长度受控坡道、经证书的自然终端接驳|核心不继承 raw C 高频坡度；出入口是强制 gate；可解释改地范围|会改变地貌；出口接不上就拒绝；可能仍零节点，不能自动当作成功|

A/B 使用相同 r2 IDs、seed、ROI、dry/shortlist 排序和预算，不按 seed 更换方案。
L=1024、jitter=128 由下面的支持区域面积及不相交条件选择，不是从真实地形调参得到。
没有比较过实际 r2 terrain，也不能把“合成平地能接驳”说成 useful majority。

A 的详细身体/插值领域取中心±10方形（不是只看一个 block）；最多22²高度顶点，
共享整数raw缓存后≤23²调用/节点，16节点+anchors≤8992 raw calls。
仅可用真实连续包络/已证明的有限增量上界，不能用quarter-block离散高度差冒充区间证明。
A 不承诺解决 r1 availability，必须保留所有拒绝和出口/FULL 失败。

## 3. B 的确定性核心与分布

p=(x,z)，y与长度均为blocks，density无量纲，正实体、负空、零仅界面。
M/h/u 沿用r1的64位 wrapping定义，节点 x/z tag 改为 manifest 中两个 r2 tag；不使用seed+phaseIndex。
`c(i,j)=1024*(i+.5,j+.5)+128*(2*u_x-1,2*u_z-1)`，signed IDs；已存储 binary64 中心定义模型坐标。
距离是全局坐标差，不按chunk重算。每个ID的 Hc reference/seed/配置全 Phase 相同。

anchor：只算中心 Hc 的保守点区间 [lo,hi]，误差证明≤2^-24，floor(lo)=floor(hi) 才取
`h=floor(lo)+.5`。跨整数bin、非有限或证据未知即拒绝，不能任取一边；h必须≥71、
[h-32,h+20]在[-40,240]内。不是把海洋clamp抬到71。半格量化与r1不同，最大改变不到.5，必须版本化。
`Dn=(h-y)/16`，无独立3D细节。核心半径16，共同collar到24；背景在24..176平滑恢复 Phase。

令 `Q(t)=t³*(10+t*(-15+6*t))`，t先clamp[0,1]。Q∈[0,1]，C2，Q'(t)=30t²(1-t)²≤15/8。
所有式按显式括号 binary64 计算，不允许 lerp helper 偷换运算次序或FMA。
`b=1-Q((r-24)/152)`，`r=|p-c|`，
`Db=(1-b)*DpCore+b*Dn`，`Hb=(1-b)*Hp+b*h`，Hp是各Phase的2D宏观高度，DpCore含该Phase3D细节。

分布：最小节点间距≥1024-2*128=768，大于两倍192，支持域互不重叠。
core理想覆盖约.0767%；collar inclusive约.1726%；192支持圆面积上界约11.045%，分别统计。
192包含176背景、坡道消隐及横向/原生插值足迹，不代表这些面积全平或全安全。
至少约88.95%的渐近平面没有本节点DF影响；FULL结构/树木等传播不在该几何面积证明内。
两ROI并集的实际影响上界须≤15%，按core/collar/transition/exit分别输出，并单列每窗口面积；
不是把渐近比例冒充有限窗口精确值，也不能为过预算动态删掉不喜欢的节点。

点查询只需floor(p/1024)±1的9个ID：遗漏中心至少一轴相距1408>192。
跨chunk、负坐标及不同遍历顺序使用同一中心/不可变记录，等距按ID字典序。

## 4. 受控坡道与终端，不允许环形孤岛

四个方向E/W/N/S；单位方向d及其垂线n，u=dot(p-c,d)，v=dot(p-c,n)，仅轴向符号变换。
走廊中心|v|≤2；密度控制plateau |v|≤10；横向消隐到14。
q=`Q((u-16)/4)*(1-Q((u-176)/8))*(1-Q((abs(v)-10)/4))`。
不同方向的q支持不相交（u>16与另一路横坐标绝对值<14矛盾），因此没有浮点求和的遍历顺序选择。
起段在u20已全权控制；横向plateau在u20时仍处共同collar，接缝没有高度跃变。

每Phase/方向有独立终点 `hend=h+delta`，来自该方向u152,v0的 Hp **有证明区间**：[lo_p,hi_p]误差≤2^-24且floor两端相同，才取hend=floor(lo_p)+.5；
此bin必须唯一。当前端点位于u152,v0，|delta|≤8；失败不改变h或延长坡道。
`R(u)=h+delta*Q((u-24)/128)`，u24..152；前面为h，后面为hend。
精确模型 `|R'|≤(15/8)*8/128=15/128=.1171875`，低于原1/8，留出浮点余量。

终端u152..176使用 `T=Q((u-152)/24)`，`J=hend+T*(Hp-hend)`，到176后等于Hp。
证书领域额外包含出口u176..184、|v|≤10。全域须证明 `|Hp-hend|≤.5`、`||grad Hp||≤1/16`，
或能推出同等有限物理增量的严格包络；当前合成原型使用连续梯度版，不假装已经证明实际原生 Hp。
则 `||grad J||≤1/16+(15/8)*.5/24=13/128=.1015625`。
端点Q'=0，在152接水平hend、176接Hp，C1连续；u176..184保留自然退出段。
核心/单调坡道/终端最低模型高度≥65（海平面+2），仍须FULL检查实际水/熔岩和支撑。

`Hport=R`到152、之后J；`Dport=(Hport-y)/16 + T*detail_p`，detail_p是DpCore已有3D项。
u≥176后Dport与DpCore相同；q消隐发生在176..184，此时背景已恢复DpCore。
`Dctrl=(1-sum(q))*Db + sum(q*Dport)`，`Hctrl`同样由Hb/Hport混合。
非活动节点或非选中/未在两Phase共同通过的端口q=0；只有两 Phase 共同的相反方向对通过才允许节点成为 MATH_NODE_READY。
允许其余扇区陡峭，**不允许缺乏相反出入口的封闭环墙节点进入可用集合**。
E/W优先于N/S，只是固定tie-break，不是观察失败后换高度/方向；FULL失败后不换另一对掩盖失败。

近表层保护采用r1的正确组合位置：
`W=1-(1-b)*(1-sum(q*(1-T)))`，`P=W*Py(Hctrl,y)`；
`Fsolid=post(slide(Dctrl))`（无caves/noodle），
`Dfinal=(1-P)*Fnative(Dctrl)+P*Fsolid`，P选择位于最后noodle之后，无外层interpolated。
核心/控制坡道的中央带 P=1；终端逐步恢复自然细节/caves；外围 P=0。
本式是待接线的r2数学契约，尚未证明原生插值/3D细节/洞穴后的零面等于Hctrl。
preliminary surface必须用同一无洞穴受控分支，不得沿用错误的旧reference；bounds需重新含浮点误差推导。

端点位置、range/gradient/bin误差都必须证明；证据缺失即NO_COMMON_OPPOSITE_PORTS/相应拒绝原因。
一个数学坡道不能证明穿过洞穴、水、树、结构、实体或玩家造的墙。FULL检查必须独立拒绝这些情况。

## 5. 三层证书，不调高1/8

1. **连续模型层**：常高核心L=0、坡道L≤15/128、终端≤13/128，以及clamp/Q边界和支持域不相交证明。
   原生外部Hp的range/gradient是输入前提，不在B2伪造。
2. **有限物理浮点层**：定义dmin=.25，程序误差cap E=2^-20 blocks。
   同一已证明的连续走廊域内，d≥.25才使用 `|Hhat(p)-Hhat(q)|≤L*d+2E`。
   对坡道finite slope≤15/128+8E≈.11719513，终端≤13/128+8E≈.10157013，均仍小于.125。
   d更小时只声明模型+点误差包络，不声称rounded程序具有齐次Lipschitz。
   `shared_nodes_r2.py` 用精确Fraction区间/完整ulp传播减、除、Q及加权运算，证明这两段标量公式的E预算；
   端点半格差是精确dyadic，外部Hp程序误差须≤2^-24。后续Java25须逐位对照并补齐mask、距离和完整DF误差。
3. **Native/FULL层**：先实际NoiseChunk/preliminary/base-column，再真实FULL碰撞、实体、脚下形状、流体、
   危险块、双向±4和路线。carver/aquifer/features/structure均可能破坏前两层。

core身体宽≤1.2时，核心边缘16+.6+sqrt(4²+4²)<24，原生插值点有共同collar；
走廊中心半宽2+.6+sqrt(32)<10。此为水平覆盖余量，不是完整3D碰撞证明。
2^-20是明确的物理误差预算而不是放宽坡度阈值；若实际接线证明不了此值，停止并提新版本。
DF激活依赖冻结后的MATH_NODE_READY，不依赖FULL或玩家状态；FULL失败不能反过来移除节点、重生成或隐藏失败地形。
任何已观测到环墙隔离的节点令整个profile失败；未通过FULL的候选绝不叫安全节点。
玩家建造、破坏、流体/实体变化使旧FULL站位失效；每次Shift仍调用现有LandingSafety/Resolver，
不缓存永久安全结论，不改TransferPolicy，不把baseHeight直接变为落点。

## 6. 数据依赖与准备成本

避免循环：seed→132 target IDs→中心Hc区间/ANCHOR_DRY→anchor valley graph→Hp→端口证书→MATH_NODE_READY。
Smooth谷线图端点仅依赖ANCHOR_DRY，不依赖端口成功；节点无法接驳不会反过来改变已用于验证的Hp。
该图是地貌线图，不是安全道路。失败节点附近仍可能有谷线，需独立视觉/路线验收。

Gaussian宽64、double-zero半径2048；起点floor(p/1024)±3，共98边/64端点足够。
遗漏有限线段至少一轴相距2432>2048，按原Java25 StrictMath下溢规则贡献零；不是全局最近边距离。
两固定ROI各±1536，连端点halo并集恰132 ID（cap256），中心每个4 raw C calls，上界528。
各窗口从支撑圆192完全在窗口内的dry anchors按已存储中心的精确dyadic距离²/ID取前8，合并≤16；重复不补齐。
端口失败不追加新候选、不换seed/加半径。未入选为DEFERRED_NOT_SHORTLISTED，绝不偷标eligible。

终端领域32×20，枚举全部覆盖整数顶点≤34×22=748；两Phase共用原始整数 C 缓存，
Hp每点的4角最多再加一行列，raw unique≤35×23=805/port。
总上界16*4*805+132*4=**52048**（r1实际2077812，约39.9倍少的raw调用上界对比）；
不是CPU速度提升39.9倍。两个Phase的高度work items≤95744，Smooth最坏还有4,691,456次边测试。
后者可能成为新瓶颈，不能只数raw调用。缓存必须按raw seed+profile/source摘要+x/z共享，
任何miss越域、非有限、容量不足或中途失败都终止整个准备，不发布半成品。

每tick最多2048 raw calls，同时对几何、区间及metadata也切片；软8ms、硬25ms，
整个arm/seed CPU≤1500ms、≤256tick、scheduled wall≤15000ms；cache限65536 raw /98304 phase columns。
单调用无法抢占，超时立即记录BLOCKED并停止后续工作。冷初始化/构造也计入，不能只测warm循环。
时间预算是**未来实测失败标准**；本轮纯数学计数不证明这些时间成立。
上下文retained复审限32MiB，单准备线程分配cap256MiB；必须实测owned heap/分配，
不能把进程heap差值直接当context retained。始终owner server thread，无异步Level采样。
最低按call cap饱和需ceil(52048/2048)=26tick；软时间限可能更多，不能把1.3秒下界当预计真实wall。

## 7. 可否证的门禁与独立路线验收

- **Math/native gate**：三个老seed每个窗口至少2个MATH_NODE_READY；全体候选、拒绝、DEFERRED、
  真正面积、分布/组件全报。任一seed达不到就FAIL，不降低标准、不无限调参。
- **Native gate**：r2独立codec/key、raw seed、mapAll、actual holder bound、finite errors、bounds、
  原生插值和preliminary一致性先过；READY不是授权。B1的forced-P与六个探针不证明r2。
- **FULL/Shift gate**：每窗口按同一排序取前2就绪节点，合并≤4/seed，不因失败换点；
  首个共同相反方向对做完整真实端口检查。至少75%通过，且每seed同窗口至少2点通过。
  身体/支撑/流体/危险、实际±4双向传送、UUID/inventory保持，均与数学yield分开。
  FULL显式请求cap512、≤1/tick、单步25ms、≤2400ticks/120s/10s CPU；邻域连带生成另报，
  不声称一个FULL请求只生成一个chunk。不可抢占调用超过预算仍记录FAIL，不偷偷加限。
- **图与路线 gate**：分别报告anchor-valley graph、共用端口的endpoint-only graph与实际走过的边。
  endpoint组件连通不等于中间约1024格能走通。为每seed固定排序最多3组同窗口点对尝试，
  至少1条自然障碍→正常Shift→步行/跳跃→出口/目标到达的真实有用路线完成；无DEV_SURFACE/飞行跳障碍。
  走廊/端点可用不等于ROUTE_COMPLETED。若仍0路线，可形成负面RETUNE/REDESIGN结论。
- **地形视觉 gate**：沿用母计划的独立辨识度/空间用途验收与旧V1/C1对照。共享平地与天空通过不能替代地貌通过。

## 8. 冻结、复现与边界

先提交manifest、本协议和纯原型/测试为冻结commit；然后运行数学测试并把结果作为后继证据提交，
不在看结果后改阈值或失败seed。若公式/参数本身错，不覆盖r2改成“通过”，应记录BLOCKED并提新版本。
新字段/codec都只列计划ID；本PR没有MC源码/注册/Gradle行为更改。
未来先独立review批准r2，再做原生预检；预检通过后**另行批准**新测试世界生成。
世界路径/资源版本与r1分开，旧存档不升级，任何source/seed/config/digest失配硬失败。
C1仍只有旧系数对照；r1仍仅负面数字证据，禁止为公平对照去生成被阻塞的r1世界。
holdout ID只保存在manifest，不调用其hash/地形/候选搜索；开发种子不替换。

复现命令（Python标准库；没有MC调用）：

```sh
python -m unittest discover -s tools -p test_shared_nodes_r2.py
python tools/shared_nodes_r2.py --write-report
python tools/shared_nodes_r2.py --require-native  # 必须非零，不能批准worldgen
```

纯测试会区分：理想连续模型、binary64标量误差、**合成**线性插值、模拟后续挖空的反例。
没有真实NoiseChunk、FULL、实际seed地形yield、CPU/MSPT或人类走路线结果，绝不写为PASS。
