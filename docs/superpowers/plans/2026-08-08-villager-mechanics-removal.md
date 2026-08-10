# Villager Mechanics Reduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Reduce villagers to persistent merchants by removing autonomous village simulation, automatic gossip, villager raid response, nightly village sieges, and village-only iron-golem defense while preserving trading and public Bukkit/Paper APIs.

**Architecture:** Keep `AbstractVillager`, `Merchant`, profession/type registries, offers, demand, restocking, XP, and direct player trading intact. Replace the villager Brain's schedule-driven POI/work/social matrix with a small `CORE`/`IDLE`/`PANIC` graph, move restocking to a server-side villager tick path, and remove only the active village/reputation/defense call sites. Preserve registry and serialized identifiers when removing them would make old worlds or datapacks fail to decode.

**Tech Stack:** Java 25, Minecraft/Paper NMS sources, CraftBukkit/Paper Java sources, Gradle 9.4.1, JUnit 5, Mockito, existing Paper patch regeneration workflow.

## Global Constraints

- Keep villagers, zombie villagers, villager types, professions, trade sets, offers, demand, XP, leveling, direct trading, and merchant events.
- Preserve the public `org.bukkit.entity.Villager`, `org.bukkit.entity.AbstractVillager`, `CraftVillager`, and merchant API surfaces; do not remove reputation methods, restock methods, sleep methods, or zombification methods.
- Remove automatic job-site acquisition, workstation work, farming, item pickup/distribution, autonomous breeding, bed seeking, bell socialization, villager-to-villager trade, Hero of the Village gifts, automatic gossip mutation/transfer/decay, villager raid activities, nightly village sieges, and village-only iron-golem movement/defense.
- Keep ordinary panic/flee behavior, ordinary iron-golem combat/anger behavior, zombie infection/cure, global raids/Bad Omen, the shared POI subsystem, and wandering-trader behavior.
- Automatic reputation sources disappear; explicitly assigned reputation remains readable and continues through the existing price-adjustment/API path.
- Automatic restocking must continue without a workstation or job-site memory and must preserve `shouldRestock(ServerLevel)`, `restock()`, demand catch-up, and inactive-villager behavior.
- Do not remove historical datafixers or registry keys merely to reduce source count; preserve decode compatibility for old brains, NBT, and datapacks.
- New Alkahest code is not needed. Do not create `dev.mintychochip` patches or move vanilla logic into that namespace.
- Changes under `paper-server/src/minecraft/java/net/minecraft/...` require the Paper source-patch workflow and regenerated patch files.
- Preserve unrelated existing worktree changes in `.gitignore`, `README.md`, `build.gradle.kts`, `test-plugin/build.gradle.kts`, `bench/`, `serve/`, and heap dumps; never stage them.
- Each implementation task ends with a green compile/test check and an atomic commit containing only that task's behavior and its tests/patch updates.

## Scope decomposition

The approved specification spans three coupled but independently verifiable units:

1. merchant-safe Brain and workstation-independent restocking;
2. removal of POI/work/social/breeding and automatic reputation behavior; and
3. removal of village sieges and village-only iron-golem defense.

The tasks below keep those units in separate commits. The approved design remains the single behavioral contract at `docs/superpowers/specs/2026-08-08-villager-mechanics-removal-design.md`.

---

### Task 1: Make the villager Brain merchant-safe and preserve restocking

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java:130-220,238-313,305-313,383-457`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java:32-67,182-235,237-288`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java:37-46`
- Create: `paper-server/src/test/java/io/papermc/paper/entity/VillagerGoalPackagesTest.java`

**Interfaces:**
- Consumes: existing villager trade methods (`getOffers`, `shouldRestock`, `restock`), `Brain`, and generic panic/trading behaviors.
- Produces: `VillagerGoalPackages.getCorePackage(float)`, `getIdlePackage(float)`, and `getPanicPackage(float)` with no POI/work/raid controls; a villager-owned restock check that does not require a job site.

- [ ] **Step 1: Write the failing Brain-topology test**

Create `VillagerGoalPackagesTest` with the normal standalone test environment. Test behavior topology through the returned controls, not source text:

```java
package io.papermc.paper.entity;

import com.mojang.datafixers.util.Pair;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Normal
class VillagerGoalPackagesTest {
    private static Set<String> behaviorNames(final Iterable<? extends Pair<?, ?>> controls) {
        Set<String> names = new HashSet<>();
        for (Pair<?, ?> control : controls) {
            names.add(control.getSecond().getClass().getSimpleName());
        }
        return names;
    }

