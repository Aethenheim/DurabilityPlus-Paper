# DurabilityPlus-Paper
Hard fork of DurabilityPlus by MyceliumMind, aimed at supporting folia and using paper api.
This hard fork will never support Spigot, as it relies on paper-api exclusive 
code. Spigot is a stagnant dying ecosystem and quite frankly people should move on to the paper api family when it comes to plugins.
My opinions and whatever does not reflect that of the original author.

Original description for DurabilityPlus below by MyceliumMind. It will be replaced with a new one later that reflects what this hard fork
is to add/change.

# DurabilityPlus

DurabilityPlus is the **one-stop durability management plugin** for Spigot/Paper servers. 
It extends and rebalances Minecraft’s durability system with fine-grained control, realistic mechanics, and player-friendly tools.

---

## Features

### Core Multipliers
- Global durability multiplier (`globalMultiplier`) 
- Per-item overrides with wildcards (`DIAMOND_*`, `IRON_SWORD`, etc.) 
- Per-world multipliers 
- Donor/permission-based bonuses 
- Elytra-specific nerfs (`multiplier`, `repairable` toggle)

### Degradation System (v1.1)
- Weapon damage scales down with durability (configurable curves) 
- Armor protection weakens as durability drops 
- Mining speed slowdown:
  - **Effect mode**: applies Mining Fatigue
  - **Delay mode**: server-enforced break speed (with optional ProtocolLib crack sync) 

### Protection & Repair
- **Auto-protect**: prevents final break, item is marked `BROKEN` instead 
- **Repair commands**:
  - `/dp repair` → repair held item 
  - `/dp repairall` → repair all items 
- **Mending rebalance**: configurable efficiency factor 
- Prevent/allow Elytra repair via config 

### Wrong Tool Handling (v1.2)
- Extra durability wear for breaking blocks with wrong tool 
- Extra wear for combat with wrong weapon 
- Separate multipliers for blocks vs combat 
- Config option to treat axes as weapons or not 

### Weather Wear (v1.2)
- Configurable periodic wear in rain/thunder 
- Supports tools and armor separately 
- Exempt tiers (e.g. Diamond/Netherite) 
### Salvage System (v1.2.1)
- Broken items can drop back ingredients or custom loot pools 
- Define salvage pools per-item, per-tier, or by matcher (name/lore/customModelData) 
- Configurable drop count range 
- Works with both natural breaks and auto-protect path 

### Notifications
- **Low-durability ping**: configurable sound, threshold %, cooldown 
- `/dp ping <on|off>` toggle per-player (saved via PDC) 
- **Actionbar/Chat notifications** for broken items 

### Performance
- Caching for multipliers 
- Skip redundant lore updates 
- All systems toggleable in `config.yml` 

---

## 🛠 Commands

| Command | Description |
|---------|-------------|
| `/dp add <amount>` | Add durability to held item |
| `/dp take <amount>` | Subtract durability |
| `/dp set <value>` | Set remaining durability |
| `/dp repair` | Repair held item |
| `/dp repairall` | Repair all items in inventory/armor |
| `/dp reload` | Reload plugin config |
| `/dp unbreakable <true|false>` | Toggle unbreakable flag |
| `/dp ping <on|off>` | Toggle low-durability ping |

*(Admin-only features like salvage, weather wear, degradation, wrong tool multipliers are config-driven.)*

---

## 🔧 Config Highlights
- `globalMultiplier`, `perItemMultipliers`, `perWorldMultipliers` 
- `degradation.weaponDamage.curve`, `armorProtection.curve`, `miningSpeed.mode` 
- `autoProtect.enabled`, `autoProtect.notifyMode` 
- `mending.factor` 
- `elytra.multiplier`, `elytra.repairable` 
- `wrongTool.blocks/combat.enabled`, `wrongTool.blocks/combat.multiplier` 
- `weatherWear.enabled`, `materials`, `exemptTiers` 
- `salvage.enabled`, `salvage.perItem`, `salvage.perTier` 
- `lowDurabilityPing.sound`, `thresholdPercent`, `cooldownSeconds` 

---

## 📦 Requirements
- Spigot 1.20+ / Paper 1.20+ 
- (Optional) ProtocolLib for crack animation sync

