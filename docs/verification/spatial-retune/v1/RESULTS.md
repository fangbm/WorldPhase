# Legacy spatial baseline V1：实测结果

状态：原生产生成基准，不是新地貌/环境/路线验收。完整数据见 [results.json](results.json)。
协议/源码锁：[PROTOCOL.md](PROTOCOL.md)、[protocol.json](protocol.json)。不替换水域/普通样本。

## 环境与范围

Linux x86_64、Ryzen 9 8945HX、RTX 5070 Ti Laptop、driver595.99.02；Java25.0.3+9，MC26.1 / NF26.1.0.19-beta。
每seed：13,780高度行、2,580原生base columns、196,090 raw density点、270插值探针、270梯度归因点。
三seed总计41,340高度行、7,740剖面列、588,270密度点，另810探针及810归因点。
工具不请求Phase FULL chunks；原生server初始化Overworld spawn仍可能加载其原生区块，不能宣称整个进程零FULL。

| seed | 实际Overworld shared spawn | server thread CPU s | wall s | 最大采样work tick ms |
|---|---|---:|---:|---:|
| 123456789 | (64,68,-48) | 65.69 | 126.61 | 102.07 |
| 8675309 | (16,64,80) | 63.08 | 125.00 | 40.28 |
| 20261011 | (-64,88,-192) | 57.11 | 116.50 | 37.54 |

512操作/tick及25ms目标是分批预算，并非硬实时中断；单次原生column可能超时，实测最大102.07ms。
CPU含同线程server维护/保存等工作，wall含等tick；不是孤立kernel benchmark，也不是MSPT分位数。

## 同坐标高度、坡度与局部起伏

coarse每窗口1089点，step64，局部起伏为3×3邻域max-min（128格跨距）。
坡度是采样height有限差分，不是玩家脚下碰撞斜率。A为梯度张量各向异性0..1，不能单独证明长山脊。

| seed / 窗口 | Phase | p5 / median / p95 (y) | std | mean slope | median local relief | A |
|---|---|---|---:|---:|---:|---:|
| 123456789 / forest | smooth | 50.4 / 62 / 78.0 | 8.7 | 0.086 | 14 | 0.03 |
| 123456789 / forest | ridge | 41.0 / 85 / 117.0 | 23.1 | 0.248 | 42 | 0.06 |
| 123456789 / forest | cell | 36.0 / 54 / 163.0 | 43.0 | 0.410 | 72 | 0.12 |
| 123456789 / forest | fracture | 11.0 / 71 / 120.6 | 31.2 | 0.308 | 49 | 0.10 |
| 123456789 / forest | overworld | 37.0 / 64 / 109.0 | 20.7 | 0.177 | 28 | 0.05 |
| 123456789 / highland | smooth | 55.0 / 64 / 86.0 | 10.1 | 0.098 | 19 | 0.05 |
| 123456789 / highland | ridge | 47.0 / 86 / 125.0 | 23.6 | 0.264 | 46 | 0.10 |
| 123456789 / highland | cell | 37.0 / 57 / 165.0 | 43.3 | 0.402 | 73 | 0.14 |
| 123456789 / highland | fracture | 2.0 / 63 / 123.0 | 35.9 | 0.372 | 61 | 0.05 |
| 123456789 / highland | overworld | 53.0 / 70 / 127.0 | 24.8 | 0.219 | 38 | 0.05 |
| 8675309 / forest | smooth | 50.0 / 63 / 81.0 | 10.2 | 0.092 | 17 | 0.06 |
| 8675309 / forest | ridge | 42.0 / 86 / 123.0 | 24.6 | 0.260 | 46 | 0.01 |
| 8675309 / forest | cell | 37.0 / 56 / 160.0 | 40.0 | 0.401 | 65 | 0.08 |
| 8675309 / forest | fracture | -9.0 / 75 / 125.0 | 41.6 | 0.356 | 56 | 0.15 |
| 8675309 / forest | overworld | 38.0 / 70 / 119.0 | 24.9 | 0.212 | 37 | 0.06 |
| 8675309 / highland | smooth | 46.4 / 56 / 69.0 | 7.5 | 0.073 | 12 | 0.08 |
| 8675309 / highland | ridge | 29.0 / 71 / 106.0 | 24.3 | 0.264 | 45 | 0.05 |
| 8675309 / highland | cell | 33.0 / 51 / 158.0 | 40.5 | 0.426 | 72 | 0.10 |
| 8675309 / highland | fracture | -22.0 / 59 / 120.0 | 41.5 | 0.410 | 64 | 0.10 |
| 8675309 / highland | overworld | 34.0 / 50 / 78.6 | 15.9 | 0.111 | 18 | 0.11 |
| 20261011 / forest | smooth | 45.0 / 54 / 65.0 | 6.8 | 0.069 | 12 | 0.06 |
| 20261011 / forest | ridge | 30.0 / 70 / 101.0 | 22.6 | 0.256 | 44 | 0.10 |
| 20261011 / forest | cell | 32.0 / 50 / 157.6 | 38.6 | 0.396 | 67 | 0.04 |
| 20261011 / forest | fracture | -13.6 / 47 / 116.6 | 39.9 | 0.368 | 59 | 0.17 |
| 20261011 / forest | overworld | 34.0 / 46 / 72.6 | 12.8 | 0.113 | 18 | 0.05 |
| 20261011 / highland | smooth | 46.0 / 55 / 71.0 | 7.7 | 0.067 | 12 | 0.10 |
| 20261011 / highland | ridge | 35.0 / 76 / 105.0 | 21.5 | 0.232 | 39 | 0.04 |
| 20261011 / highland | cell | 31.0 / 52 / 160.6 | 43.6 | 0.404 | 63 | 0.14 |
| 20261011 / highland | fracture | 14.0 / 59 / 112.0 | 29.9 | 0.314 | 50 | 0.22 |
| 20261011 / highland | overworld | 34.0 / 46 / 94.0 | 18.8 | 0.115 | 16 | 0.13 |

