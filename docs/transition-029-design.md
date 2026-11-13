# 0.2.9-beta 过渡清理：设计与实现

2026-11-13。命令、关闭后备份与副本清理、中英文结果界面已实现，版本已升级至 0.2.9-beta。已通过构建和纯逻辑验收；按用户要求不启动游戏、不处理玩家实际存档。

## 结论

默认只清连接、约束及其持久化引用，保留方块。已有 disconnect/release 接口证明拆除连接并不普遍要求删除端点方块。不能据此保证任意第三方组合不会崩溃，也不能仅凭离线 NBT 判定任意方块运行时加载失败。

此前问题说明直接删除实体块可能使一个子结构只剩无质量的装饰方块，并保留外部关节。为避免再制造这种状态，不将“删方块”作为默认清理。只有明确定位到无法解除或无法加载的具体方块时，才允许精确删除该方块、附带数据以及相关约束/提供者；严禁整个子结构删除、全 Sable 清空或全 Synaxis 清空。

## 已核对的生命周期

- `FireHoseConnections.disconnect` 清本端及可解析另一端，未加载另一端不能假装已全部处理。Create 运动端点与 HoseRoutes 同样需要处理。
- `SimulatedPhysicalHoseBlockEntity.disconnectPhysical` 通过拥有者销毁 rope；后续 `clearLink -> clearAnchors` 会删除支架。过渡清理必须改用保留支架的专用释放路径，不能直接复用后宣称保留所有方块。
- `BallCouplingBlockEntity.disconnect` 会再次 `beginSearch(true)`；`FluidCouplingBlockEntity.disconnect` 会唤醒另一端搜索。最终方案因此不在线调用 disconnect：命令仅登记内存请求，关闭世界以后处理副本，不需要新增在线禁用状态。
- `SmartRopeConnectorBlockEntity` 在 `OwnedRopes` 和 `ExternalRopes` 保存连接，read 会恢复；需要同时清运行对象和序列化状态，处理由原生 Simulated 另一端持有的 rope。
- `RopeLatchBlockEntity.release` 释放 pin、ropeId、ownerPos、附着记录。管理器的 pending/claim 以及物理支架锚点也必须解除。
- `CouplingPhysics.remove(ServerLevel, feature, id)` 会先取 Synaxis 持久化镜像。应按本模组 feature/owner 删除，不应清空其他模组的约束表。

## 全范围与退出要求

在线命令只遍历已加载方块不能实现“所有连接”。必须覆盖未加载普通区块、Sable 存储、Create 运动结构及相关 SavedData。强制一次载入全世界也不能保证三十秒完成。

用户最终确定：命令仅在内存登记请求，显示“三十秒内保存退出”；不扫描、不备份、不清理、不改变连接。三十秒内没有开始保存退出则请求失效，之后退出也不能执行该旧请求。期限内开始退出则等待正常保存和存储关闭完成，先检查大小/空间并完成备份，再处理全部持久化数据。只有全部阶段成功才标记可换装。多人服务器须由管理员保存停止服务器，单个玩家退出并不等于世界关闭。三十秒是提示，不是超时后允许重新连接或声称自动安全的条件。

未完成、失败和成功需要不同的中英文消息。不能把“命令已受理”显示为“所有清理完成”。清理失败保留可诊断的状态，不宣布可以换装拆分版。

## 参考资料

- NeoForge 命令注册：https://docs.neoforged.net/docs/misc/commands/
- NeoForge 1.21.1 事件总线：https://docs.neoforged.net/docs/1.21.1/concepts/events/
- Sable 关节实现：https://github.com/ryanhcode/sable/blob/main/sable_rapier/src/main/rust/rapier/src/joints.rs

上游 main 仅帮助解释实现；兼容性仍需以本地实际前置版本核对，不将 main 当作所有已发布版本的事实。

## 超时实现与事件顺序核对

`transition/CleanupRequest` 为独立、无文件操作的状态机：WAITING → EXIT_STARTED → READY → CONSUMED；超过30秒（含边界）为 EXPIRED，不能再消费。使用单调时钟，避免系统时间变化。正常保存可以超过30秒，只有开始退出的时刻需要满足期限。

已核对本地映射的 MinecraftServer 源码：stopServer 关闭 levels/resources/storageSource 后，finally 中才触发 ServerLifecycleHooks.handleServerStopped，之后 onServerExit。实际执行仍必须取得session.lock；未获得不能处理。需要在 ServerStoppingEvent 记录是否及时开始退出，在 ServerStoppedEvent 才消费请求。单个玩家登出事件不能用于多人服务器。

用户补充要求已接入：先生成 ZIP 并逐文件校验 SHA-256；超过 512 MiB、单文件超过 64 MiB、超过 100000 个文件、空间不足时拒绝自动处理。超时请求不会进入执行器。需要的可用空间为源文件总大小的三倍加 64 MiB。

## 实现与验收

`TransitionCommands` 注册准备/取消命令，只有期限内进入正常 stopping 才允许 stopped 后运行。`TransitionCleanupJob` 取得 session.lock 后备份，原世界只读，副本转换完成且原文件列表/哈希未变才以原子重命名发布。失败不发布，保留已完成备份，临时目录标记 INCOMPLETE。

先收集本模组绳索 UUID，再清理原生另一端。保留方块 palette、物品/流体、子结构 UUID/姿态；仅删除本模组的 Synaxis 约束、连接字段和四类连接 SavedData。Create mounted storage 的小写连接字段单独处理。支持 Anvil gzip/zlib/raw、Sable 内联/外置记录及普通 NBT；未知或损坏容器拒绝，不跳过后假装成功。世界内数据包 ZIP 原样复制，外部蓝图不处理。

清理连接不是 ID 迁移，也不模拟运行时加载。命令不猜测删除方块；若仍崩溃，使用现有本地工具删除已知旧组件及可追溯的约束提供者，禁止整结构删除。操作说明见 transition-029-player-text.md。

`gradlew check jar --offline` 通过，包括超时状态机、52 项清理断言与现有回归检查。仅使用 build 下自动生成并删除的合成数据；没有启动游戏或处理玩家存档。