    @Test
    void corePackageContainsMerchantAndSafetyBehaviorsOnly() {
        Set<String> names = behaviorNames(VillagerGoalPackages.getCorePackage(0.5F));
assertTrue(names.contains("LookAndFollowTradingPlayerSink"));
assertTrue(names.contains("VillagerPanicTrigger"));
assertFalse(names.contains("AcquirePoi"));
assertFalse(names.contains("ValidateNearbyPoi"));
assertFalse(names.contains("PoiCompetitorScan"));
assertFalse(names.contains("GoToPotentialJobSite"));
assertFalse(names.contains("YieldJobSite"));
assertFalse(names.contains("AssignProfessionFromJobSite"));
assertFalse(names.contains("ResetProfession"));
assertFalse(names.contains("ReactToBell"));
assertFalse(names.contains("SetRaidStatus"));
assertFalse(names.contains("WakeUp"));
assertFalse(names.contains("GoToWantedItem"));
    }

    @Test
    void idlePackageContainsPlayerTradePresentationOnly() {
        Set<String> names = behaviorNames(VillagerGoalPackages.getIdlePackage(0.5F));
assertTrue(names.contains("ShowTradesToPlayer"));
assertTrue(names.contains("SetLookAndInteract"));
assertFalse(names.contains("TradeWithVillager"));
assertFalse(names.contains("VillagerMakeLove"));
assertFalse(names.contains("GiveGiftToHero"));
assertFalse(names.contains("JumpOnBed"));
assertFalse(names.contains("InteractWith"));
assertFalse(names.contains("SocializeAtBell"));
    }

    @Test
    void panicPackageRemainsAvailableForOrdinaryDanger() {
        Set<String> names = behaviorNames(VillagerGoalPackages.getPanicPackage(0.5F));
assertTrue(names.contains("VillagerCalmDown"));
assertTrue(names.contains("SetWalkTargetAwayFrom"));
    }
}
```

The exact assertions intentionally cover the approved contract: merchant presentation and ordinary panic remain, while POI/work/social/raid controls cannot return to the active packages.

- [ ] **Step 2: Run the focused test and verify the current implementation fails**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: the suite fails in `VillagerGoalPackagesTest` because the current packages still contain POI, work, social, and raid behaviors. Do not commit the failing state.

- [ ] **Step 3: Replace the Brain provider's activity matrix with the retained activities**

In `Villager.java`, replace the current `BRAIN_PROVIDER` sensor list and activity builder with the merchant-safe shape below. Keep `NEAREST_LIVING_ENTITIES`, `NEAREST_PLAYERS`, `HURT_BY`, and `VILLAGER_HOSTILES` for trading/player look behavior and ordinary panic. Do not register `NEAREST_ITEMS`, `NEAREST_BED`, `VILLAGER_BABIES`, `SECONDARY_POIS`, or `GOLEM_DETECTED` in the villager Brain.

```java
private static final Brain.Provider<Villager> BRAIN_PROVIDER = Brain.provider(
    List.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.HURT_BY,
        SensorType.VILLAGER_HOSTILES
    ),
    body -> List.of(
        ActivityData.create(Activity.CORE, VillagerGoalPackages.getCorePackage(0.5F)),
        ActivityData.create(Activity.IDLE, VillagerGoalPackages.getIdlePackage(0.5F)),
        ActivityData.create(Activity.PANIC, VillagerGoalPackages.getPanicPackage(0.5F))
    )
);
```

Keep `refreshBrain(ServerLevel)` and Brain packing, but make `registerBrainGoals` choose `IDLE` as the default schedule activity for both adults and babies:

```java
private void registerBrainGoals(final Brain<Villager> brain) {
    brain.setSchedule(EnvironmentAttributes.VILLAGER_ACTIVITY);
    brain.setDefaultActivity(Activity.IDLE);
    brain.updateActivityFromSchedule(this.level().environmentAttributes(), this.level().getGameTime(), this.position());
}
```

Do not remove `Activity` registry entries. They are shared registry data; only the villager's active graph changes.

- [ ] **Step 4: Reduce `VillagerGoalPackages` to the retained controls**

Change `getCorePackage` to accept only `speedModifier` and return swimming, door interaction, look target, panic, movement target, trade-player following, and any other control required by the retained merchant-safe graph. It must not construct `AcquirePoi`, `ValidateNearbyPoi`, `PoiCompetitorScan`, `GoToPotentialJobSite`, `YieldJobSite`, `WakeUp`, `ReactToBell`, `SetRaidStatus`, `GoToWantedItem`, or any profession-dependent control.

Keep `getIdlePackage(float)` limited to basic random stroll/look/do-nothing controls, `SetLookAndInteract` for players, and `ShowTradesToPlayer`. Keep `getPanicPackage(float)` as the existing ordinary flee behavior. Remove all work, meet, rest, pre-raid, raid, hide, and play package calls from this task's Brain graph; delete their package methods in Task 2 after the final call-site audit.

The retained idle shape is:

```java
public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> getIdlePackage(
    final float speedModifier
) {
    return ImmutableList.of(
            Pair.of(2, RandomStroll.stroll(speedModifier)),
        Pair.of(3, SetLookAndInteract.create(EntityTypes.PLAYER, 4)),
        Pair.of(3, new ShowTradesToPlayer(400, 1600)),
        getFullLookBehavior(),
        Pair.of(99, UpdateActivityFromSchedule.create())
    );
}
```

If a retained control requires a sensor removed by Step 3, remove that control rather than restoring a village sensor. Keep `LookAndFollowTradingPlayerSink` in the core package so a villager continues to track the player while its merchant screen is open.

- [ ] **Step 5: Move automatic restock checks out of `WorkAtPoi`**

Add a server-side check counter to `Villager` and run it from `tick()` so restocking does not depend on AI activation, a profession, or a job site:

```java
private static final int RESTOCK_CHECK_INTERVAL = 300;
private int restockCheckTicks;