统一inland子集仅由同位置Overworld base height≥70定义。六窗口分别有393, 548, 573, 138, 89, 148个inland点。
分层仍保留Smooth低起伏、Cell高起伏、Fracture不规则起伏，详见inlandHeightStd；不挑选Phase自身高地来粉饰结果。

## 同坐标 ±4 高度代理（不是安全切换成功率）

fine每窗口289点，step16；仅|baseHeight差|≤4，不检查支撑/液体/实体/完整玩家AABB。

| seed / window | Smooth–Ridge | Smooth–Cell | Smooth–Fracture | Ridge–Cell | Ridge–Fracture | Cell–Fracture |
|---|---:|---:|---:|---:|---:|---:|
| 123456789 / forest | 0.0% | 4.2% | 36.7% | 4.2% | 2.1% | 3.1% |
| 123456789 / highland | 2.4% | 2.8% | 21.1% | 3.1% | 3.1% | 1.7% |
| 8675309 / forest | 13.5% | 3.1% | 8.0% | 5.5% | 14.9% | 4.2% |
| 8675309 / highland | 26.6% | 10.4% | 5.5% | 10.0% | 8.7% | 3.5% |
| 20261011 / forest | 17.0% | 13.5% | 9.0% | 9.7% | 8.7% | 3.5% |
| 20261011 / highland | 20.8% | 18.0% | 7.6% | 10.7% | 5.9% | 7.6% |

## 密度与剖面诊断

810归因点中 `Tp=Gp+B3` 最大绝对残差 1.39e-15，支持“梯度替换而不是旧梯度完整保留”的源码归因。
810插值探针中 raw router 与原生插值最大绝对差 0.108555；两者不可互换，也不能以raw零面直接充当FULL地表。
base columns的平均solid/non-solid跨界次数约1.91–3.66，既有地下空隙；这个有限截面数不是洞穴品质/拓扑保证。
raw体积图为真正(x,y,z)采样的三正交切片，不是从高度图挤出的伪3D；仅8格分辨率，细小洞口会漏。

## 图像索引（全部固定窗口，包括表现不明显区域）

每组含coarse/fine高度+坡度、air/water/lava/solid原生剖面、raw二维密度、raw三维密度切片。
所有高度图共用−32..224色域，坡度0..1.5；越界饱和需看原数据，不可从颜色推断截断地形。
二维密度及三维切片共用±0.458色域，正值blue实体、负值red空气。

