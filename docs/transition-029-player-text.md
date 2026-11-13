# 0.2.9-beta 过渡版操作说明 / Transition instructions

## 中文

此版本仍是消防与动力整合版，供旧环境准备拆分使用。不要与拆分后的 Powertrain 同装。

进入世界时会展示一条固定英文的聊天提示，说明转换专用版本、操作步骤和可点击的 GitHub 文档入口；其中“操作前务必备份存档”以红色粗体显示。执行转换前请先自行备份。

1. 在旧整合环境安装 0.2.9-beta，保留原来的前置依赖。管理员输入 `/cffa prepare_split`（权限等级 2；单人需作弊/管理员权限）。
2. **30 秒内开始保存退出**。多人由管理员执行 `/stop`，单个玩家离开无效。可用 `/cffa cancel_split` 取消。超时后本请求失效，不扫描、备份或清理；以后退出也不会触发它。游戏正常保存不受影响。
3. 期限内开始退出后，等待保存关闭、备份校验与副本清理完成，不要强制结束游戏。正常保存与处理可以超过 30 秒。
4. 客户端显示结果和打开目录按钮；服务端在日志显示完整路径。新世界文件夹与游戏内名称均以 **CFFA-029-CLEANED-** 开头，副本内 `CFFA-TRANSITION-RESULT.txt` 列出原档、备份和输出路径。原档旁的 `CFFA-TRANSITION-BACKUPS` 保存 ZIP 和结果文件。备份只排除游戏自动重建的 session.lock。
5. 退出游戏，把新副本放到同时安装拆分后消防、Powertrain 及其依赖的新环境测试。服务端指向新副本。**不要用整合版重开清理副本，否则可能重建连接。** 原环境和原档保留用于回退。

内置模式只清理本模组的连接、绳索、约束与相关引用，保留方块、库存、流体和子结构。它不迁移拆分后的注册 ID；旧动力组件可能在换装后丢失。成功表示副本生成，不代表已经通过实际游戏运行验收。

自动处理上限：世界 512 MiB、单文件 64 MiB、100000 个文件。可用空间至少为源文件总大小三倍加 64 MiB。超限可能使退出界面长时间等待或占用过多内存，因此停止自动处理并提示本地工具。未知/损坏容器同样停止，不发布可用副本；不要打开带 INCOMPLETE 的临时目录。

大存档或清连接后仍崩溃时，从[GitHub 转换工具目录](https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta)下载同版 `World cleaner.zip`，解压并运行 `Clean_Old_World.bat`，需要 Python 3.10+。工具先以红字提示风险、备份及空间要求，黄字说明所有处理仅在本地进行；随后先选导出目录，再选源存档。工具提示全部为英文，完成后提示关闭窗口。独立工具是**删除旧组件的后备方案**，会清除旧动力组件及可追溯的约束提供方块，但不删除整个子结构。先阅读包内 `docs/legacy-world-cleanup.md`，对原档生成另一个副本。不要先卸载旧模组并保存原档。

## English

This is the integrated Firefighting + Powertrain transition build for an existing integrated environment. Do not install it alongside the split Powertrain mod.

On joining a world, one English-only chat notice identifies this conversion build, summarizes the steps and links to the GitHub documentation. Its reminder to back up before conversion is red and bold. Make your own backup before running the command.

1. Install 0.2.9-beta with the old environment's dependencies. An administrator runs `/cffa prepare_split` (permission level 2; cheats/operator access in singleplayer).
2. **Begin saving and quitting within 30 seconds.** In multiplayer an administrator must run `/stop`; a player leaving is insufficient. `/cffa cancel_split` cancels the request. Timeout expires it without scanning, backup or cleanup, including on a later shutdown. Minecraft's ordinary saving remains unaffected.
3. Wait for normal saving, shutdown, backup verification and copy processing. Do not force-close the process. Saving and processing may take longer than 30 seconds once shutdown begins in time.
4. The client displays the result and an open-folder button; dedicated servers log the full paths. The world folder and display name both start with **CFFA-029-CLEANED-**. The copy's `CFFA-TRANSITION-RESULT.txt` lists all paths. ZIP backups and receipts are under `CFFA-TRANSITION-BACKUPS` beside the original. Backups omit only session.lock, which Minecraft recreates.
5. Exit the game and test the new copy with both split mods and their dependencies. Point a dedicated server at the new copy. **Do not reopen the cleaned copy with the integrated mod: it may rebuild connections.** Keep the original environment and world for recovery.

Built-in cleanup removes this mod's connections, ropes, constraints and references, retaining blocks, inventories, fluids and substructures. It does not remap moved registry IDs; legacy Powertrain components may be lost after switching mods. Success means the copy was generated, not that gameplay loading was verified.

Built-in limits: 512 MiB per world, 64 MiB per file, 100000 files. Free space must cover three times the source size plus 64 MiB. Oversized worlds may cause prolonged shutdown or excessive memory use, so processing stops and directs you to the local tool. Unknown/damaged formats also stop processing. Never open an INCOMPLETE directory as a world.

For large worlds or crashes persisting after connection cleanup, obtain `World cleaner.zip` from the [GitHub conversion tools directory](https://github.com/MIKOALOPEX/CreateFireFightingAdd/tree/main/world-conversion/0.2.9-beta), extract it and run `Clean_Old_World.bat` with Python 3.10+. Red startup text explains the risks, separate-copy behavior and disk-space requirement; yellow text confirms all processing stays local. Select the export directory first, then the source world. All tool prompts are English. Close the window after completion. This fallback **removes legacy components**, including identifiable related constraint-provider blocks, without deleting whole substructures. Read `docs/legacy-world-cleanup.md` first and generate another copy from the original. Do not uninstall the old mods and save over the original world first.