private void tickRestock(final ServerLevel level) {
    if (++this.restockCheckTicks < RESTOCK_CHECK_INTERVAL) {
        return;
    }

    this.restockCheckTicks = 0;
    if (this.shouldRestock(level)) {
        this.restock();
    }
}

private void tickRestockIfServer() {
    if (this.level() instanceof ServerLevel serverLevel) {
        this.tickRestock(serverLevel);
    }
}

@Override
public void inactiveTick() {
    if (this.getUnhappyCounter() > 0) {
        this.setUnhappyCounter(this.getUnhappyCounter() - 1);
    }
    if (this.aware && this.isEffectiveAi()) {
        if (this.level().spigotConfig.tickInactiveVillagers) {
            this.customServerAiStep(this.level().getMinecraftWorld());
        } else {
            this.customServerAiStep(this.level().getMinecraftWorld(), true);
        }
    }
    this.tickRestockIfServer();
    this.maybeDecayGossip(); // remove in Task 3
    super.inactiveTick();
}

@Override
public void tick() {
    super.tick();
    if (this.getUnhappyCounter() > 0) {
        this.setUnhappyCounter(this.getUnhappyCounter() - 1);
    }
    this.tickRestockIfServer();
    this.maybeDecayGossip(); // remove in Task 3
}

Invoke `tickRestockIfServer()` from both `tick()` and Paper's `inactiveTick()`; inactive villagers must retain restocking even when `tickInactiveVillagers` is false. During Task 3, remove the two `maybeDecayGossip()` calls. The final active and inactive tick paths must retain only the restock check and existing non-gossip entity behavior. Remove the `shouldRestock`/`restock` call from `WorkAtPoi`; if `WorkAtPoi` has no remaining call sites after Task 2, delete the class then. Do not alter `shouldRestock`, `restock`, `updateDemand`, `catchUpDemand`, or the Bukkit `restock()` bridge.

- [ ] **Step 6: Compile and run the topology test**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: compilation succeeds and `VillagerGoalPackagesTest` passes. The test must show no POI/work/raid controls in the active core/idle packages and preserve the panic package.

- [ ] **Step 7: Regenerate vanilla patches**

If the Minecraft source tree was not already applied, run once before patch regeneration:

```bash
./gradlew applyPatches
```

Then regenerate the source patches for the changed vanilla files:

```bash
./gradlew fixupSourcePatches rebuildPatches
```

Expected: the generated patch set contains only the intended `Villager.java`, `VillagerGoalPackages.java`, and `WorkAtPoi.java` changes; no `dev/mintychochip` patch path is created.

- [ ] **Step 8: Commit the merchant-safe Brain and restock unit**

```bash
git add paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java \
  paper-server/patches/sources/net/minecraft/world/entity/npc/villager/Villager.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java.patch \
  paper-server/src/test/java/io/papermc/paper/entity/VillagerGoalPackagesTest.java
git commit -m "feat: simplify villager brain for merchant behavior"
```

Do not stage unrelated worktree files or generated runtime artifacts.

---