### 123456789-forest

[高度/坡度64](plots/123456789-forest-coarse-maps.png) · [高度/坡度16](plots/123456789-forest-fine-maps.png) · [base columns](plots/123456789-forest-columns.png) · [raw density二维](plots/123456789-forest-density-planes.png) · [raw density三维](plots/123456789-forest-density-3d.png) · [高度/起伏分布](plots/123456789-forest-distributions.png)

### 123456789-highland

[高度/坡度64](plots/123456789-highland-coarse-maps.png) · [高度/坡度16](plots/123456789-highland-fine-maps.png) · [base columns](plots/123456789-highland-columns.png) · [raw density二维](plots/123456789-highland-density-planes.png) · [raw density三维](plots/123456789-highland-density-3d.png) · [高度/起伏分布](plots/123456789-highland-distributions.png)

### 8675309-forest

[高度/坡度64](plots/8675309-forest-coarse-maps.png) · [高度/坡度16](plots/8675309-forest-fine-maps.png) · [base columns](plots/8675309-forest-columns.png) · [raw density二维](plots/8675309-forest-density-planes.png) · [raw density三维](plots/8675309-forest-density-3d.png) · [高度/起伏分布](plots/8675309-forest-distributions.png)

### 8675309-highland

[高度/坡度64](plots/8675309-highland-coarse-maps.png) · [高度/坡度16](plots/8675309-highland-fine-maps.png) · [base columns](plots/8675309-highland-columns.png) · [raw density二维](plots/8675309-highland-density-planes.png) · [raw density三维](plots/8675309-highland-density-3d.png) · [高度/起伏分布](plots/8675309-highland-distributions.png)

### 20261011-forest

[高度/坡度64](plots/20261011-forest-coarse-maps.png) · [高度/坡度16](plots/20261011-forest-fine-maps.png) · [base columns](plots/20261011-forest-columns.png) · [raw density二维](plots/20261011-forest-density-planes.png) · [raw density三维](plots/20261011-forest-density-3d.png) · [高度/起伏分布](plots/20261011-forest-distributions.png)

### 20261011-highland

[高度/坡度64](plots/20261011-highland-coarse-maps.png) · [高度/坡度16](plots/20261011-highland-fine-maps.png) · [base columns](plots/20261011-highland-columns.png) · [raw density二维](plots/20261011-highland-density-planes.png) · [raw density三维](plots/20261011-highland-density-3d.png) · [高度/起伏分布](plots/20261011-highland-distributions.png)

## 结构性解释与限制

- Ridge六窗口A=0.01–0.10，宏观方向信号弱；这不能证明不存在任何局部脊线，但不支持“已有定向连续山脊”的强结论。
- Cell六窗口std=38.6–43.6、median局部起伏63–73格，确实有很强的高度分区/陡坡；不能把这些直接称规则蜂窝。F1没有编码精确多边形边界。
- Fracture六窗口std=29.9–41.6，幅度显著，地图中有弯曲谷地；依然未得到断层面/错位岩体的体积证据，不能把噪声warp等同空间机制。
- Smooth六窗口std=6.8–10.2，低于对应Overworld12.8–24.9；低起伏成立，独特连续通道尚未成立，水下海底高度也降低可视辨识度。
- 四Phase高度差存在，不等于视觉辨识度或路线可用性；共同生态/材质/3D细节继续产生Vanilla家族观感。
- 这是两个固定窗口、三个seed的基准，非全球统计，不反推所有世界的连通性。未运行holdout。

## 原生客户端证据

原生固定相机观察及逐图判读见 [NATIVE_OBSERVATIONS.md](NATIVE_OBSERVATIONS.md)。
首次native设置不匹配（Fancy重置sim12）被中止排除；保留ignored history，不改相机/世界生成来重挑图。
最终settings、clock/weather/position逐图保存在measurements内spatial-client.json；不把native截图称人类玩法验收。

## 验证与结论

验证命令/最终结果见同目录 [VALIDATION.md](VALIDATION.md)。
结论：有充分理由进入C方案**架构review**，不是生产实施批准；RETUNE / Phase2 NOT_APPROVED不变。
