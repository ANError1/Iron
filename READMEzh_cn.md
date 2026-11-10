因为英文README翻译可能不太准确，所以我创建了这个README

此README可能不是最新的，建议查看英文的README:)

# 警告

此mod目前处于实验性阶段，最好不要使用
我推荐使用 [Embeddium](https://github.com/FiniteReality/embeddium) 或 [Sodium](https://github.com/CaffeineMC/sodium)

<img src="src/main/resources/icon.png" width="128">

# Iron (铁)

Iron是Embeddium的一个非官方分支, 开发方向是性能 ~~(因为我的电脑太垃圾了)~~ 和实用性

这个项目不被 Embeddedt 或 CaffeineMC 支持，请不要向它们的GitHub、Discord或Gitea反馈此项目(Iron)的问题

1.21.4以上需要Sodium为前置 ~~(虽然目前没有:)~~

~~我是一个Java初学者，所以我可能会有AI帮我解决一些问题~~

# 兼容性

**永远不兼容OptiFine!**

以下是已知兼容的Rubidium / Embeddium附属
- [Oculus](https://github.com/Asek3/Oculus)
- [rubidium extra](https://github.com/dima-dencep/rubidium-extra) (部分功能重复)
- [Sodium/Embeddium extras](https://github.com/txnimc/MagnesiumExtras) (部分功能重复)
- [RuOK](https://github.com/MCTeamPotato/RuOK)
- [Sodium/Embeddium Dynamic Lights](https://github.com/txnimc/DynamicLightsReforged)
- [Sodium options mod compat](https://github.com/txnimc/SodiumOptionsModCompat)

- [SodiumOptionsAPI](https://github.com/txnimc/SodiumOptionsAPI)(不完全兼容,依然存在bug, ~~但bug同时适用于Embeddium~~)
- [chloride](https://github.com/SrRapero720/chloride) (应该兼容，仅在NeoForge环境测试)

此mod完全不兼容移动端,如果你想要在移动端使用,请使用 [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues-release) 或 [Embeddium for Holy GL4ES](https://github.com/chatiane3-gb/Embeddium-for-HolyGL4ES)

# 版本支持

| 版本    |    加载器      |    支持情况 |
|---------|----------------|------------|
| 1.20.1  | (Neo)Forge     |    活跃     |
| 1.20.X  |  NeoForge      |   无计划    |
| 1.21.1  |  NeoForge      |   计划中    |
| 1.21.11 |  NeoForge      |    暂停     |
| 1.12.2  |  Cleanroom     |    暂停     |

**未列出的版本要么停更，要么不支持，请不要反馈未列出版本的bug，它们会被直接关闭，除非它们对支持的版本也有效!**


# 如何构建

0.前提条件

- JDK 17+ ~~因为MC1.20.1需要JDK17(应该是吧)~~
- Git
- Windows / macOS / linux 设备(或任意可以使用git与终端的设备)

1.克隆此仓库:
```bash
  git clone https://github.com/ANError1/Iron.git
 ```
2.你应该可以在 C:\Users\<your user name> 找到Iron文件夹，打开它
3.打开CMD
4.输入
```bash
  cd Iron

  .\gradlew build
   # 或
   gradle build
 ```
5.等待一会 ~~(可能长达6小时)~~
~~6.构建失败:)~~
6.1.Jar文件应该会出现在 C:\Users\<your username>\Iron\build\libs

如果你不知道如何构建，去看看 Actions, 那里可能有Jar:)

你也可以Fork这个仓库，然后随便提交一次，就可以在Actions找到

# 备注

如果你要发布此项目到其它平台，请问一下我(虽然大多数会同意，但任然建议问一下)

~~此README我写的时候我都忘了我写了什么，我还要看一下翻译，当然此RAEDME是ANError(作者)写的~~

~~如果出现错别字，你可以开一个PR~~

# 以下是Embeddium的原描述:

# Embeddium

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/sk9rgfiA?label=Modrinth&labelColor=%232D2D2D)](https://modrinth.com/mod/embeddium)
[![Modrinth Version](https://img.shields.io/modrinth/v/sk9rgfiA?label=Latest%20version&labelColor=%232D2D2D)](https://modrinth.com/mod/embeddium/versions)
[![](http://cf.way2muchnoise.eu/short_embeddium_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/embeddium)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_embeddium_full.svg)](https://www.curseforge.com/minecraft/mc-mods/embeddium/files)

Embeddium 是一个开源并且免费的Minecraft客户端优化mod,它基于Sodium最后一个自由开源许可证的代码库，包含了额外的功能与修复，以兼容其它mod

Embeddium尽管从Sodium(~~Rubidium~~)分支出来，但CaffeineMC不支持Embeddium,不要向他们的bug追踪器或Discord报告遇到的问题, Embeddium有自己的[Discord](https://discord.gg/rN9Y7caguP) 给我(Embeddedt)的mod

## 亮点

* 所有的优化都来自Sodium 0.5.8与更早版本，即重新编写的地形渲染器、对即时模式渲染流水线（用于实体、图形用户界面、块实体等）的各种优化，以及其他杂项改进
* 支持minecraft forge 1.20.2 - 与 Fabric / NeoForge 1.20.1 + 
* 集成 Fabric Rendering API (Indium 不需要，也不兼容)
* 在问题被报告并复现后，频繁更新补丁以修复模组兼容性问题
* 额外的API
* 可选的支持半透明排序 ~~这翻译好像不对吧~~ (可在视频设置开关)

## 开发者

如果你想将 Embeddium 添加到你的开发环境中，请查看专门的[wiki 页面](https://github.com/embeddedt/embeddium/wiki/For-Developers) ，里面有集成的说明和推荐指南

## 致谢

* JellySquid 与 CaffeineMC ，他们最初创建了Sodium, 如果没有他们，这个mod也不可能存在
* Asek3, 最初的Forge移植
* embeddedt, Rubidium 被改进并分支为 Embeddium, 这个mod就是从那里分支出来的
* XFactHD, 用于提供一份破坏游戏的Rubidium问题列表，参与了早期的测试
* Pepper, 他们在让Forge光照在 Sodium 0.5 上正常工作的宝贵帮助
* @CelestialAbyss and @input-Here 制作了Embeddium的新图标 ~~(事实上我(ANError)设计了Iron的图标)~~

## 许可

Embeddium 使用GUN LGPL V3 许可

选项屏幕代码的部分内容基于FlashyReese的Reese's sodium options，并根据[MIT许可证](https://opensource.org/license/mit)使用, 位于`src/main/resources/licenses/rso.txt`