### Task 2: Remove villager POI, work, social, and autonomous breeding behavior

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java:97-191,234-313,689-852`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java`
- Delete after call-site confirmation: `AcquirePoi.java`, `AssignProfessionFromJobSite.java`, `ResetProfession.java`, `YieldJobSite.java`, `PoiCompetitorScan.java`, `GoToPotentialJobSite.java`, `WorkAtPoi.java`, `WorkAtComposter.java`, `HarvestFarmland.java`, `UseBonemeal.java`, `VillagerMakeLove.java`, `TradeWithVillager.java`, `GiveGiftToHero.java`, `SocializeAtBell.java`, `ReactToBell.java`, `RingBell.java`, `JumpOnBed.java`
- Retain shared behavior files if another non-villager caller exists: `InteractWithDoor.java`, `VillageBoundRandomStroll.java`, `SetLookAndInteract.java`, `ShowTradesToPlayer.java`, `VillagerPanicTrigger.java`, and `VillagerHostilesSensor.java`
- Retain compatibility-only sensors and registry declarations: `VillagerBabiesSensor.java`, `SecondaryPoiSensor.java`, `SensorType.VILLAGER_BABIES`, and `SensorType.SECONDARY_POIS`; remove only their active villager Brain registrations.
- Modify: `paper-server/src/test/java/io/papermc/paper/entity/VillagerGoalPackagesTest.java`

**Interfaces:**
- Consumes: the minimal Brain graph and restock scheduler from Task 1.
- Produces: no automatic profession assignment, workstation use, farming, item pickup, social exchange, bed seeking, or villager breeding; explicit profession, inventory, sleep, and breeding API surfaces remain.

- [ ] **Step 1: Extend the failing topology tests for village simulation removal**

Add these assertions to `VillagerGoalPackagesTest` before changing the remaining package methods:

```java
@Test
void noRemovedActivityPackageRemainsReachableFromTheVillagerGraph() {
    Set<String> core = behaviorNames(VillagerGoalPackages.getCorePackage(0.5F));
    Set<String> idle = behaviorNames(VillagerGoalPackages.getIdlePackage(0.5F));
    Set<String> panic = behaviorNames(VillagerGoalPackages.getPanicPackage(0.5F));

Set<String> active = Stream.of(core, idle, panic).flatMap(Set::stream).collect(Collectors.toSet());
for (String removed : Set.of(
    "AcquirePoi", "AssignProfessionFromJobSite", "ResetProfession", "YieldJobSite",
    "PoiCompetitorScan", "GoToPotentialJobSite", "WorkAtPoi", "WorkAtComposter",
    "HarvestFarmland", "UseBonemeal", "VillagerMakeLove", "TradeWithVillager",
    "GiveGiftToHero", "SocializeAtBell", "ReactToBell", "RingBell", "JumpOnBed"
)) {
    assertFalse(active.contains(removed), removed);
}
}
```

Add the required `java.util.stream.Collectors` and `java.util.stream.Stream` imports. Run the normal suite and confirm the old activity graph fails the new assertions before implementation. Do not commit the red test state.

- [ ] **Step 2: Remove all work, POI, rest, meet, raid, hide, and play packages from `VillagerGoalPackages`**

Delete the package methods and helpers no longer reachable after Task 1:

```java
getWorkPackage(...)
getPlayPackage(...)
getRestPackage(...)
getMeetPackage(...)
getPreRaidPackage(...)
getRaidPackage(...)
getHidePackage(...)
validateBedPoi(...)
raidExistsAndActive(...)
raidExistsAndNotVictory(...)
```

The final class must expose only the retained core, idle, panic, and look helpers used by `Villager`. Remove imports for POI types, profession predicates, raids, beds, and social/raid controls. Keep `getFullLookBehavior` and `getMinimalLookBehavior` only if a retained package uses them.

- [ ] **Step 3: Remove automatic profession assignment and stale structure assignment state**

In `Villager.java`:

- remove `assignProfessionWhenSpawned` and its `DEFAULT_ASSIGN_PROFESSION_WHEN_SPAWNED` constant;
- remove the `assignProfessionWhenSpawned` reset in `customServerAiStep`;
- remove the `EntitySpawnReason.STRUCTURE` flag assignment in `finalizeSpawn`;
- stop writing `AssignProfessionWhenSpawned`; old input fields are ignored by the new reader; and
- keep `setVillagerData`, `getVillagerData`, `setProfession` through CraftBukkit, and explicit command/plugin profession changes unchanged.

Do not remove profession registries or trade-set mappings. Existing loaded professions still determine offers.

- [ ] **Step 4: Disable autonomous item pickup without removing the inventory API**

In the `Villager` constructor, prevent autonomous loot pickup:

```java
this.setCanPickUpLoot(false);
```

Remove the `pickUpItem(ServerLevel, ItemEntity)` and `wantsToPickUp(ServerLevel, ItemStack)` overrides. Keep `AbstractVillager`'s inventory and `CraftAbstractVillager.getInventory()` unchanged for plugin access. Remove `hasFarmSeeds`, `hasExcessFood`, and `wantsMoreFood` only after the deleted work/social behavior has no remaining callers. Keep `canBreed`, `getBreedOffspring`, and the food-level fields needed by explicit `Breedable` compatibility; they are no longer fed by villager AI.

