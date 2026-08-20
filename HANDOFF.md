# ClimateWeapon 当前开发 Handoff

> 生成日期：2026-08-18  
> 当前版本：`0.1.0`  
> Mod ID：`stormweapon`  
> 目标环境：Minecraft Java Edition `26.2` + Forge `65.1.1` + Java `25`

## 1. 位置与交付规则

- 工作区：`/Users/starrail/Desktop/软件项目/ClimateWeaponMC`
- Gradle 产物：`/Users/starrail/Desktop/软件项目/ClimateWeaponMC/build/libs/climateweapon-0.1.0.jar`
- **最终产出目录（`build` 每次执行后自动同步一份到这里）：`/Users/starrail/Desktop/软件项目/ClimateWeaponMC/dist/climateweapon-0.1.0.jar`**
- **唯一正确的模组部署目录：`/Users/starrail/Desktop/Folder/npp/versions/26.2/mods`**
- 部署后文件：`/Users/starrail/Desktop/Folder/npp/versions/26.2/mods/climateweapon-0.1.0.jar`

每次修改后必须执行（注意：仓库内的 mavenizer 依赖缓存在此环境下无法联网重新下载，不要用 `clean build`，只用 `build`）：

```bash
cd '/Users/starrail/Desktop/软件项目/ClimateWeaponMC'
./gradlew build
cp -f dist/climateweapon-0.1.0.jar '/Users/starrail/Desktop/Folder/npp/versions/26.2/mods/climateweapon-0.1.0.jar'
shasum -a 256 dist/climateweapon-0.1.0.jar '/Users/starrail/Desktop/Folder/npp/versions/26.2/mods/climateweapon-0.1.0.jar'
```

两个 SHA-256 必须一致，然后完全退出并重启 Minecraft，不要依赖资源重载替换正在运行的 JAR。

### 当前已部署产物

- 大小：`592860` bytes
- SHA-256：`2ba0daa8a41835a11605b1b5d4949f4d84fa6e1ca376726090e6bbbbab3f7a6f`
- `./gradlew clean build`：成功
- 源码 OpenGL-specific 调用：无
- 最后一轮的实体引擎声和落雷伤害修改已经编译、打包、部署，但本轮尚未完成真实游戏内的听感/伤害 UAT。

## 2. 当前玩家流程

### 物品 ID

```text
stormweapon:weather_missile_launcher
stormweapon:weather_missile
stormweapon:storm_controller
```

创造模式可从物品栏获取，也可使用：

```mcfunction
/give @s stormweapon:weather_missile_launcher
/give @s stormweapon:weather_missile
/give @s stormweapon:storm_controller
```

- 发射架在 `FUNCTIONAL_BLOCKS` 中。
- 导弹和 Storm Controller 在 `TOOLS_AND_UTILITIES` 中。
- 三者都加入了 OP 物品栏。

### 发射操作

1. 放置 `Weather Missile Launcher`。
2. 手持 `Weather Missile` 右键发射架，导弹被消耗并持久化为 `hasMissile=true`。
3. 空手右键打开 `LauncherControlScreen`。
4. 选择三个预设槽之一，输入目标 `X/Z`。
5. 倒计时可设为 `3–30` 秒，默认 `5` 秒。
6. 点击 `SAVE TARGET`。
7. 第一次点击 `ARM / LAUNCH` 进入 `ARMED`。
8. 第二次点击进入 `COUNTDOWN`，聊天栏每秒显示 `T-N`，直到 `T-0`。
9. 服务器生成真实 `WeatherMissileEntity`，导弹初始缓慢离架，然后加速转向目标。
10. 导弹在目标 `X/Z` 的 `Y=300` 引爆，不撞击地面。

发射架保存：三个 X/Z 预设、装弹状态、当前预设、发射状态、倒计时、冷却。通过 BlockEntity update packet 同步客户端。

## 3. 导弹与发射架实现

### 核心文件

