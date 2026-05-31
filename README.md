# Mirror Building Combat — Fabric Mod for Minecraft 1.21.11

## What This Mod Does
Two (or more) players stand in a shared arena. Every block one player places or breaks is
instantly mirrored to the other side of the arena across a central axis.
First person to build their side wins — or use it for symmetry building, PvP challenges, etc.

---

## PART 1 — HOW TO BUILD THE JAR (Step-by-Step for Non-Developers)

### You will need:
1. **Java 21** (NOT Java 8 or 17 — must be 21)
   - Download: https://adoptium.net/temurin/releases/?version=21
   - During install, tick "Set JAVA_HOME" if offered
2. **The mod source folder** (`mirrorbuild/`) — the folder containing this README

### Windows — Building the JAR
1. Open **File Explorer**, go into the `mirrorbuild` folder
2. In the address bar type `cmd` and press Enter (opens Command Prompt here)
3. Run this command:
   ```
   gradlew.bat build
   ```
4. Wait — it will download Gradle, Fabric, Minecraft mappings (~200 MB first time)
5. When you see `BUILD SUCCESSFUL`, your JAR is at:
   ```
   build\libs\mirrorbuild-1.0.0.jar
   ```

### Mac / Linux — Building the JAR
1. Open **Terminal**, `cd` into the mirrorbuild folder:
   ```bash
   cd /path/to/mirrorbuild
   chmod +x gradlew
   ./gradlew build
   ```
2. JAR will be at: `build/libs/mirrorbuild-1.0.0.jar`

### Common Build Errors
| Error message | Fix |
|---|---|
| `JAVA_HOME is not set` | Install Java 21 and restart your terminal |
| `error: release version 21 not supported` | You have an old Java — install Java 21 |
| `Could not resolve minecraft:1.21.11` | Internet connection issue; retry |
| `BUILD FAILED` with `yarn` error | Yarn mappings for 1.21.11 may not exist yet — see Version Note below |

### ⚠️ VERSION NOTE
Minecraft **1.21.11** is from the Tricky Trials update series. If Yarn mappings are not yet
published for this exact build number, change this line in `build.gradle`:
```groovy
mappings "net.fabricmc:yarn:1.21.11+build.1:v2"
```
To check available build numbers, visit:
https://maven.fabricmc.net/net/fabricmc/yarn/

If 1.21.11 mappings are truly unavailable, use `1.21.1+build.3` mappings — the mappings are
compatible for minor patch versions within the same major release. The mod code uses no
version-specific internal APIs that would break.

---

## PART 2 — INSTALLATION

### Server Installation
1. Install **Fabric Server** for Minecraft 1.21.11:
   - Download installer from https://fabricmc.net/use/server/
   - Run: `java -jar fabric-installer.jar server -mcversion 1.21.11 -downloadMinecraft`
2. Download **Fabric API 0.102.0+1.21.11** from https://modrinth.com/mod/fabric-api
3. Put both JARs in your server's `mods/` folder:
   - `fabric-api-0.102.0+1.21.11.jar`
   - `mirrorbuild-1.0.0.jar`
4. Start the server normally

### Singleplayer / Client Installation
1. Install Fabric Loader for 1.21.11 via https://fabricmc.net/use/installer/
2. Download Fabric API and put it in `.minecraft/mods/`
3. Put `mirrorbuild-1.0.0.jar` in `.minecraft/mods/`
4. Launch via the Fabric profile in the Minecraft launcher

---

## PART 3 — IN-GAME SETUP GUIDE

You must be an **operator** (OP level 2+) to use /mirror commands.
On a local server: run `op YourName` in the server console.

### Step-by-Step Arena Setup

```
1.  /mirror wand
    → You receive a Wooden Axe (the selection wand)

2.  Left-click any block for the FIRST corner of your arena
    Right-click any block for the SECOND corner
    (Or use: /mirror pos1 X Y Z  and  /mirror pos2 X Y Z)

3.  /mirror setarea
    → Confirms the arena. Must be at least 3×3 blocks on X and Z.

4.  /mirror axis z
    → Mirror East-West (recommended for most arenas — players face each other)
    
    /mirror axis x
    → Mirror North-South instead

5.  /mirror enable
    → Mirroring is now ACTIVE!

6.  /mirror status
    → Confirms everything is set up correctly

7.  Place or break blocks — they mirror instantly to the other side!
```

### All Commands
| Command | What it does |
|---|---|
| `/mirror help` | Full in-game instructions |
| `/mirror wand` | Gives you the selection wand (Wooden Axe) |
| `/mirror pos1 X Y Z` | Manually set first corner |
| `/mirror pos2 X Y Z` | Manually set second corner |
| `/mirror setarea` | Confirm the arena from your selection |
| `/mirror cleararea` | Reset everything |
| `/mirror showbounds` | Show arena outline with particles |
| `/mirror axis x` or `/mirror axis z` | Set mirror axis |
| `/mirror enable` | Turn mirroring on |
| `/mirror disable` | Turn mirroring off (pause) |
| `/mirror status` | Show current configuration |
| `/mirror reload` | Safe reset — clears all state without crashing |