- [ ] **Step 5: Remove autonomous breeding and social exchange call sites**

Delete the `VillagerMakeLove`, `TradeWithVillager`, `GiveGiftToHero`, bell-social, bed-jump, and automatic child-play controls from every package. Delete the corresponding behavior source files listed in the task file map only when the repository has no remaining callers.

Remove `VillagerBabiesSensor` from the Brain provider and retain baby age/dimensions/entity spawning. Babies use the minimal idle graph; they do not run autonomous play or breeding.
Do not delete `VillagerBabiesSensor.java` merely because the villager no longer registers it; `SensorType` still constructs it for registry and serialized-data compatibility.

Keep direct `sleep(Location)`, `wakeup()`, `zombify()`, `setProfession()`, `getInventory()`, and explicit plugin-controlled breeding hooks. Do not remove Bukkit interfaces merely because the vanilla AI no longer invokes them.

- [ ] **Step 6: Release stale villager POI claims during load/brain refresh**

Before replacing a loaded Brain or clearing removed memory state, release the old `HOME`, `JOB_SITE`, `POTENTIAL_JOB_SITE`, and `MEETING_POINT` claims through the existing `releasePoi`/`releaseAllPois` path. Then erase those memories from the new Brain. Preserve `releaseAllPois()` for `CraftVillager.remove()` and death cleanup even though new villagers no longer acquire claims.

The resulting load/refresh invariant is:

```java
brain.eraseMemory(MemoryModuleType.HOME);
brain.eraseMemory(MemoryModuleType.JOB_SITE);
brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
brain.eraseMemory(MemoryModuleType.MEETING_POINT);
```

Do not clear `VillagerData`, offers, XP, restock timestamps, or explicit `Gossips` in this step.

- [ ] **Step 7: Run compile and behavior-topology tests**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: all `VillagerGoalPackagesTest` methods pass; no removed behavior class is loaded by a retained villager package; existing standalone tests remain green.

- [ ] **Step 8: Regenerate patches and verify deleted behavior call sites**

Run:

```bash
./gradlew fixupSourcePatches rebuildPatches
```

Use the repository search tool to confirm the deleted classes have no remaining references outside their own deleted files. The intentional remaining compatibility references are `SensorType.VILLAGER_BABIES`, `VillagerBabiesSensor`, `SensorType.SECONDARY_POIS`, `SecondaryPoiSensor`, `SensorType.GOLEM_DETECTED`, `MemoryModuleType.GOLEM_DETECTED_RECENTLY`, and `GolemSensor`; these are handled as dormant registry definitions rather than active villager behavior.

- [ ] **Step 9: Commit removal of village simulation**

Stage only the changed villager/behavior sources, their regenerated patches, and `VillagerGoalPackagesTest`:

git add \
  paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AcquirePoi.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/ResetProfession.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/YieldJobSite.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/PoiCompetitorScan.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/GoToPotentialJobSite.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/WorkAtComposter.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/UseBonemeal.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/TradeWithVillager.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/GiveGiftToHero.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/SocializeAtBell.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/ReactToBell.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/RingBell.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/JumpOnBed.java \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerGoalPackages.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerMakeLove.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/AcquirePoi.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/AssignProfessionFromJobSite.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/HarvestFarmland.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/PoiCompetitorScan.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/ResetProfession.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/WorkAtPoi.java.patch \
  paper-server/src/test/java/io/papermc/paper/entity/VillagerGoalPackagesTest.java
git commit -m "feat: remove autonomous villager village simulation"
```

Before committing, inspect the staged path list and ensure `.gitignore`, runtime files, heap dumps, and unrelated user changes are not staged.

---

### Task 3: Remove automatic gossip and NMS reputation event plumbing

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java:118-128,280-288,312,520-558,629-669,875-962`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/ZombieVillager.java:39-42,265-274`
- Modify: `paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java:95-99,2460-2463`
- Delete: `paper-server/src/minecraft/java/net/minecraft/world/entity/ReputationEventHandler.java`
- Delete: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/village/ReputationEventType.java`
- Retain and modify only if needed: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/gossip/GossipContainer.java`, `GossipType.java`
- Retain unchanged public bridge: `paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftVillager.java`
- Create: `paper-server/src/test/java/org/bukkit/entity/VillagerReputationApiTest.java`

**Interfaces:**
- Consumes: preserved `GossipContainer` storage and `CraftVillager` reputation methods.
- Produces: no automatic gossip mutation, decay, transfer, or NMS reputation events; explicit reputation API methods and price application remain available.

- [ ] **Step 1: Add the public reputation compatibility test**

Create an ordinary standalone API-surface test:

```java
package org.bukkit.entity;