- `block/WeatherLauncherBlock.java`：单方块逻辑占地、装弹交互、打开 UI。
- `blockentity/WeatherLauncherBlockEntity.java`：服务器权威发射状态机、预设、存档、倒计时和生成导弹。
- `client/render/WeatherLauncherRenderer.java`：超出单方块的程序化发射架模型和已装导弹。
- `entity/WeatherMissileEntity.java`：同步目标、飞行阶段、速度、旋转、存档及高空引爆。
- `client/render/WeatherMissileRenderer.java`：程序化 3D 导弹几何体、LOD 和尾焰粒子提交。
- `client/gui/LauncherControlScreen.java`：三组 X/Z 预设、倒计时和武装/发射按钮。
- `network/LauncherControlPacket.java`：服务器验证 UI 输入，玩家必须在发射架 8 格内。

### 导弹飞行

- 独立 EntityType，不是箭、方块、sprite 或纯粒子。
- 发射角：约 `75°`。
- Entity 初始 `18 ticks` 保持点火状态，随后 `66 ticks` 沿导轨加速，然后导引飞向目标。
- 导航速度上限约 `1.55 blocks/tick`。
- 目标距离小于 `2.4` 格或飞行超过 `1800 ticks` 时引爆。
- 引爆位置始终为 `targetX + 0.5, 300, targetZ + 0.5`。

### 发动机音频

- 已删除烟花和火焰弹占位声音。
- 已删除狂风循环音效。
- 当前只有原创 `24 s` 单声道导弹发动机循环：`assets/stormweapon/sounds/missile_engine_loop.ogg`。
- `client/MissileEngineSoundManager.java` 为每枚客户端可见导弹创建一个空间声音实例。
- 声源每 tick 跟随导弹坐标，使用 `LINEAR` attenuation；导弹远去后音量自然递减，导弹移除后短暂衰减并停止。
- 单声道是刻意设计：OpenAL 的实体定位和距离衰减不应使用 stereo 源。

### 已知发射时序细节

`WeatherLauncherBlockEntity` 的 `IGNITION` 在生成 Entity 前持续 `18 ticks`，Entity 生成后自身又有 `18 ticks` 静止点火阶段。因此从 `T-0` 到明显离架约有 `1.8 s`；发动机声从 Entity 生成后才开始。如果后续需要更紧凑，应合并这两段点火时序，但必须同时校准挂架模型与飞行 Entity 的交接位置。

## 4. 气象武器状态

### 导弹引爆流程

```text
ATMOSPHERIC_WAVE (10 s weather ramp; visible wave itself is 4 s)
    -> PEAK_STORM (held until 300 s after detonation)
    -> DECAY (45 s)
    -> CLEARING (45 s)
    -> CLEAR
```

- 大气爆炸波位于高空，不在地面扩散。
- 爆炸波在 `4 s` 内从爆炸中心扩展到半径 `2000 blocks`。
- 天气强度用 `10 s` 快速上升。
- 从引爆时刻起计算，武器在 `300 s` 时进入衰减，随后用 `45 + 45 s` 恢复。
- 当前武器效果是**整个维度全局生效**。`StormSnapshot.radialInfluence()` 在 active 时固定返回 `1.0`。
- `stormRadius` 与 `stormTransitionWidth` 仍在 Common Config 中，但是当前实际全局效果不使用它们。
- 由于是全局 storm，一个维度存在 active storm 时，发射架会拒绝新发射。

### 普通命令风暴

`/climateweapon storm start` 不走导弹的快速流程，而是：

```text
SEEDING (8 s)
-> CLOUD_BUILDUP (17 s)
-> WIND_RISING (15 s)
-> HEAVY_RAIN (18 s)
-> SUPERCELL (20 s)
-> PEAK_STORM (222 s)
-> DECAY (45 s)
-> CLEARING (45 s)
```

## 5. 渲染与天气表现

### 当前方案

