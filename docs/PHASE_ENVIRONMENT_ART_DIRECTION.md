# Phase 天空与环境方向：MC 26.1 / NeoForge 26.1.0.19-beta

状态：**设计与API审计，未添加渲染器或修改生产维度**。关联[Issue #34](https://github.com/fangbm/WorldPhase/issues/34)。
配套[空间重设计](SPATIAL_RETUNE_PLAN.md)。环境通过、地形通过、路线通过分别判断。

## 1. 目标与共享可读性

四个世界应在天空轮廓、云层结构、运动方式、远景层次与声音节奏上具有身份，不能只给画面染色。
地下stone/dirt、水/熔岩/火、敌对生物与碰撞边缘保持可辨。天空不更改物理几何，也不替代真实空间解法。
继续Overworld式日夜、下雨、睡觉与生物行为；仅表现重解释，不偷偷改变mob light/spawn规则。

以下是美术提案，**不是本轮已实现效果**。所有候选颜色需实际客户端/灰度对照确认，非最终palette。

| 元素 | Smooth | Ridge | Cell | Fracture |
|---|---|---|---|---|
| 天空/天体轮廓 | 开阔连续穹顶，清晰太阳/月亮，薄层柔和地平线 | 高对比有方向光，太阳边缘清楚，山墙剪影可读 | 太阳/月亮旁稀疏分段环/多边形弧；保留天体方位 | 静态错位弧与分层天幕裂纹，不闪屏、不抖相机 |
| 雾/远景 | 稀薄远雾、宽谷层次，近景干净 | 谷地层雾、山脊明暗层次，不能把出口藏进浓雾 | 单元远景分层，边界层次清晰，雾不画假碰撞墙 | 远景少量错层条带，真实断层边缘前保留视野 |
| 云形 | 薄而宽的长纤维，缓慢连续移动 | 沿主脊方向伸展的带状云，少量分层高云 | 大块平顶/切角单元云，稀疏间隙，不棋盘格噪声 | 不同高度的偏移云片，固定裂口；低速非连续位移仅远景渐变 |
| 色调/照明 | 中性偏暖天空、柔和但保留地表对比 | 冷中性天顶，暖侧光刻画山墙 | 中性清晰漫反射，浅金/灰蓝轮廓强调平面 | 低饱和灰紫/青远景、正常近景照明，危险色不反转 |
| 环境音 | 低密度柔风、开阔谷回响，少量自然声 | 山风定向层次、低频风切，非持续轰鸣 | 稀疏短反射/轻微共鸣，单元节律而非电子警报 | 远处低频岩石张力与迟缓裂响，不冒充怪物/玩家脚步 |
| 标志粒子 | 偶发缓慢花粉/浮尘，地表附近 | 很少量顺风尘线 | 稀疏细小短直线/切角微尘 | 远处低频偏移碎尘，近景不闪、不遮挡 |
| 昼夜/天气 | 清晨宽光带，夜间星空平稳；雨仍清楚 | 日落侧光强调脊线，夜间山墙分离；雨雪顺风视觉 | 环段随正常日夜旋转，夜间稀疏规则星组 | 日夜节律正常，天幕错层缓变，雷暴不额外频闪 |

Smooth的差异来自空旷与缓慢连续层次，Ridge来自方向性，Cell来自几何分区，Fracture来自
错位/断续结构；四种运动/构图语法不同。天体/云可与地形方向同源，但只读稳定seed配置，
不读取任意未同步服务端Level引用，也不逐帧计算worldgen。

## 2. 当前版本 API 审计

核对对象是本仓库实际 `minecraft-patched-26.1.0.19-beta-sources.jar` 及
`neoforge-26.1.0.19-beta-sources.jar`，不是旧版DimensionSpecialEffects示例，
也不是网上26.2/master签名。官方[26.1迁移说明](https://docs.neoforged.net/primer/docs/26.1/)
及[1.21.11环境属性迁移说明](https://docs.neoforged.net/primer/docs/1.21.11/)说明背景；
精确实现以[pinned源码artifact](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.0.19-beta/neoforge-26.1.0.19-beta-sources.jar)为准。

### 2.1 DimensionType 与数据驱动属性

实际 `net.minecraft.world.level.dimension.DimensionType` 是record，含 `skybox`、
`cardinalLightType`、`EnvironmentAttributeMap attributes`、`HolderSet<Timeline> timelines`、
`Optional<Holder<WorldClock>> defaultClock`，以及坐标比例、高度、skylight/ceiling等行为字段。
codec JSON使用 `skybox`、`cardinal_light`、`attributes`、`timelines`、`default_clock`。
**没有旧 `effects` 字段**；不设计 RegisterDimensionSpecialEffectsEvent。

新Phase dimension type应以Overworld行为字段为基准（coord1、minY-64、height384、skylight、
无ceiling、正常monster light/床行为），各自拥有独立environment attributes。
复用Overworld默认clock与gameplay timelines；不能为了固定天色把正式世界永久设为noon。
当前生产四Phase仍是minecraft:overworld类型，所以天空/天气身份一致属于现状，不是API坏了。

`EnvironmentAttributes` 中本方案相关的真实ID：

| 数据属性 | 可实现范围 |
|---|---|
| `minecraft:visual/sky_color`, `fog_color`, `sunrise_sunset_color` | sky/fog是RGB_COLOR，sunrise_sunset是ARGB_COLOR；按各自codec验证，不混写RGB/ARGB |
| `minecraft:visual/fog_start_distance`, `fog_end_distance`, `sky_fog_end_distance`, `cloud_fog_end_distance` | 雾范围；必须验证能见度、天气/水下覆盖，不能作为隐藏地形缺陷的手段 |
| `minecraft:visual/water_fog_color`, `water_fog_start_distance`, `water_fog_end_distance` | 水下表现，不能改变水/熔岩危险语义 |
| `minecraft:visual/cloud_color`, `cloud_height` | 原生云色/高度；不能凭此得到多边形或断裂云mesh |
| `minecraft:visual/sun_angle`, `moon_angle`, `star_angle`, `moon_phase`, `star_brightness` | 天体角度/星光；不能凭此得到新天体几何 |
| `minecraft:visual/block_light_tint`, `sky_light_color`, `sky_light_factor`, `ambient_light_color` | 光色/视觉因子；与server `gameplay/sky_light_level` 区分 |
| `minecraft:visual/ambient_particles`, `default_dripstone_particle` | 现有粒子类型的参数化表现；新形状需新provider，不应滥用dripstone影响玩家判断 |
| `minecraft:audio/background_music`, `music_volume`, `ambient_sounds` | 原生音乐与ambient mood/additions；新增音频仍需合法资源与SoundEvent |

属性有各自同步、插值和spatial规则，不是任意字符串贴进JSON都有效。
必须对有效/无效payload、注册时机、客户端实际最终值进行小型fixture测试。

### 2.2 合成顺序：不要被 biome 覆盖

检查 `EnvironmentAttributeSystem.Builder` 实际构建路径：默认值 → dimension常量 →
位置相关biome属性/插值 → dimension timelines → weather内置变化；客户端还有sky-flash等局部效果。
plains biome明确提供 `minecraft:visual/sky_color`，所以仅设置dimension sky_color可能被覆盖。

实施PR先做**最终采样值探针**，选plains/forest/mountain/ocean以及边界、昼夜、晴雨、水下，
记录最终sky/fog/cloud/light属性，不以JSON加载成功代替效果确认。
可选方案是独立**仅视觉timeline**在biome之后合成，或client渲染状态的显式Phase风格合成；
不能先假定多个timeline/标签holder的顺序稳定。保留原Overworld gameplay timelines，不复制一套
易漂移的睡觉/怪物规则。若数据层无法可靠表达优先级，使用最小client组合器并单测，不做全局滤镜。

### 2.3 NeoForge 自定义环境渲染入口

`NeoForgeEnvironmentAttributes` 注册的实际同步ID为：

```text
neoforge:custom_skybox
neoforge:custom_clouds
neoforge:custom_weather_effects
```

值是Identifier，默认 `minecraft:default`。不是 `neoforge:visual/...`。
未来仅在需要几何时给各Phase选择renderer id；关闭特效可回退native。

客户端mod bus的 **`RegisterCustomEnvironmentEffectRendererEvent`**：

```java
registerSkyboxRenderer(Identifier, CustomSkyboxRenderer)
registerCloudRenderer(Identifier, CustomCloudsRenderer)
registerWeatherEffectRenderer(Identifier, CustomWeatherEffectRenderer)
```

不能覆盖 `minecraft:default`。该事件是logical client / 非cancellable。
26.1.0.19-beta的回调签名（这是审计摘录，不是本轮实现）：

```java
boolean renderSky(LevelRenderState, SkyRenderState, Matrix4fc, Runnable setupFog);
boolean renderClouds(LevelRenderState, Vec3 cameraPosition, CloudStatus,
                     int cloudColor, float cloudHeight, int cloudRange, Matrix4fc);
boolean renderSnowAndRain(LevelRenderState, WeatherRenderState,
                         MultiBufferSource, Vec3 cameraPosition);
boolean tickRain(ClientLevel, int ticks, Camera);
```

返回true表示该路径已处理、跳过相应Vanilla表现；false走原生fallback。
SkyRenderState/WeatherRenderState等属于当前extract/render分离体系，不能用旧版本回调参数。
通过 `ExtractLevelRenderStateEvent` 提取并存入LevelRenderState的只读自定义数据，
渲染阶段不跨线程读取可变Level/服务器worldgen服务。

## 3. 数据与客户端代码的明确分工

| 需求 | 数据优先 | 需要客户端时的最小扩展 |
|---|---|---|
| 基本天空/雾/云色与昼夜曲线 | DimensionType attributes + visual timeline | 仅为解决实际覆盖优先级加入局部合成，先证明必要性 |
| Smooth薄纤维云 / Ridge方向带云 | 原生云是低配fallback，色/高度可配置 | CustomCloudsRenderer，共享轻量mesh/纹理，稳定方向与速度 |
| Cell多边形云、分段环 | 原生天体角度/颜色不能生成几何 | CustomSkybox/CloudsRenderer，静态低面数网格和少量层 |
| Fracture静态天幕裂口、错位云层 | 基色/雾可数据 | 非闪烁背景几何；不做全屏后处理/相机扭曲 |
| 音效 | SoundEvent/资源+ambient/music属性 | 定向/多层混音确有需求时局部client音景控制，限制声部 |
| 标志粒子 | 复用ParticleOptions与ambient属性 | 真正新几何才注册provider；默认低密度，可关闭 |
| 雨雪 | 原生优先 | 仅风向形态确有需求才CustomWeatherEffectRenderer，不改weather模拟 |

本轮不增加任何上述renderer/provider/resources。Phase1地形原型必须在native中性天空下先验收，
防止把环境风格当作地形辨识度。

### 服务端隔离

公共端只持有Identifier、codec与维度环境数据；全部 `net.minecraft.client.*`、
渲染状态、mesh、音频控制器、事件订阅放client专属入口/源文件，Dist.CLIENT门控。
不可在公共静态字段引用client类、不可从server注册lambda意外捕获renderer。
未来用dedicated cold/start/save/reload验证classloading；制品检查公共入口不引用client。
不为天空引入网络包/新权限系统/通用渲染框架。

## 4. 帧预算、低配与可访问性

提供原生fallback、低、标准三个可关闭等级（未来client配置，不是新GUI项目）。
关闭几何云/粒子/特殊天空/音景各自独立，不影响碰撞、seed、传送或路线。

- 原生fallback保持可读天空、正常太阳/月亮；不让不支持特效的玩家失去基本方位/危险信息。
- 低档每种天空一个缓存mesh、最多两层静态云，不做体积raymarch/多层透明全屏/逐帧网格重建。
- 标准档粒子可见总量初始预算≤128、音景同时声部≤4；先profile再提高。无无限远粒子或常驻大纹理。
- 相对native baseline，环境增量目标median≤1ms、p95≤2ms（参考GPU）；再在真正低端硬件测fallback。
  这些是后续预算，不是当前已测结果，也不能写机器相关unit timingassert。
- 禁闪烁裂纹、突然白屏、镜头抖动、强色差、强烈周期明暗；Fracture错位缓慢发生在远景，
  可设置静态。尊重lightning闪光相关客户端选项，不额外制造光敏刺激。
- 颜色不是唯一编码：云轮廓/方向/节奏/音景均可识别；灰度与常见色觉模拟检查。
- 雾/云不能遮挡近处洞口、熔岩、支撑边缘；夜间保留对比，音效不冒充敌人或落石碰撞提示。
- 音景有独立音量、可静音；关音后仍可玩，不让听觉成为唯一必要教程线索。

## 5. 冻结验证矩阵（未来环境 PR）

同3seed与本轮camera/window；再加Overworld-type对照、plains/forest/mountain/ocean、
洞穴/水下与biome边界。每Phase测noon/sunset/night、clear/rain/thunder、全部特效等级，
精确记录最终attribute值、clock、weather、显卡驱动、frame-time/显存、GC和资源重载。
新增native截图必须与真实framebuffer关联，不用美术概念图作为实现证据。

验收分开记录：

1. `ENVIRONMENT_IDENTITY`：天空区域盲辨，去除地形/UI，目标总体≥80%、每Phase≥70%，
   两名独立评审、固定样本数和混淆矩阵；低档至少能凭轮廓/运动区分，不要求fallback相同效果。
2. `ACCESSIBILITY_READABILITY`：静态/关粒子/静音、灰度、昼夜和水下危险识别正常，特效不改变玩法信息。
3. `ENVIRONMENT_PERFORMANCE`：固定视距/硬件native对照、三轮、median/p95、allocation/显存与重载释放。
4. `SERVER_SAFE`：dedicated不加载client类，dimension/attribute codecs正常，切换/重连/重载不泄漏。
5. `TERRAIN_IDENTITY` 和 `GAMEPLAY_ROUTE`：仍由空间方案独立验证；前三项全PASS不能覆盖它们FAIL。

当前结论仍是 **规划，不是环境效果完成；RETUNE，不是Phase2批准**。
