# Full Draw

Archery where the shot is yours: time the release, aim for the head, pick the right bow and the right arrow.
A Minecraft 26.3 mod for NeoForge and Fabric, part of Panda's Workshop.

**Download:** CurseForge (link once the project exists). Modpacks: please use the CurseForge file, not the GitHub
release.

Status: in development.

## Build and run

JDK 25 is pinned in `gradle.properties` (`org.gradle.java.home`). From this folder:

```bash
./gradlew build
```

```bash
./gradlew :neoforge:runGameTestServer
```

- `build` writes `neoforge/build/libs/fulldraw-<version>-neoforge.jar` and `fabric/build/libs/fulldraw-<version>-fabric.jar`.
- JUnit tests are in `common/src/test`; GameTests in `neoforge/src/main/java/com/panda/fulldraw/gametest`.
- Dev dedicated servers listen on port 25613.

## License

All Rights Reserved.