- `StormVanillaWeatherBridge` 在客户端把同步的 cloud envelope 映射到 Minecraft 雷暴天色、云彩和天体变暗。
- 这不是服务器 `/weather thunder`，不会显示天气切换聊天提示。
- `StormWeatherEffectRenderer` 会在模组风暴期间抑制原版垂直雨丝，但保留原版雷暴天色。
- `StormRainRenderer` 提交模组自定义的斜雨幕和附近水花。
- `StormWindField` 仍用于云彩漂移、雨丝倾角和闪电一致性，但已没有风屑粒子、狂风循环声、玩家推力、移速 Debuff 或挖掘 Debuff。
- `StormFogController` 提供中等能见度下降：完整强度远雾面约 `165 blocks`，较低强度约 `245 blocks`。
- 旧的自定义黑云 sheet 渲染当前被明确禁用；`StormCloudRenderer` 实例和质量统计仍保留，但 `StormWeatherPass.executes()` 不提交它。
- `StormWeaponEffectsRenderer` 渲染高空大气引爆和扩散环。
- `StormLightningRenderer` 渲染确定性云内闪光和服务器同步的分支落雷。

### Vulkan / OpenGL

- 不依赖 VulkanMod、Iris、OptiFine 或 Sodium 类模组。
- 所有定制几何体通过 Minecraft/Blaze3D `RenderType`、`VertexConsumer`、frame graph 和 Forge 渲染事件提交。
- 模组源码中禁止 `org.lwjgl.opengl.*`、`GL11/20/30/40/45`、`glBind*`、`glUseProgram`。
- 客户端启动后会记录：`StormWeapon graphics backend: Vulkan` 或 `OpenGL`。
- 历史 Vulkan 启动日志已确认 renderer 能加载；最新产物仍需要完整 OpenGL/Vulkan 双后端 UAT。

## 6. 雷电与伤害

雷电分为两种：

1. `StormLightningField` 的云内/远处视觉闪电：客户端效果，无伤害。
2. `StormLightningManager` 的 physical strike：服务器选点、生成 `LightningBolt`、结算伤害，再广播 `StormLightningPacket` 增强渲染。

当前 physical strike 规则：

- 在雷电强度 `>= 0.30` 后启用。
- 默认间隔 `1.5–4.5 s`，低强度时较慢。
- 以随机已加载玩家为观测锚点，在其附近 `18–96 blocks` 抽样，不会持续锁定玩家脚下。
- 落点 `2.5 blocks` 内为完整 `18` 点基础伤害。
- `2.5–8 blocks` 线性衰减到 `0`。
- 最终乘以 `lightningDamageMultiplier`。
- 使用 Minecraft `lightningBolt` DamageSource，保留护甲/免伤/创造模式规则。创造模式不适合验证血量变化。
- `stormFireEnabled=false` 时不点火；开启时保留原版火焰/导体交互，实体伤害仍由模组只结算一次。

## 7. 镜头震动

- 大气引爆后延迟 `10 ticks` / `0.5 s` 开始。
- 持续 `60 ticks` / `3 s`，使用二次衰减。
- `StormDetonationPacket` 是主触发，新的 `ATMOSPHERIC_WAVE` snapshot 是丢包/错过事件的 fallback。
- 旧 `ViewportEvent.ComputeCameraAngles` 方案在当前 26.2 路径上没有可见效果，已改为客户端 tick 中可逆的 yaw/pitch offset；每 tick 先移除上一帧 offset，保留玩家鼠标输入，结束时回到无 offset 朝向。
- 强度受 Client Config `cameraShakeIntensity=0..1` 控制；`0` 为完全关闭。
- 当前方案已编译部署，但需要在最新 JAR 上再做一次目视 UAT。

## 8. 服务器/客户端边界

### 服务器权威

- 发射架状态、装弹、倒计时、冷却。
- 导弹实体飞行和引爆。
- `StormSavedData` 风暴状态机、seed、中心、时间。
- physical lightning 选点、伤害和可选点火。
- 防止 active storm 期间发射新导弹。

### 客户端表现

