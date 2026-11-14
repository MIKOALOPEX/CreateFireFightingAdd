> 2026-11-12 更新：按用户要求，已移除两个模组的旧存档检查、加载拦截及自动迁移标记写入。当前允许直接载入旧存档测试；下文迁移工具是可选离线工具，不会自动运行。先前关于加载保护的交付记录已失效。

# 拆分存档迁移（开发中）

旧整合版同时把消防与动力内容注册到 `createfirefightingadd`。新动力使用 `createpowertrain`，不能仅替换 jar 后直接打开原存档。特别是旧 `hose_bracket` 同时代表普通和物理支架，需要读取方块实体的 `PhysicalEndpoint` 分流。

当前工具 `tools/split_migration/convert.py` 仅用于离线检查和生成单独的转换副本。运行前关闭正在使用该存档的客户端或服务器。

```powershell
# 仅读取，打印检查报告；不生成转换存档。
python tools/split_migration/convert.py D:/Minecraft/backups/old-world

# 目标必须尚不存在，且不得位于源目录内部。
python tools/split_migration/convert.py D:/Minecraft/backups/old-world --output D:/Minecraft/converted-world
```

已实现原生 NBT、Anvil 区域文件、物品及物品组件、原生结构模板、Create 动态结构的注册 ID 转换。普通/物理支架按位置与实体数据对应分流，跨调色板位宽时重新编码索引。节点、端口和连接 UUID 不重新生成；自有约束的 owner、feature 和 SavedData 身份不进行全局命名空间替换。

转换先检查全部文件，再建立临时输出目录；任何未知二进制容器或无法辨识的支架结构都会报错，不生成最终输出。源文件摘要用于检测转换期间发生的修改。源目录不被写入。

已按 Sable 2.0.5 实际字节码实现 `.slvlr`（128 字节扇区）、`.slvls`（4096 字节扇区）及外置 `.slvl` 数据。保留存储槽索引、子空间 UUID、姿态、指针和外置文件路径；支持 gzip 与原始 NBT。区块数据仍按原生调色板规则转换，嵌套的区块和结构模板会递归处理。

容器的重叠/越界扇区、缺失外置文件、未知标志或转换后超出单记录 255 扇区的情况会拒绝输出。Sable Schematic API 常规导出的 gzip `.nbt` 蓝图已支持：按 `block_palette`、`palette_id` 与 `block_entity_data_id` 关联分流支架，保留 `source_uuid`。嵌套原生结构模板也会转换。未知第三方封装、原生 Anvil 外置区块和 LZ4 压缩仍会明确拒绝，不应删除报错文件或强制忽略错误。

工具只有在所有文件验证成功后才输出新目录；输出中的 `level.dat` 和 `level.dat_old` 根节点写入整数 `CreateAdditionsSplitVersion=1`，供加载前保护识别。只读检查不会改写这个标记到源存档。

17 项合成数据测试已通过，覆盖两种 Sable 扇区布局、外置数据、嵌套区块、UUID 保留、损坏输入拒绝和源目录不变。这不代表真实存档迁移验收完成；仍须在副本中验证未加载区块、容器物品、动态结构、物理连接及重新载入。当前不支持直接替换旧整合版后加载未经转换的存档。
