# Combat Helper (Minecraft Tactical Combat & Aiming Assistant)

[English](README.md) | [简体中文](README_CN.md)

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-blue.svg)
![Forge Version](https://img.shields.io/badge/Forge-47.4.10-red.svg)
![Compatible](https://img.shields.io/badge/Modern%20Firearms-TACZ%20%7C%20Point%20Blank%20%7C%20JEG%20%7C%20Scguns-orange.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)

Combat Helper is an understated, high-efficiency combat enhancement and firearm tactical assistant mod tailored for Minecraft 1.20.1 (Forge).

### Background & Genesis
Originally created to solve a very specific gameplay bottleneck—endgame weapons in magic mods like **MythicBotany** (e.g. Alfsteel blades) boast attack speeds so rapid that human clicking cannot reach full DPS, while ordinary autoclickers drop damage and break special left-click weapon beam/energy releases—Combat Helper began as a frame-accurate attack cooldown synchronizer. Over time, it evolved into a comprehensive tactical suite featuring bow Runge-Kutta numerical lead calculation, modern firearm fire control (TACZ, Point Blank, JEG, Scguns), automatic recoil compensation, empty-chamber auto-reloading, and an immersive dark side-drawer console.

---

## Table of Contents

- [Features](#features)
  - [1. Intelligent Melee Cooldown Sync & Special Weapon Beams](#1-intelligent-melee-cooldown-sync--special-weapon-beams)
  - [2. Modern Firearm Tactical Suite (TACZ / Point Blank / JEG / Scguns)](#2-modern-firearm-tactical-suite)
  - [3. Ranged Bow/Crossbow Ballistics & Lead Interception](#3-ranged-bowcrossbow-ballistics--lead-interception)
  - [4. Three Target Search & Aim Assist Modes](#4-three-target-search--aim-assist-modes)
  - [5. Dark Side-Drawer Tactical Console](#5-dark-side-drawer-tactical-console)
  - [6. Real-Time Tactical HUD Monitor (U Key)](#6-real-time-tactical-hud-monitor-u-key)
  - [7. Global 3-Second Confirmation for Destructive Actions](#7-global-3-second-confirmation-for-destructive-actions)
- [Keybindings](#keybindings)
- [Configuration Reference](#configuration-reference)
- [Compatibility](#compatibility)
- [Installation](#installation)
- [AI Development Disclaimer](#ai-development-disclaimer)
- [License](#license)

---

## Features

### 1. Intelligent Melee Cooldown Sync & Special Weapon Beams
- **100% Full Attack Speed Output**: Automatically swings at the exact tick attack cooldown reaches zero, unlocking the maximum damage potential of high-speed weapons (MythicBotany, Botania, Better Combat, etc.) without finger fatigue.
- **Weapon Beam & Special Ability Preservation**: Unlike blunt autoclickers, timing strictly respects native item usage cycles, ensuring that left-click energy beams, mana bursts, and weapon skills fire reliably.
- **Configurable Invulnerability Frame Removal**: Optional setting to bypass target damage immunity ticks (0-tick attacks).

### 2. Modern Firearm Tactical Suite
Deep native support for Timeless and Classics: Zero (TACZ), Point Blank, Just Enough Guns (JEG), and Scguns:
- **Aim Assist & Triggerbot**: Automatically aligns with the target's designated body part (supports headshot priority) and fires automatically upon line of sight.
- **Empty-Chamber Auto-Reloading**:
  - **Full AmmoBox & Inventory Integration**: Supports TACZ `IAmmoBox` containers and loose ammo items. Detects reserve ammo inside high-capacity boxes in the player's inventory seamlessly.
  - **Custom Feed Mechanisms**: Full support for non-standard ammo items (e.g., JEG emerald blocks for Vindicator SMG) without namespace restrictions.
  - **Server Cooldown Packet Protection**: Enforces an optimal cooldown buffer after shooting before dispatching reload requests, preventing server-side reload drop glitches where animations play but no bullets are loaded.
  - **Anti-Lock Deadlock Guard**: Prevents infinite reload loops when truly out of ammunition.
- **Factory Straight-Aim**: Modern firearms automatically adopt high muzzle velocity and zero-gravity straight-aim logic, avoiding excessive projectile elevation.
- **Anti-Recoil Compensation**: Counteracts vertical pitch kicks and continuous fire camera shake.

### 3. Ranged Bow/Crossbow Ballistics & Lead Interception
- **4th-Order Runge-Kutta Ballistic Integration**: Solves 3D projectile trajectories factoring in muzzle velocity, gravity, and drag coefficients.
- **Relative Momentum Reverse Compensation**: Compensates for player and target relative velocities, accounting for height offsets and flight states while maintaining stable tracking.
- **3D Lead Indicator**: Projects an accurate green interception ring along the enemy's movement vector.
- **Full Draw Auto-Shoot**: Automatically releases arrows the moment full charge is achieved.
- **Zero-Gravity Bow Support**: Built-in support for ExtraBotany Failnaught bow and customizable zero-gravity lists.

### 4. Three Target Search & Aim Assist Modes
- **Mode 1: Hover-Timed Lock-On**: Automatically locks onto a target when the crosshair hovers over it for a configurable duration (0.1s - 3.0s).
- **Mode 2: Always Auto Lock-On**: Automatically tracks the nearest valid target within range while allowing free manual camera adjustments while holding weapons.
- **Mode 3: Manual Hotkey Mode**: Target locking is exclusively controlled via hotkey (default: R / Middle Mouse).
- **Target Part Selection**:
  - HEAD: Headshot priority for precision firearms and bows.
  - TORSO: Center of mass for consistent damage output.
  - ADAPTIVE: Dynamically transitions between headshots at close range and torso aim at long distance.

### 5. Dark Side-Drawer Tactical Console
Accessible in-game via the Configured mod configuration menu:
- **Four Dedicated Tabs**:
  - [Lock-On]: Range, smoothing speed, target part, and search mode selection.
  - [Ballistics]: Velocity adjustment, gravity tuning, trajectory/lead rings, and HUD distance/health overlays.
  - [Profiles]: Cached weapon profiles. Supports alphabetical/chronological sorting, ascending/descending directions, and 15-item pagination to guarantee zero UI lag.
  - [Lists]: Zero-gravity weapons, attack blacklist, attack whitelist, and excluded entities.
- **Quick Entry Management**:
  - Add or remove the currently held mainhand weapon with one click.
  - Add or remove the entity currently targeted by the crosshair (e.g., villagers, pets).

### 6. Real-Time Tactical HUD Monitor (U Key)
Press U in-game to toggle the floating tactical HUD:
- Tab 0: Monitors real-time muzzle velocity, gravity, estimated flight time, draw percentage, and straight-aim status.
- Tab 1: On-the-fly slider adjustments for range, speed, target parts, and tracking toggles.

### 7. Global 3-Second Confirmation for Destructive Actions
Prevents accidental data loss during intense combat or configuration adjustments:
- **Single Item Delete Buttons (Red X)**: The button expands horizontally to display a prominent red confirmation prompt. The deletion is only executed upon a second click within 3 seconds; otherwise, it reverts automatically.
- **Reset Profile & Clear Cache**: Double confirmation prompt before clearing profiles.
- **Clear List & Restore Defaults**: Protected with 3-second confirmation timers.
- **Reset Current Page**: Footer reset button turns red and requires confirmation to avoid wiping custom page settings.

---

## Keybindings

Configure under **Options -> Controls -> Key Binds -> Combat Helper**:

| Action Name | Default Key | Description |
| :--- | :---: | :--- |
| **Lock-On / Aim Assist** (`key.autoattacker.lock_on`) | R / Middle Mouse | Activate or switch target tracking |
| **Tactical Monitor Panel** (`key.autoattacker.debug_panel`) | U | Toggle the on-screen tactical HUD monitor overlay |

---

## Configuration Reference

Configuration file is located at `.minecraft/config/combathelper-client.toml` (or modified in-game via the drawer console / Configured):

```toml
[General_Settings]
    # Master mod switch
    enableMod = true
    # Auto melee swing upon full charge
    enableAutoAttack = true
    # Auto release bow when fully drawn
    enableAutoShoot = true
    # Auto reload firearms when magazine is empty
    enableGunAutoReload = false
    # Auto fire firearms when aligned with target (Triggerbot)
    enableGunTriggerbot = true
    # Firearms recoil elimination & auto pull-down
    enableGunAntiRecoil = false
    # Target search mode: HOVER_TIMED, ALWAYS_AUTO, DISABLED
    autoSearchMode = "HOVER_TIMED"
    # Hover time before auto lock-on engages (seconds)
    autoSearchHoverDelay = 0.5

[Lock_On_Settings]
    # Enable aim assist tracking
    enableAimAssist = false
    # Maximum tracking search radius (blocks)
    aimAssistRange = 64.0
    # Camera rotation smoothness (0.05 ~ 1.0)
    aimAssistSpeed = 0.25
    # Target part: HEAD, TORSO, ADAPTIVE
    targetPart = "HEAD"
    # Trajectory visualization style: PARTICLE_CHAIN (3D Dot Chain), HUD_RETICLE (2D Reticle), BOTH, OFF
    trajectoryStyle = "PARTICLE_CHAIN"
    # Enable 3D lead interception indicator
    enableLeadIndicator = true
    # Display distance tag above targets
    showDistance = true
    # Display health bar above targets
    showHealthBar = true

[List_Settings]
    # Zero-gravity straight-shooting weapon IDs
    zeroGravityBows = ["extrabotany:failnaught"]
    # Auto attack blacklist (Item ID or #tag)
    blacklist = []
    # Auto attack whitelist (Item ID or #tag)
    whitelist = []
    # Excluded entities (ignored by lock-on and auto-attack)
    entityBlacklist = ["minecraft:villager", "minecraft:armor_stand"]
```

---

## Compatibility

| Mod Name | Status | Notes |
| :--- | :---: | :--- |
| **MythicBotany (神话植物学)** | Fully Supported | Frame-accurate full attack speed DPS output & flawless energy beam/burst triggering |
| **Timeless and Classics: Zero (TACZ)** | Fully Supported | Straight-aim, Triggerbot, anti-recoil, AmmoBox integration, and packet cooldown protection |
| **Point Blank** | Fully Supported | Straight-aim, Triggerbot, and reload detection |
| **Just Enough Guns (JEG)** | Fully Supported | Custom ammo resolution (e.g. emerald blocks) and reload deadlock prevention |
| **Scguns** | Fully Supported | Firearm identification and shoot mapping adaptation |
| **Better Combat** | Fully Supported | Frame-accurate attack speed and sweep collision synchronization |
| **ExtraBotany** | Fully Supported | Native Failnaught (百中弓) zero-gravity straight-aim profile |
| **Configured** | Fully Supported | In-game configuration button launches the tactical drawer console |

---

## Installation

1. Ensure **Minecraft 1.20.1** with **Forge 47.1.0** or higher is installed;
2. (Optional) Install your preferred combat, magic, or gun mods (e.g. MythicBotany, TACZ);
3. Place `autoattacker-1.0.0.jar` into the `.minecraft/mods` folder;
4. Launch the game and enjoy tactical combat assistance.

---

## AI Development Disclaimer

This project was developed with the assistance of Artificial Intelligence (Google DeepMind Antigravity). All architectural designs, physics algorithms (4th-order Runge-Kutta numerical integration, momentum compensation), network packet handling for modern firearm mods, and the dark tactical drawer console were collaboratively formulated, implemented, and verified in Minecraft 1.20.1 Forge.

---

## License

This project is licensed under the [MIT License](LICENSE).