- 发射架/导弹 mesh、LOD、粒子尾焰。
- 大气引爆和扩散波。
- 斜雨、雾、雷暴天色桥接、云内闪电、程序化落雷和屏幕闪光。
- 镜头震动和空间导弹发动机音频。
- 大量视觉变化由 `seed + gameTime + world cell` 重建，不每 tick 发送完整粒子状态。

### 网络包

| ID | 包 | 方向 | 作用 |
|---:|---|---|---|
| 0 | `StormSyncPacket` | S2C | 同步风暴 snapshot |
| 1 | `StormLightningPacket` | S2C | physical strike 视觉事件 |
| 2 | `LauncherControlPacket` | C2S | 预设/倒计时/武装发射 |
| 3 | `StormDetonationPacket` | S2C | 高空引爆与镜头震动触发 |

通道：`stormweapon:main`，当前 protocol version 为 `1`。

## 9. 命令

全部需要 OP / cheats：

```mcfunction
/climateweapon storm start
/climateweapon storm start <x> <z>
/climateweapon storm stop
/climateweapon storm phase <phase>
/climateweapon status
/climateweapon launcher preset <1..3> <x> <z>
/climateweapon missile launch <x> <z>
/climateweapon debug
```

建议快速验证：

```mcfunction
/climateweapon missile launch ~200 ~200
/climateweapon status
/climateweapon debug
```

`/climateweapon storm phase peak_storm` 可立即进入高强度普通风暴，但不会生成导弹引爆波。

## 10. 配置

### Common / Server

```text
weapon.atmosphericWaveSeconds = 4
weapon.atmosphericWaveRadius = 2000
weapon.effectRampSeconds = 10
weapon.activeSeconds = 300
launcher.cooldownSeconds = 45
phaseSeconds.seeding = 8
phaseSeconds.cloudBuildup = 17
phaseSeconds.windRising = 15
phaseSeconds.heavyRain = 18
phaseSeconds.supercell = 20
phaseSeconds.peakStorm = 222
phaseSeconds.decay = 45
phaseSeconds.clearing = 45
physicalLightningMinSeconds = 1.5
physicalLightningMaxSeconds = 4.5
lightningDamageMultiplier = 1.0
stormFireEnabled = false
```

`stormRadius`/`stormTransitionWidth` 为遗留配置，当前全局效果忽略它们。

### Client

```text
stormQuality = HIGH        # LOW / MEDIUM / HIGH / ULTRA
cloudQuality = 5
rainDensity = 0.55
cameraShakeIntensity = 0.6
lightningFlashIntensity = 0.85
stormFog = true
```

手持 `Storm Controller` 右键可打开游戏内设置界面。它可修改画质、雨密度、云层设置、震动、闪光和雾。

## 11. 源码目录责任

```text
com.stormweapon
├── StormWeaponMod.java             # mod 入口、config/network/event 启动
├── block/                         # 发射架方块交互
├── blockentity/                   # 发射架服务器状态与存档
├── entity/                        # WeatherMissileEntity
├── item/                          # 导弹物品与 Storm Controller
├── registry/                      # block/item/entity/block entity/sound 注册
├── network/                       # 4 种网络包和 SimpleChannel
├── storm/                         # SavedData、phase、snapshot、lightning manager
├── command/                       # /climateweapon
└── client/
    ├── gui/                       # 发射台和画质设置界面
    ├── render/                    # 导弹/发射架程序化渲染
    └── weather/                   # 雨、雾、闪电、波、风场、frame graph pass
```

## 12. 资源

- 语言：`en_us.json` 和 `zh_cn.json`。
- 导弹/发射架物品模型与 blockstate 已存在。
- 天气纹理为原创程序生成资源。
- 当前打包音频只有 `missile_engine_loop.ogg`。
- 发动机 WAV/OGG 可用 `tools/generate_missile_engine_sound.py` 重新生成，需要 Python + NumPy + ffmpeg。
- 项目不包含、复制或分发《三角洲行动》资产。

## 13. 当前已知问题与技术债

