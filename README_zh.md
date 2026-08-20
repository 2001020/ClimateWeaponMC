# 气象武器 (Climate Weapon)

[English](README.md)

Climate Weapon(模组 ID `stormweapon`)是一个为 Minecraft Java 版 26.2 开发的原创 Forge 模组。
0.1.7 版本是一个“武器化天气”垂直切片:玩家部署发射架,发射制导导弹,导弹在高空引爆并触发一次
全维度范围的极端天气事件,在此之上依然保留了项目最初的区域性风暴系统。

天气效果不是简单包装 `/weather thunder`。它是一个服务器权威的生命周期状态机,客户端桥接到
Minecraft 原版雷暴天色、世界锚定的风驱动雨幕、能见度雾、确定性云内闪光、程序化落雷通道、屏幕
曝光闪光,以及稀疏的服务器权威伤害性落雷。这个桥接不会修改服务器天气本身;自定义的雨、风和
闪电始终由 Climate Weapon 自行控制。

## 环境要求

- Minecraft Java 版 26.2
- Minecraft Forge 65.1.x(开发与测试基于 65.1.1)
- 开发环境需要 Java 25

## 安装

1. 为 Minecraft 26.2 安装 Forge 65.1.x。
2. 把 `climateweapon-0.1.7.jar` 复制到实例的 `mods` 目录。
3. 使用该 Forge 配置启动 Minecraft。

不需要 VulkanMod、Iris、OptiFine 或 Sodium 系模组作为依赖。

## Vulkan

Minecraft 26.2 内置实验性原生 Vulkan 后端,可在以下位置开启:

`选项 -> 视频设置 -> 图形 API -> 优先使用 Vulkan(实验性)`

切换后端后需要重启 Minecraft。Climate Weapon 只通过 Minecraft/Blaze3D 的 RenderType 和
frame-graph API 提交几何体,不包含任何直接的 OpenGL 调用,同样支持 `优先使用 OpenGL`。启动后
当前后端会记录为以下两种之一:

```text
StormWeapon graphics backend: Vulkan
StormWeapon graphics backend: OpenGL
```

## 0.1.7 版本玩法

请在开启作弊/管理员权限的世界中游玩,目前还没有生存模式配方。物品可以从创造模式物品栏
(`Climate Weapon` 分类)获取,也可以用 `/give` 命令获取:

```mcfunction
/give @s stormweapon:weather_missile_launcher
/give @s stormweapon:weather_missile
/give @s stormweapon:storm_controller
/give @s stormweapon:signal_connector
```

共有五种导弹:`雷电导弹(Thunder Missile)`、`浓雾导弹(Fog Missile)`、
`陨石导弹(Meteor Missile,高破坏性)`、`暴风雪导弹(Blizzard Missile)`、
`樱花导弹(Cherry Blossom Missile)`。

### 发射流程

1. 放置一个 `Weather Missile Launcher`(气象导弹发射架)。
2. 手持任意导弹物品,右键点击发射架完成装填;导弹物品会被消耗。
3. 空手右键发射架,打开发射控制界面。
4. 选择三个预设槽之一,输入目标 X/Z 坐标,保存。
5. 设置 3–30 秒的倒计时(默认 5 秒)。
6. 第一次点击 `ARM / LAUNCH` 进入武装状态,再次点击开始倒计时;倒计时期间聊天栏每秒显示一次
   `T-N` 警告,直到 `T-0`。
7. 服务器生成真实的导弹实体,导弹缓慢离架后加速并制导飞向目标,最终在目标 X/Z 上空
   `Y=300` 处引爆,而不是撞击地面。

可以使用 `信号连接器(Signal Connector)` 把发射架绑定到按钮或拉杆上,实现远程/红石触发。
同一时间全服/全维度只能有一次天气事件生效,因此在事件进行期间发射架会拒绝新的发射。

### 引爆后的天气流程

```text
ATMOSPHERIC_WAVE(大气爆炸波,10 秒渐变,可见波持续 4 秒,半径 2000 格)
    -> PEAK_STORM(引爆后维持 300 秒)
    -> DECAY(衰减,45 秒)
    -> CLEARING(转晴,45 秒)
    -> CLEAR(结束)
```

该效果是**全维度**生效的,不再是区域性的。

### 命令直接触发的普通风暴

如果不想用导弹,也可以直接用命令驱动区域性风暴生命周期,便于快速测试:

`SEEDING -> CLOUD_BUILDUP -> WIND_RISING -> HEAVY_RAIN -> SUPERCELL -> PEAK_STORM -> DECAY -> CLEARING`

## 命令

以下命令均需要管理员/作弊权限。

```text
/climateweapon storm start
/climateweapon storm start <x> <z>
/climateweapon storm stop
/climateweapon storm phase <phase>
/climateweapon status
/climateweapon launcher preset <1..3> <x> <z>
/climateweapon missile launch <x> <z>
/climateweapon debug
```

快速视觉验证:

```mcfunction
/climateweapon missile launch ~200 ~200
/climateweapon status
/climateweapon debug
```

调试 HUD 会显示阶段计时、引爆中心、局部影响强度、云/雨/闪电包络、风向与雨丝倾角、云/雨预算、
当前活跃闪电,以及当前图形后端。

## 配置

服务器/公共配置(`stormweapon-common.toml`):

- `weapon.atmosphericWaveSeconds`、`weapon.atmosphericWaveRadius`、`weapon.effectRampSeconds`、
  `weapon.activeSeconds`
- `launcher.cooldownSeconds`
- `phaseSeconds` 下各阶段时长(供命令直接触发的普通风暴使用)
- `physicalLightningMinSeconds`、`physicalLightningMaxSeconds`
- `lightningDamageMultiplier`
- `stormFireEnabled`(默认 `false`)

客户端配置(`stormweapon-client.toml`),也可以手持 `Storm Controller`(风暴控制器)物品
右键打开游戏内设置界面修改:

- `stormQuality`:`LOW` / `MEDIUM` / `HIGH` / `ULTRA`
- `cloudQuality`(为休眠中的实验性自定义云渲染器保留)
- `rainDensity`
- `cameraShakeIntensity`:引爆后镜头震动强度,`0` 为完全关闭
- `lightningFlashIntensity`
- `stormFog`

## 构建

```bash
./gradlew build -Djava.net.useSystemProxies=false
```

产物 JAR 会生成在 `build/libs/` 下,并复制一份到 `dist/`。额外的 Java 网络代理属性仅在系统代理
干扰 Gradle TLS 连接的环境下需要,其他情况可以省略。

在 macOS 上,开发客户端运行会附加 `-XstartOnFirstThread`。Forge 65.1.1 的 userdev 可能无法正确
处理包含非 ASCII 字符且被百分号编码的项目路径;如果 `runClient`/`runGameTestServer` 遇到这个
Forge 启动器问题,可以使用一个纯 ASCII 路径的临时检出来验证。正常的模组安装和 `build` 不受影响。

## 原创资源

本仓库中的所有代码、贴图和音频均为原创。本模组不包含也不再分发来自其他游戏的资源、音频、
着色器、logo 或代码。
