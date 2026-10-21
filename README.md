# EMI Recipe Pins Reforged

> 把 EMI 的收藏渲染成 REI 风格固定配方卡片的客户端插件 —— A Clanker-assisted reforged backport of [EMI Recipe Pin](https://modrinth.com/mod/emi-recipe-pin) for Forge 1.20.1.

## 来源

本仓库是重构分支，基于以下项目：

| 项目 | 作者 | 地址 |
| --- | --- | --- |
| **EMI Recipe Pin**（原创） | cat4blep | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/emi-recipe-pin) · [Modrinth](https://modrinth.com/mod/emi-recipe-pin) |
| **EMI Recipe Pin - 1.20 Forge Backport**（直接上游） | HiroLeWeeb | [GitHub](https://github.com/HiroLeWeeb/EMI-Recipe-Pin-1.20) · [Modrinth](https://modrinth.com/mod/forgemi-recipe-pin) |

本仓库在上游基础上做了下面的重构，因而更名 **Reforged**。

## 与原版的区别

- **卡片直接标出缺失材料** —— 固定卡片会按玩家当前库存，在缺少的输入槽上叠加半透明红色遮罩，不需要悬停或按任何键
- **新增独立的普通收藏键** —— EMI 原有收藏键保持原样（配方界面收藏带配方的卡片）；想只收藏物品、不生成卡片时，用模组新增的按键（默认 `P`，可在控制设置中修改）
- **修复收藏栏分页** —— 展开的卡片占好几格却只算一个条目，原版会出现翻页跳条目、收藏接近满页时翻不出第二页的问题；现在第一页按实际渲染条目数切分，后续页按 EMI 原始容量切分，所有收藏都能翻到
- 取消固定后不再残留已经不存在的分页
- 收藏 / 取消收藏时不会闪分页
- 卡片放不下时退回紧凑图标，不再中断整页渲染
- **卡片不再让下层界面穿透** —— 鼠标在卡片上时，下方被遮住的原版槽位、药水效果等不会再显示 tooltip 或响应悬停
- **兼容 StylishEffects** —— 卡片遮住 StylishEffects 的效果图标时，不会再触发它的悬停、tooltip 和点击（可选，未安装时不会加载相关补丁）
- **合成模式不再被影响** —— 合成模式（Crafting mode）激活时固定卡片自动折叠成图标，EMI 的合成步骤与材料统计保持原生显示，不再被卡片挤出收藏栏

## 功能

- 固定配方始终排在普通收藏之前
- 收藏栏中前 N 个固定配方渲染为整宽配方卡片，超出上限的以紧凑图标显示
- 卡片内实时标注缺失材料（半透明红色遮罩）
- 可在收藏栏内拖动卡片调整顺序
- 使用 EMI 的收藏键固定配方（与 EMI 原版一致）
- 在配方界面按模组按键（默认 `P`）只收藏物品本身，不生成固定卡片
- 按住 EMI 的「显示配方」键（默认 `Shift`）悬停卡片或材料槽时显示配方预览
- 卡片上限在 `minecraft/config/emi_recipe_pins.properties` 中配置（默认 `3`）
- 支持任意 EMI 配方，包括其它模组提供的

## 安装

需要 Forge 1.20.1 与 EMI 1.1+。把 `emi_recipe_pins_reforged-2.0.0-1.20.1.jar` 放进 `mods` 目录即可。

> ⚠️ mod id 仍然是 `emi_recipe_pins`。从旧版本升级时请**先删除旧 jar**，同 id 的两个 jar 同时存在会导致加载失败。

## 按键

| 按键 | 默认值 | 作用 |
| --- | --- | --- |
| EMI 收藏键 | EMI 配置中的「收藏」键 | 配方界面收藏为固定卡片，其它界面普通收藏 |
| 添加普通收藏 | `P`（控制设置 → EMI Recipe Pins） | 配方界面里只收藏物品本身，不生成卡片 |

## 构建

```powershell
.\gradlew.bat --no-daemon build
```

产物为 `build/libs/emi_recipe_pins_reforged-2.0.0-1.20.1.jar`。

## 协议

MIT，原项目版权归 cat4blep 所有。