1. **README 已过期**：仍称“weather-first prototype、launcher/missile 未实现”，也仍描述 regional 768/1024 范围。实际以本 Handoff 和源码为准，下一次文档任务应重写 README。
2. **mods.toml description 过期**：仍表述为初始 regional weather vertical slice。
3. **尚无生存配方**：当前主要通过创造物品栏、`/give` 和命令测试。
4. **自定义黑云已禁用**：现状是 Minecraft 原版雷暴天色/云彩 + 模组斜雨/雾/闪电。不要误以为 `StormCloudRenderer` 已在主 pass 中运行。
5. **飞行中重复发射的保留问题**：发射架仅在武装/发射时检查 active storm；导弹仍在飞行且风暴尚未引爆时，另一发射架可能发射第二枚。若需严格“同一维度只能有一枚在途导弹”，需要增加 server-side reservation/in-flight registry。
6. **发射架视觉大于碰撞体**：BlockEntity renderer 提交大型模型，逻辑 shape 仍为单方块底座，没有完整 3×5×2 multiblock 碰撞。
7. **最新音频/伤害/震动需要 UAT**：编译已通过，但下一位开发者应优先在实际 Forge 65.1.1 世界验证音频空间衰减、生存模式落雷掉血和引爆镜头震动。
8. **项目目录含中文**：Forge userdev 历史上可能在 `runClient` 遇到 percent-encoded 路径问题；`build` 正常。如果开发客户端启动器失败，可用 ASCII-only 临时副本验证，但最终修改仍要回到本工作区并部署到上述 npp 实例。
9. **无 Git 历史可依赖**：当前工作区不是有效 Git repository，修改前需主动保留用户文件，不要假设可以 `git reset`。

## 14. 建议的立即 UAT

### 导弹与音频

1. 完全重启 Minecraft。
2. 确认声音设置中 `Blocks` 类别不为 0。
3. 装弹，设置距离玩家至少 300 格的目标，完整发射。
4. 确认无烟花声，导弹 Entity 生成时发动机声渐强。
5. 导弹远去时声音应连续递减，不应始终黏在玩家摄像机上。
6. 引爆后发动机声应在短暂收尾后停止。
7. 日志应出现 `Storm Weapon positional missile engine started for entity <id>`。

### 伤害

1. 切换生存模式，关闭或脱下高防护装备。
2. `/climateweapon storm start`，然后 `/climateweapon storm phase peak_storm`。
3. 等待 physical strike，或临时在调试分支缩短间隔。
4. 落点 2.5 格内应受到最高 18 点基础伤害，8 格边缘附近应接近 0。
5. 确认一道 physical strike 没有重复结算两次。

### 镜头震动与恢复

1. `cameraShakeIntensity` 设为 `1.0`。
2. 发射导弹并观察高空引爆。
3. 引爆后 `0.5 s` 应开始震动，在 `3 s` 内由强到弱完全停止。
4. 震动结束后玩家朝向不应有永久偏移。
5. `/climateweapon storm stop` 后天色、斜雨、雾和闪电应使用 client envelope 渐隐，而不是一帧消失。

### 图形后端

在同一世界分别使用：

```text
Prefer Vulkan (Experimental)
Prefer OpenGL
```

每次切换后重启，完整观察发射架、装架导弹、飞行模型、尾焰粒子、爆炸波、斜雨、雾、闪电和 GUI，并检查 `latest.log` 中无渲染/pipeline/shader 异常。

## 15. 下一位开发者的建议顺序

1. 先按第 14 节验证当前已部署 JAR，保存截图和 `latest.log`。
2. 如果发动机声不播放，先搜索日志中的 `positional missile engine started`，再检查 `sounds.json` 解析和 Blocks 音量，不要重新改回烟花声。
3. 如果伤害不明显，必须用生存模式并区分 visual cloud lightning 和 physical ground strike。
4. 修复 UAT 发现的问题后重新 `clean build`并部署到固定 npp `mods` 目录。
5. 更新 README 和 mods.toml，使它们与本 Handoff 的当前功能一致。