import java.util.Map;
import java.util.UUID;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Normal
class VillagerReputationApiTest {
    @Test
    void reputationMethodsRemainAvailable() throws NoSuchMethodException {
        assertNotNull(Villager.class.getMethod("getReputation", UUID.class));
        assertNotNull(Villager.class.getMethod("getReputations"));
        assertNotNull(Villager.class.getMethod("setReputation", UUID.class, com.destroystokyo.paper.entity.villager.Reputation.class));
        assertNotNull(Villager.class.getMethod("setReputations", Map.class));
        assertNotNull(Villager.class.getMethod("clearReputations"));
        assertNotNull(Villager.class.getMethod("updateDemand"));
        assertNotNull(Villager.class.getMethod("restock"));
    }
}
```

Run the normal suite and confirm the API test passes before touching NMS reputation plumbing. This locks the compatibility requirement independently of the removed internal event types.

- [ ] **Step 2: Remove automatic trade, cure, damage, and death reputation sources**

In `Villager.java`:

- remove the `ServerLevel.onReputationEvent(ReputationEventType.TRADE, ...)` call after trade; retain the happy particle event and clear `lastTradedPlayer` if it is still used for that visual;
- remove the `VILLAGER_HURT` event call from `setLastHurtByMob`, retaining the ordinary angry particle behavior;
- remove `tellWitnessesThatIWasMurdered` and its call from `die`, retaining POI release and normal death handling; and
- remove `onReputationEventFrom` and the `ReputationEventHandler` implementation.

The class declaration becomes:

```java
public class Villager extends AbstractVillager implements VillagerDataHolder {
```

Do not remove `getGossips`, `setGossips`, `getPlayerReputation`, or the `GossipContainer` field because the explicit public reputation bridge and zombie-villager state transfer still use them.

- [ ] **Step 3: Remove gossip transfer/decay from the active villager loop**

In `Villager.java`:

- remove `MAX_GOSSIP_TOPICS`, `GOSSIP_COOLDOWN`, and `GOSSIP_DECAY_INTERVAL`;
- remove `lastGossipTime` and `lastGossipDecayTime` runtime fields;
- remove `gossip(ServerLevel, Villager, long)` and `maybeDecayGossip()`;
- remove `maybeDecayGossip()` from both `tick()` and `inactiveTick()`; and
- stop writing `LastGossipDecay`, while continuing to read `Gossips` for explicit reputation compatibility.

Do not call `GossipContainer.decay()` or `transferFrom()` anywhere in villager runtime code. `GossipContainer` itself remains because `CraftVillager` uses its entity map and conversion persists the data.

- [ ] **Step 4: Remove the cure-generated reputation event**

In `ZombieVillager.finishConversion`, keep the advancement trigger, conversion, nausea effect, and copied gossip/trade state, but remove:

```java
level.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, player, villager);
```

Remove the `ReputationEventType` import. Cure no longer creates positive gossip automatically.

- [ ] **Step 5: Delete the now-unreferenced NMS event plumbing**

Remove `ReputationEventHandler.java` and `ReputationEventType.java` after all call sites are gone. In `ServerLevel.java`, remove the imports and this method:

```java
public void onReputationEvent(final ReputationEventType type, final Entity source, final ReputationEventHandler target) {
    target.onReputationEventFrom(type, source);
}
```

Keep `GossipType` and `GossipContainer` because explicit reputation API operations still map through them. Do not alter `CraftVillager.getReputation`, `setReputation`, `setReputations`, or `clearReputations` except for compilation adjustments caused by removed internal event types.

- [ ] **Step 6: Compile and run compatibility tests**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: the API reflection test passes, `CraftVillager` compiles against the retained gossip storage, and no NMS reputation event type remains referenced.

- [ ] **Step 7: Regenerate patches and commit reputation removal**

Run:

```bash
./gradlew fixupSourcePatches rebuildPatches
```

Stage only the villager, zombie-villager, ServerLevel, gossip compatibility, deleted reputation plumbing, test, and regenerated patches:

```bash
git add paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/monster/zombie/ZombieVillager.java \
  paper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ReputationEventHandler.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/village/ReputationEventType.java \
  paper-server/patches/sources/net/minecraft/world/entity/npc/villager/Villager.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/monster/zombie/ZombieVillager.java.patch \
  paper-server/patches/sources/net/minecraft/server/level/ServerLevel.java.patch \
  paper-server/src/test/java/org/bukkit/entity/VillagerReputationApiTest.java
