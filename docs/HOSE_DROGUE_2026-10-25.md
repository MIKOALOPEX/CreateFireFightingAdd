# 流体铰链：Probe / Drogue 软管加油接口

## 原有机制及 GUI 名称

实现位于 `content/fluids/coupling`。`FluidCouplingBlockEntity` 继承 `FireHoseBlockEntity`，用空间索引查找另一子结构上的兼容端点；经 Synaxis/Sable 约束建立连接，再通过原水带端点流体传输逻辑接通背面管道。物理配对角色与泵决定的流向分开，新增接口没有另造流体网络。

GUI 复用 `BallCouplingMenu` / `BallCouplingScreen`，窗口标题为 `fluid_coupling.title`（流体铰链），“接口模式”标签为 `ball_coupling.interface`，“约束模式”为 `ball_coupling.constraint`。

| 接口名称 | 翻译键 | 持久化 ID | 约束 |
|---|---|---|---|
| 自由模式 | fluid_coupling.interface.0 | free | 原有三种可选模式 |
| 硬杆受油口 | fluid_coupling.interface.1 | boom_receptacle | 原有 Pitch 限位 |
| 硬杆输油口 | fluid_coupling.interface.2 | boom_nozzle | 原有 Pitch 限位 |
| 软管输油口（Drogue） | fluid_coupling.interface.3 | hose_drogue | 固定锁定，mode=2 |
| 软管受油口（Probe） | fluid_coupling.interface.4 | hose_probe | 固定锁定，mode=2 |

持久化 ID 均属于 `createfirefightingadd` 命名空间。旧模式编号与 NBT 不变；自由模式设置保留，切回自由模式时继续使用。软管锁定由服务端强制，并非仅禁用 GUI。

## 用户确认的行为

- 首次配对两端必须通电。Drogue 通电展开，完全展开后主动搜索 Probe；同类端口和软硬杆混接均不允许。
- 成功连接后，Probe 可断电而保持连接；Drogue 断电即断开并收起。未完成连接时 Probe 断电会取消配对。
- 展开/收起各 20 游戏 tick，正常 TPS 下约一秒；中途改变信号从当前进度反向运动。
- 保持现有背面管道、流体运输、断开按钮、搜索范围、不同子结构限制和恢复连接机制。

## 模型及动画实现

来源为用户的 `D:/CreateAdd/assets/Powertrain/fluid_coupling/Hose and Drogue`。原模型及 PNG 未修改。Probe 与 Drogue 使用各自提供的同名 PNG，分别重命名安装，避免互相覆盖。

`tools/generate_hose_drogue_models.py` 按展开/收起 BBModel 的 UUID 和对应 JSON 导出独立部件：3 个固定元素、12 个需要平移及 ±22.5° 旋转的 net 元素、16 个仅平移的 ring 元素。固定 UV 使用展开模型的映射，动画中不切换 UV；两端几何位置匹配原模型。进度由服务端保存和同步，客户端按 tick 和 partial tick 连续插值；不会每帧重建模型或创建实体。

源模型沿 -X，统一绕 Z 旋转 -90° 转为现有 +Y 安装坐标，再适配六个放置面。按后续反馈，两端对接点各向安装端回缩 2px：Drogue 从 `(5,8,8)` 改为 `(7,8,8)`，Probe 从 `(-8,8,8)` 改为 `(-6,8,8)`（原模型像素坐标）。对接后的两端整体靠近 4px，模型和动画不变。已建立连接需要先断开再对接，以重建约束。

## 已完成的非游戏验证

- `compileJava processResources` 成功。
- 模型生成校验：448 项原始两端几何顶点比较通过，所有活动部件只作刚性变换。
- `checkFluidCouplingGeometry`：5622 项几何与规则检查通过，包含软管互斥配对、固定约束、六面坐标、NBT 保留及动画反向。
- `checkCouplingHingeFrame`：2004 项原有约束方向及持久化检查通过。
- `jar` 成功。未启动游戏，不能据此宣称视觉、对接或流体实际运输验收通过。

## 一轮完整测试

1. 在流体铰链 GUI 选择两种新接口，点击原有应用勾选按钮；确认各自显示 Probe/Drogue 模型且约束模式固定“锁定”。切回旧接口，确认旧设置仍保留。
2. 单独给 Drogue 通/断红石，检查两端外观和途中反向，net 应旋转并移动、ring 仅移动；分别观察水平和竖直放置。
3. 一端位于世界、另一端位于可动 Sable 子结构，或分属两个不同子结构；端口相对、两端通电，确认展开完成后对接。两端背面沿用原管道/泵连接，验证实际流体输送。
4. 对接后先撤掉 Probe 红石，确认连接继续；再撤掉 Drogue 红石，确认断开、收起且停止输送。中途取消、重新通电和 GUI 主动断开也检查一次。
5. 已连接状态保存重进，检查姿态、动画进度与连接恢复；分别试一次自由/硬杆接口，确认没有回归。

日志标签 `[FLUID-HOSE]` 只在信号、连接和断开变化时记录，包含角色、端点/配对 ID、展开进度、约束模式及建立连接时的锚点距离，不逐帧刷日志。游戏日志沿用本项目 `run/logs/latest.log`。
