# Full Draw

[![Build](https://github.com/Panda-s-Workshop/full-draw/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/full-draw/actions/workflows/build.yml)

**Website:** https://panda-s-workshop.github.io/full-draw/ (every weapon, arrow, creature and number)

**Download:** not released yet (CurseForge link here after the first release). Modpacks: please use the CurseForge
file, not the GitHub release.

Archery where the shot is yours: time the release, aim for the head, pick the right bow and the right arrow.
A Minecraft 26.3 mod for NeoForge and Fabric, Java 25, part of Panda's Workshop.

- **NeoForge:** 26.3.0.89 or later. Nothing else is needed.
- **Fabric:** Fabric Loader 0.19.5 or later and Fabric API 0.161.0+26.3 or later. Nothing else is needed.

Install it on the client, and on the server too when you play on one.

## Playing

- **The shot.** Every bow has a Perfect window just after full draw: let go while the ring around the crosshair glows
  gold and the arrow flies straight and crits. Hold too long and the aim trembles; sneak to hold your breath. Heads and
  weak spots take more, long shots gain a little, point-blank shots lose some.
- **Weapons.** The Shortbow, Longbow and Recurve Bow; the Arbalest and the Repeating Crossbow.
- **Arrows.** Broadhead, Bodkin, Blunt, Rope, Flare and Blast arrows, made at the Fletching Table, which now works.
  A Quiver holds four kinds; press R to pick which one your bow draws.
- **Creatures.** The Shieldbearer, the Gale Harrier and the Bog Lurker, and the Marksman on top of the Ranger's
  Watchtower.
- **Enchantments.** Deadeye, Steady, Quickdraw and Ricochet.

`/fulldraw stats` shows the bow in your hand and the whole sum of your last hit.

## For pack makers

The server config (NeoForge `config/fulldraw-synced.toml`, Fabric `config/fulldraw.properties`) holds the shot's
tuning, the creatures' spawns and numbers, and the Marksman's. The shot rules apply to every bow a player draws,
other mods' included; `#fulldraw:no_shot_pipeline` leaves a weapon alone. Weak spots and fletching recipes are data.
The website's [Modpacks and config](https://panda-s-workshop.github.io/full-draw/packs.html) page lists every key,
tag and data format.

## Build and run

JDK 25 is pinned in `gradle.properties` (`org.gradle.java.home`). From this folder:

```bash
./gradlew build
```

```bash
./gradlew :neoforge:runGameTestServer
```

- `build` writes `neoforge/build/libs/fulldraw-<version>-neoforge.jar` and `fabric/build/libs/fulldraw-<version>-fabric.jar`.
- Dev dedicated servers listen on port 25613.

## How it is tested

- **JUnit** (`common/src/test`): the shot's arithmetic, the balance targets against vanilla bows and melee, the
  creatures' rules, the breath, the enchantments, and that every item has a name, a model and a way to get it.
- **GameTests** (`neoforge/src/main/java/com/panda/fulldraw/gametest`): the rules in a running server, such as real
  shots, arrows and bolts on real mobs, the Fletching Table's recipes, the Quiver, the creatures and the watchtower.
- CI runs both on every push, and a release is built only from a commit where they pass.

## License

All Rights Reserved.