git commit -m "feat: remove automatic villager reputation events"
```

If `GossipContainer.java` or `CraftVillager.java` has no semantic diff, do not stage it.

---

### Task 4: Remove village sieges and village-only iron-golem defense

**Files:**
- Modify: `paper-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java:675-677`
- Modify: `paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java:1287-1291`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/IronGolem.java:24-36,68-84`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java`
- Modify: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerPanicTrigger.java`
- Retain compatibility registrations: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/sensing/GolemSensor.java`, `SensorType.java`, and `MemoryModuleType.java`
- Delete after reference confirmation: `paper-server/src/minecraft/java/net/minecraft/world/entity/ai/village/VillageSiege.java`, `GolemRandomStrollInVillageGoal.java`, `MoveBackToVillageGoal.java`, `goal/target/DefendVillageTargetGoal.java`

**Interfaces:**
- Consumes: the minimal Brain and no-gossip Villager from Tasks 1–3.
- Produces: no nightly zombie siege, no villager-driven golem spawn, and no village-only IronGolem navigation or defense; ordinary golem combat and global raid systems remain.

- [ ] **Step 1: Remove active villager golem requests**

In `Villager.java`:

- remove `SensorType.GOLEM_DETECTED` from the Brain provider;
- remove the `GolemSensor` import;
- remove `spawnGolemIfNeeded`, `wantsToSpawnGolem`, and `golemSpawnConditionsMet`;
- remove `MemoryModuleType.GOLEM_DETECTED_RECENTLY` from active villager logic; and
- remove the Paper callback that invokes `GolemSensor.golemDetected` after a golem spawn.

In `VillagerPanicTrigger.java`, remove the periodic `body.spawnGolemIfNeeded(...)` block while retaining the logic that activates ordinary panic and moves away from hostile entities.

Keep `GolemSensor.java`, `SensorType.GOLEM_DETECTED`, and `MemoryModuleType.GOLEM_DETECTED_RECENTLY` as dormant compatibility registrations unless a compile-safe registry migration is explicitly added. No villager Brain may register or query the sensor/memory.

- [ ] **Step 2: Remove village-only goals from `IronGolem`**

Delete the imports and goal registrations below from `IronGolem.registerGoals()`:

```java
import net.minecraft.world.entity.ai.goal.GolemRandomStrollInVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveBackToVillageGoal;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
```

```java
this.goalSelector.addGoal(2, new MoveBackToVillageGoal(this, 0.6, false));
this.goalSelector.addGoal(4, new GolemRandomStrollInVillageGoal(this, 0.6));
this.targetSelector.addGoal(1, new DefendVillageTargetGoal(this));
```

Retain melee attack, move-towards-target, flower offering, looking, random looking, hurt-by targeting, player anger, ordinary hostile targeting, and anger reset. This preserves golems as entities and fighters without village ownership.

- [ ] **Step 3: Remove both `VillageSiege` registrations**

In `MinecraftServer.java`, remove the import and the `new VillageSiege()` entry from `overworldCustomSpawners`:

```java
List<CustomSpawner> overworldCustomSpawners = ImmutableList.of(
    new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new WanderingTraderSpawner(savedDataStorage)
);
```

Apply the same removal in `CraftServer.java`'s temporary world custom-spawner list. Preserve phantom, patrol, cat, and wandering-trader spawners.

Delete `VillageSiege.java` after both registrations are gone. The removal affects only automatic nighttime village sieges; ordinary zombies and the separate raid system remain.

- [ ] **Step 4: Delete village-only goal classes after call-site confirmation**

Confirm there are no remaining references, then delete:

```text
paper-server/src/minecraft/java/net/minecraft/world/entity/ai/village/VillageSiege.java
paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/GolemRandomStrollInVillageGoal.java
paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/MoveBackToVillageGoal.java
paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/target/DefendVillageTargetGoal.java
```

Do not delete `GolemSensor.java` or its registry declarations in this task because their identifiers may be needed to decode old serialized brains.

- [ ] **Step 5: Compile and run the server test suite**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: compilation succeeds without village-siege or village-goal references, and the existing normal suite remains green.

- [ ] **Step 6: Regenerate patches and commit village defense removal**

Run:

```bash
./gradlew fixupSourcePatches rebuildPatches
```

Stage only the two spawner-list files, IronGolem, Villager/Panic changes, deleted village goal classes, and regenerated vanilla patches:

```bash
git add paper-server/src/minecraft/java/net/minecraft/server/MinecraftServer.java \
  paper-server/src/main/java/org/bukkit/craftbukkit/CraftServer.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/animal/golem/IronGolem.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/npc/villager/Villager.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/behavior/VillagerPanicTrigger.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/village/VillageSiege.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/GolemRandomStrollInVillageGoal.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/MoveBackToVillageGoal.java \
  paper-server/src/minecraft/java/net/minecraft/world/entity/ai/goal/target/DefendVillageTargetGoal.java \
  paper-server/patches/sources/net/minecraft/server/MinecraftServer.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/animal/golem/IronGolem.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/npc/villager/Villager.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/behavior/VillagerPanicTrigger.java.patch \
  paper-server/patches/sources/net/minecraft/world/entity/ai/village/VillageSiege.java.patch
