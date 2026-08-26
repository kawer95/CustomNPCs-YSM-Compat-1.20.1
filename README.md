# CustomNPCs YSM Compat

Forge 1.20.1 compatibility mod that lets a CustomNPC use any locally loaded Yes Steve Model.

## Required versions

- Forge 47.4.22
- CustomNPCs GBPort Unofficial `1.20.1.20260227`
- Yes Steve Model `2.6.5-forge+mc1.20.1`

TaCZ `1.1.5` is optional. When installed, YSM-enabled CustomNPCs holding a TaCZ gun use real TaCZ shooting, aiming, bolting and reloading instead of CustomNPC projectile entities.

Dominion Sword `1.32.7+` is optional. Under Dominion control, a YSM CustomNPC keeps its configured CustomNPC ranged distance while Dominion owns target selection and gun maneuvering: it pursues until it has line of sight, fires while retreating inside 10 blocks, suppresses random strafing, honors breach/CQB mode, and does not complete a delayed melee hit against a superseded command target. Dominion's new configurable **G** hotkey toggles selected TaCZ-equipped CustomNPCs into native Crawl. Prone units accept only explicit movement orders at one fifth of normal command speed; their attack orders hold position and may fire at any visible target distance.

When Dominion Sword is installed, its existing target-reaction settings `balanceMaidTargetAcquisition` and `dynamicMaidTargetAcquisition` apply to every TaCZ-equipped Touhou Little Maid and YSM-enabled CustomNPC, whether or not the entity is currently under Dominion control. They add a 20-tick fixed or 10–40-tick angle-based delay after a kill before changing targets. `maidTaczAccuracy` remains a maid-only setting: each YSM-enabled CustomNPC instead uses its own CustomNPC **Ranged Accuracy** value as the chance that a shot receives an exact target-centre aim solution. TaCZ's own spread remains active. No duplicate CustomNPC setting is created, and uninstalling Dominion Sword leaves normal YSM-NPC gun behavior unchanged.

Both client and server must install the compatibility mod. Every client must install the same YSM model packs; model files are not sent over the network.

Open a CustomNPC's model editor and select **YSM Model**. The page supports search, live preview, apply, cancel, and restoring the original CustomNPC model. Formal YSM `config_forms` model tweaks (`checkbox`, `range`, and `radio`) can be edited per NPC and are retained with the NPC.

For the local player, the same formal `config_forms` choices made through YSM's **Z** menu are also retained automatically. They are stored per player UUID and per model in `config/customnpcs_ysm_compat_player_tweaks.json`, then restored after the YSM player model is ready. The file contains only form IDs and selected values—never raw Molang. Values that YSM normally synchronizes are sent using YSM's own packet; its intentionally local-only `v.roaming.*` values remain local.

Version 0.4.7 drives YSM's native player animator through a client-only `RemotePlayer` proxy. It synchronizes idle, physical movement and movement-facing body rotation, independent head rotation, confirmed melee hits, hurt reactions and death while preserving the NPC's scale, equipment and name tag. CustomNPCs' native **Crawl** action now also becomes a real TaCZ crawl request for YSM NPCs holding a TaCZ gun: TaCZ validates the weapon and ground conditions, applies its own prone pose/state, and YSM mirrors the same ground-prone player pose. Unsupported guns, water, jumping and passengers remain governed by TaCZ's normal rejection rules. With TaCZ present, a gun NPC first completes TaCZ ADS before firing. During a non-empty Dominion attack queue it remains in ADS across the target-switch and reaction interval, then leaves ADS once the queue ends; an explicit command cancellation always releases ADS immediately. The proxy receives TaCZ's synchronized aim, fire, bolt and reload values for YSM's native gun animations. Melee synchronization diagnostics use the `YSM-ATTACK-TRACE` log prefix.

The projectile slot and eight drop slots form the NPC's TaCZ ammunition inventory. Normal ammunition, ammo boxes and infinite ammo boxes are handled by TaCZ. Gun timing comes from the gun pack; CustomNPC ranged damage, projectile speed and burst timing do not override it. Server distance defaults are 64 blocks for sniper rifles, 48 for rifles and other weapons, and 32 for pistols, shotguns and SMGs.

## Build

The two required mod jars are resolved from `../原型模组` by `build.gradle`.

```powershell
.\gradlew.bat clean test build -Pdominionsword_jar=../DominionSword-1.20.1/build/libs/dominionsword-1.32.7.jar
```

The distributable jar is written to `build/libs/customnpcs_ysm_compat-0.4.7.jar`.
