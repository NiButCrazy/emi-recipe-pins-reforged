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

- **合成模式不再被影响** —— 合成模式（Crafting mode）激活时固定卡片自动折叠成图标，EMI 的合成步骤与材料统计保持原生显示，不再被卡片挤出收藏栏
- **修复收藏栏分页** —— 展开的卡片占好几格却只算一个条目，导致翻页会永久跳过部分收藏、收藏接近满页时又翻不出第二页；现在每页容量会按卡片的实际占用折算
- 取消固定后不再残留已经不存在的分页
- 卡片放不下时退回紧凑图标，不再中断整页渲染

## 功能

- 固定配方始终排在普通收藏之前
- 收藏栏中前 N 个固定配方渲染为整宽配方卡片，超出上限的以紧凑图标显示
- 可在收藏栏内拖动卡片调整顺序
- 使用 EMI 的收藏键固定配方（默认 `Q`）
- 卡片上限在 `minecraft/config/emi_recipe_pins.properties` 中配置（默认 `3`）
- 支持任意 EMI 配方，包括其它模组提供的

## 安装

需要 Forge 1.20.1 与 EMI 1.1+。把 `emi_recipe_pins_reforged-1.0.0-1.20.1.jar` 放进 `mods` 目录即可。

> ⚠️ mod id 仍然是 `emi_recipe_pins`。从旧版本升级时请**先删除旧 jar**，同 id 的两个 jar 同时存在会导致加载失败。

## 构建

```powershell
.\gradlew.bat --no-daemon build
```

产物为 `build/libs/emi_recipe_pins_reforged-1.0.0-1.20.1.jar`。

## 协议

MIT，原项目版权归 cat4blep 所有。