git commit -m "feat: remove village siege and golem defense"
```

`CraftServer.java` is an ordinary server source, so no patch file is expected for that file. Do not stage unrelated CraftBukkit changes.

---

### Task 5: Full verification, runtime smoke checks, and final patch hygiene

**Files:**
- Verify only: all files changed by Tasks 1–4
- Do not create new production files in this task

**Interfaces:**
- Consumes: the four committed implementation units and the approved design contract.
- Produces: verified server behavior, regenerated patch state, and a clean intentional diff with unrelated user changes preserved.

- [ ] **Step 1: Run the complete targeted server test suite**

Run:

```bash
./gradlew :paper-server:test --tests 'org.bukkit.support.suite.NormalTestSuite'
```

Expected: the suite passes, including `VillagerGoalPackagesTest` and `VillagerReputationApiTest`. If a failure is unrelated to villager changes, record the exact failing test and stop before changing unrelated code.

- [ ] **Step 2: Build the server artifact**

Run:

```bash
./gradlew :paper-server:compileJava :paper-server:compileTestJava
./gradlew createPaperclipJar
```

Expected: both compilation tasks and `createPaperclipJar` succeed. The generated paperclip artifact is present under `paper-server/build/libs/` and is not staged.

- [ ] **Step 3: Run the deterministic runtime smoke scenario**

Use a temporary server directory, never `run/`, and launch the built paperclip artifact through the project process supervisor. The command shape is:

```bash
smoke_dir=$(mktemp -d)
printf 'eula=true\n' > "$smoke_dir/eula.txt"
printf 'online-mode=false\n' > "$smoke_dir/server.properties"
java -jar paper-server/build/libs/paper-paperclip-*.jar --nogui
```

Exercise these observations in the running server:

1. Summon an unemployed villager, place a workstation, advance several hundred ticks, and inspect entity data. The profession remains `none`; no `HOME`, `JOB_SITE`, `POTENTIAL_JOB_SITE`, or `MEETING_POINT` brain memory is claimed.
2. Summon a profession-loaded villager without a workstation, open its trade screen, complete a trade, advance the normal restock window, and confirm the offer uses reset and demand/XP behavior remains active.
3. Place crops and food near villagers. They do not harvest, bonemeal, pick up, throw, or share those items.
4. Place beds and bells near two villagers. They do not acquire beds, meet, ring bells, exchange items, give Hero gifts, or breed autonomously.
5. Trade with, cure, hurt, and kill villagers while observing serialized gossip/reputation. No automatic gossip entry is added. Explicit API-set reputation remains readable and affects only the preserved explicit pricing path.
6. Trigger ordinary hostile panic near a villager. The villager flees but does not spawn an iron golem.
7. Spawn an iron golem near a bed/bell/villager cluster. It does not navigate toward the village or acquire village defense targets, but it still attacks ordinary hostile targets and responds to damage.
8. Let the server cross a night at a village. No `VillageSiege` zombies spawn; ordinary hostile spawning and the separate raid system remain available.

Expected: each observation matches the approved design, with no client crash, merchant screen failure, entity-load failure, or stale POI ticket preventing unrelated POI users.

- [ ] **Step 4: Rebuild and inspect generated patches**

Run:

```bash
./gradlew fixupSourcePatches rebuildPatches
```

Use the repository file/diff tools to verify:

- all modified vanilla source files have corresponding regenerated patch changes;
- no patch exists under `paper-server/patches/sources/dev/mintychochip/`;
- no Alkahest source was accidentally moved under `src/minecraft/java/dev/mintychochip/`; and
- generated build output, runtime logs, worlds, heap dumps, `bench/`, and `serve/` are not staged.

- [ ] **Step 5: Review the final intended diff and status**

Run:

```bash
git status --short
git diff --stat 590507e85..HEAD
```

Expected: the four implementation commits contain only the villager behavior units and their tests/patches, while the pre-existing unrelated worktree changes remain uncommitted and untouched. If the number of implementation commits differs because a task needed a narrowly separated fix, preserve atomic commit subjects rather than squashing unrelated behavior.

- [ ] **Step 6: Report verification evidence**

Record the exact Gradle commands and observed runtime results. Do not claim the design is implemented until the targeted test suite, compile, patch regeneration, paperclip build, and runtime smoke scenario all succeed.
