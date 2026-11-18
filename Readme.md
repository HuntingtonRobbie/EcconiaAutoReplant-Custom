# Ecconia's Auto Replant (custom fork)

This is a personal fork of **[Ecconia's Auto Replant](https://github.com/Ecconia/EcconiaAutoReplant)** by **Ecconia**. All credit for the original mod goes to Ecconia.

The fork is licensed under the **GNU GPL v3.0**, the same as the original (see `LICENSE`). Every change is listed below and in the commit history. It was made for personal use and is not an official release.

## Branches
- `26.3`: Minecraft 26.3 (Mojang names, no remapping).
- `1.21.11`: Minecraft 1.21.11.

Both are built on upstream `master` (Minecraft 1.21.8).

## What this fork changes
- **Faster held right-click:** a crop is harvested and replanted every tick while right-click is held, instead of waiting for vanilla's use delay.
- **Every vanilla crop:** adds cocoa (replanted on the same jungle log face), torchflowers and pitcher plants to the existing crops, each with its own toggle.
- **Harvest-only crops:** sugar cane (the base block stays), and pumpkins and melons that have an attached stem.
- **Settings:** stored in `config/ecconia-auto-replant.json`, with a Mod Menu + Cloth Config screen (both optional). Options: an on/off toggle key, the held-click delay, switching back to the held item, harvesting only when seeds are available, action bar messages, and protecting other blocks while right-click is held.
- `26.3` only: ported to Minecraft 26.3.

---

*The original README follows.*
# Ecconia's Auto Replant

## For?

Minecraft Fabric 1.20.1\
Also requires the Fabric-API.

## What does it do?

On interacting with a crop plant that is fully mature, the plant will be destroyed and if a seed for that plant is available in the hotbar, it will be selected and planted.

Also supports nether warts.

## Why another one?

Because apparently all the others I found (even though they have more features and are better), need a server mod.

This mod runs on the client fully. It does not need a server mod.

## Warning, hacking?

Currently, this mod will interact with the farm block, without looking at it.
A vanilla server and Spigot do not seem to mind this.\
But if the server has an anti-cheat protection, this mod will not try to hide the automation.

**You have been warned, this mod can get you banned for faked packet sending!**
