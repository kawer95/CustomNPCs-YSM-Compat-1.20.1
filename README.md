# CustomNPCs YSM Compat

Forge 1.20.1 compatibility mod that lets a CustomNPC use any locally loaded Yes Steve Model.

## Required versions

- Forge 47.4.22
- CustomNPCs GBPort Unofficial `1.20.1.20260227`
- Yes Steve Model `2.6.5-forge+mc1.20.1`

TaCZ `1.1.5` is optional. When installed, YSM-enabled CustomNPCs holding a TaCZ gun use real TaCZ shooting, aiming, bolting and reloading instead of CustomNPC projectile entities.

Both client and server must install the compatibility mod. Every client must install the same YSM model packs; model files are not sent over the network.

Open a CustomNPC's model editor and select **YSM Model**. The page supports search, live preview, apply, cancel, and restoring the original CustomNPC model.

Version 0.3.3 drives YSM's native player animator through a client-only `RemotePlayer` proxy. It synchronizes idle, physical movement and movement-facing body rotation, independent head rotation, confirmed melee hits, hurt reactions and death while preserving the NPC's scale, equipment and name tag. With TaCZ present, the real NPC owns the gun state while the proxy receives TaCZ's synchronized aim, fire, bolt and reload values for YSM's native gun animations.

The projectile slot and eight drop slots form the NPC's TaCZ ammunition inventory. Normal ammunition, ammo boxes and infinite ammo boxes are handled by TaCZ. Gun timing comes from the gun pack; CustomNPC ranged damage, projectile speed and burst timing do not override it. Server distance defaults are 64 blocks for sniper rifles, 48 for rifles and other weapons, and 32 for pistols, shotguns and SMGs.

## Build

The two required mod jars are resolved from `../原型模组` by `build.gradle`.

```powershell
.\gradlew.bat clean test build
```

The distributable jar is written to `build/libs/customnpcs_ysm_compat-0.3.3.jar`.
