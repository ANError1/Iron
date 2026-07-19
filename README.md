# warning

This mod is in an experimental phase, so it’s best not to use it
I recommend using [Embeddium](https://github.com/FiniteReality/embeddium) or [Sodium](https://github.com/CaffeineMC/sodium)

<img src="src/main/resources/icon.png" width="128">

# Iron

Iron is an unofficial offshoot of Embeddium, The primary focus of development is performance ~~(because my computer is so shoddy)~~ and practical

This project is not supported by Embeddedt or CaffeineMC. Please do not report issues related to this project (Iron) to their Discord, GitHub, or Gitea channels

Versions 1.21.4 and above require sodium as a dependency ~~(although this is not currently the case:)~~

I’m a beginner in Java, so I use AI-tools to help me solve some problemsl:)

## Compatibility

**Never compatible with OptiFine!**

The following are known compatible rubidium/embeddium accessories:
- [Oculus](https://github.com/Asek3/Oculus)
- [rubidium extra](https://github.com/dima-dencep/rubidium-extra) (Some features are duplicated)
- [Sodium/Embeddium extras](https://github.com/txnimc/MagnesiumExtras) (Some features are duplicated)
- [RuOK](https://github.com/MCTeamPotato/RuOK)
- [Sodium/Embeddium Dynamic Lights](https://github.com/txnimc/DynamicLightsReforged)
- [Sodium options mod compat](https://github.com/txnimc/SodiumOptionsModCompat)

- [SodiumOptionsAPI](https://github.com/txnimc/SodiumOptionsAPI)(It’s not fully compatible, and there might be some bugs, ~~but the same applies to Embeddium~~)
- [chloride](https://github.com/SrRapero720/chloride) (It should be compatible; tested only in the NeoForge environment)

This mod is not compatible with any mobile devices! If you wish to use it on a mobile platform, please use [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues-release) or [Embeddium for Holy GL4ES](https://github.com/chatiane3-gb/Embeddium-for-HolyGL4ES)

## Supported versions

| Version | Loader         | Support          |
|---------|----------------|------------------|
| 1.20.1  | (Neo)Forge     | Active           |
| 1.20.X  |  NeoForge      | No plan          |
| 1.21.1  |  NeoForge      | Planned support  |
| 1.21.11 |  NeoForge      |Suspension pending|
| 1.12.2  |  Cleanroom     |   In progress    |

> 1.12.2 is expected to need Java 21 or higher, this means you’ll need to use the Cleanroom Loader

> [Cleanroom Loader download](https://github.com/CleanroomMC/Cleanroom/releases)

**Unlisted versions have either ceased receiving updates or are no longer supported! Please do not submit issues for these versions! as they will be closed immediately. Unless they are also valid for supported versions!**


# How to build

0.Prerequisites

- JDK 17+
- Git
- Windows / macOS / linux equipment (Or any device capable of using Git and a terminal)

1.Clone the repository:
```bash
git clone https://github.com/ANError1/Iron.git
 ```
2.Open the CMD or git
3.Enter 
```bash
cd Iron

.\gradlew build
# or
gradle build
 ```
4.Wait for a while ~~(which could last up to 6 hours)~~
~~5.Build failed:)~~
5.1.The Jar file should be located at Iron/build/libs

If you’re not familiar with building, please take a look at the [Actions](https://github.com/ANError1/Iron/actions), there might be a usable Jar available there:)

You can also fork a repository and add it to your GitHub account. Then, simply make a commit, and you should be able to find the Jar file in the Actions section

## Remarks

If you plan to publish it on another platform, please ask me about it (although I usually agree, it’s still a good idea to ask)

You can open a separate issue to inquire about this (as I am primarily active on GitHub)

## Here is the original description from Embeddium:

# Embeddium

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/sk9rgfiA?label=Modrinth&labelColor=%232D2D2D)](https://modrinth.com/mod/embeddium)
[![Modrinth Version](https://img.shields.io/modrinth/v/sk9rgfiA?label=Latest%20version&labelColor=%232D2D2D)](https://modrinth.com/mod/embeddium/versions)
[![](http://cf.way2muchnoise.eu/short_embeddium_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/embeddium)
[![](http://cf.way2muchnoise.eu/versions/Available%20for_embeddium_full.svg)](https://www.curseforge.com/minecraft/mc-mods/embeddium/files)

Embeddium is a free and open-source performance mod for Minecraft clients. It is based on the last FOSS-licensed version of the Sodium codebase, and includes additional bugfixes & features for better mod compatibility.

Despite being forked from Sodium, Embeddium is **not supported by CaffeineMC**. Do not report issues encountered with it to their bug tracker or in their Discord. I have my own [Discord server](https://discord.gg/rN9Y7caguP) for my mods.

## Highlights

* All performance improvements from Sodium 0.5.8 and earlier, i.e. a rewritten terrain renderer, various optimizations to the immediate-mode rendering pipeline (used by entities, GUIs, block entities, etc.), and other miscellaneous improvements
* Available for Minecraft Forge on 1.20.2 and older, and Fabric/NeoForge on 1.20.1 and newer
* Integrated Fabric Rendering API support (Indium is no longer required, and will not work with Embeddium)
* Frequent patch updates to fix mod compatibility issues soon after being reported & reproduced
* Additional APIs for mod integration
* Optional support for translucency sorting (can be enabled in Video Settings)

## For developers

If you're looking to add Embeddium to your development environment, please take a look at the [dedicated wiki page](https://github.com/embeddedt/embeddium/wiki/For-Developers) for instructions & recommended guidelines for integration.

## Credits

* JellySquid & the CaffeineMC team, for making Sodium in the first place, without which this project would not be possible
* Asek3, for the initial port to Forge
* embeddedt, Rubidium was improved and branched into Embeddium, from which this mod is forked
* XFactHD, for providing a list of gamebreaking Rubidium issues to start this work off, and for testing early builds
* Pepper, for their invaluable assistance with getting Forge lighting to work on Sodium 0.5
* @CelestialAbyss and @input-Here for making the new logo design ~~(In fact, I designed Iron’s new logo)~~

## License

Embeddium is licensed under the Lesser GNU General Public License version 3.

Portions of the option screen code are based on Reese's Sodium Options by FlashyReese, and are used under the terms of
the [MIT license](https://opensource.org/license/mit), located in `src/main/resources/licenses/rso.txt`. 