---

## PART 4 — TROUBLESHOOTING GUIDE

### 🔴 Mod crashes on startup

**Check 1: Wrong Minecraft version**
- Open `logs/latest.log`
- Search for `MirrorBuild` or `mirrorbuild`
- If you see `incompatible minecraft version`, you have the wrong Minecraft profile

**Check 2: Missing Fabric API**
- Error: `net.fabricmc.fabric.api.XXX not found`
- Fix: Download Fabric API 0.102.0+ from modrinth.com and put in `mods/`

**Check 3: Java version**
- Run `java -version` in terminal
- Must say `version "21.x.x"` — anything else will fail

**Check 4: Corrupt JAR**
- Re-run `gradlew build` and use the fresh JAR

**Check 5: Conflicting mods**
- Temporarily remove ALL other mods — test with only Fabric API + MirrorBuild
- Add mods back one by one to find the conflict

### 🔴 Mirroring doesn't work

1. Run `/mirror status` — check that it says `ENABLED` and shows an arena
2. Make sure you ran `/mirror setarea` (not just setting pos1/pos2)
3. Make sure you ran `/mirror enable`
4. Check that you're building INSIDE the arena boundaries
5. Check axis: if your arena is East-West, use `axis=Z`; North-South use `axis=X`
6. Check server log for `[MirrorBuild]` error lines

### 🔴 Wand doesn't select blocks

1. **Are you OP?** Run `op YourName` in the server console — you need OP level 2+
2. **Are you holding the correct item?** Must be the Wooden Axe from `/mirror wand`
3. Try manual coordinates instead:
   ```
   /mirror pos1 0 64 0
   /mirror pos2 20 80 20
   /mirror setarea
   ```
4. Check you're left-clicking (attack) for pos1, right-clicking (use) for pos2

### 🔴 "Selection is too small" error

Your selected area must be **at least 3 blocks wide on BOTH X and Z axes**.
Example of a valid arena: pos1 = `0 64 0`, pos2 = `20 80 20` (21×17×21)

### 🔴 Mirror places blocks in wrong location

- Check your axis setting with `/mirror status`
- `axis=Z` flips the X coordinate (East-West)
- `axis=X` flips the Z coordinate (North-South)
- Your arena should be centred — the mirror axis passes through the middle

### 🔴 Server lag when mirroring

- Reduce arena size (smaller = faster)
- The mod is synchronous on the server thread — it should be near-instant for normal arenas
- Very large arenas (100+ blocks) are fine; the mirror is a single block operation

### 🔴 Finding crash reports

**Crash reports location:**
- Server: `crash-reports/` folder in your server directory
- Singleplayer: `.minecraft/crash-reports/`

**What to look for:**
- Search the crash report for `mirrorbuild` or `MirrorBuild`
- Look for lines starting with `java.lang.` followed by the error type
- Common: `NullPointerException` at a MirrorBuild class = report as a bug
- Common: `ClassNotFoundException` for Fabric classes = missing Fabric API

**How to share a crash report:**
- Open the `.txt` file in Notepad
- Copy everything
- Paste to https://mclo.gs/ and share the link

### 🔴 "Permission denied" running commands

You need to be server operator. In the server console (not in-game chat):
```
op YourUsername
```
Then the `/mirror` commands will work.

---

## PART 5 — HOW IT WORKS (Technical Summary)

- **Block break mirroring**: Fabric's `PlayerBlockBreakEvents.BEFORE` event detects breaks.
  The mirrored break is scheduled on the server thread immediately after.
- **Block place mirroring**: A Mixin on `BlockItem.place()` detects successful placements
  and mirrors the block state to the opposite position.
- **Wand selection**: `AttackBlockCallback` (left-click) and `UseBlockCallback` (right-click)
  are intercepted when holding a Wooden Axe.
- **Re-entrancy guard**: A `ThreadLocal<Boolean>` flag prevents mirror events from
  triggering another mirror (infinite loop prevention).
- **Null safety**: Every single operation checks for null world, null player, null BlockPos,
  null BlockState before acting.
- **No crashes guaranteed**: All event handlers are wrapped in try-catch. Errors are logged
  to the server console and mirroring is silently skipped for that action.

---

## PART 6 — GAMEPLAY TIPS

1. **Arena design**: Make the arena symmetric to start. Each player gets one half.
2. **Best axis**: If players face each other (one on North side, one on South), use `axis=X`.
   If players face each other (one on East, one on West), use `axis=Z`.
3. **Arena size**: 20×10×10 per side (40 wide total) is a good starting size.
4. **Spectators**: Non-OPs can build inside the arena — all players' blocks get mirrored.
5. **TNT/fire**: These work normally and are NOT mirrored (only direct block place/break).
   This is intentional — TNT on one side only destroys that side.

---

## License
MIT — free to use, modify, and redistribute.
