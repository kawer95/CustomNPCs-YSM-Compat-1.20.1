# CustomNPCs YSM Compat

Forge 1.20.1 compatibility mod that lets a CustomNPC use any locally loaded Yes Steve Model.

## Required versions

- Forge 47.4.22
- CustomNPCs GBPort Unofficial `1.20.1.20260227`
- Yes Steve Model `2.6.5-forge+mc1.20.1`

Both client and server must install the compatibility mod. Every client must install the same YSM model packs; model files are not sent over the network.

Open a CustomNPC's model editor and select **YSM Model**. The page supports search, live preview, apply, cancel, and restoring the original CustomNPC model.

Version 0.2.1 drives YSM's native player animator through a client-only `RemotePlayer` proxy. It synchronizes idle, physical movement, independent head rotation, real swing progress, hurt reactions and death while preserving the NPC's body rotation, scale, equipment and name tag. State priority is death, hurt, attack, walking, then idle. The proxy name tag is suppressed and the model-screen preview uses an isolated idle clock.

## Build

The two required mod jars are resolved from `../原型模组` by `build.gradle`.

```powershell
.\gradlew.bat clean test build
```

The distributable jar is written to `build/libs/customnpcs_ysm_compat-0.2.1.jar`.
