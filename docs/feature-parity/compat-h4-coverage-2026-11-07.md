# 실제 jar 대 호환 계층 대조표 (H4, 생성 파일)

`./gradlew :furnace-compat-paper:compatReport` 가 만든 파일이다. 직접 고치지 않는다.

실제 Bukkit·Paper API 로 컴파일된 jar(Maven Central 의 실제 플러그인과 플러그인 라이브러리)의 클래스 파일에서
`org.bukkit.*` 등 서버가 줘야 하는 심볼(클래스·필드·메서드, 반환형까지 정확한 서명)을 읽어, `furnace-compat-paper` 가
내놓는 클래스와 JVM 심볼 해석 규칙대로 견준 결과다. **정적 분석이라 요구 상한**이다 — 그 코드 경로가 실제로 실행되는지는 보지 않는다.
"충족"은 요구한 서로 다른 심볼(클래스 + 멤버) 가운데 호환 계층에 있는 비율이다.

## 0. 실제 플러그인 jar 를 헤드리스 서버에 올린 결과

`plugin.yml` 이 있는 실제 jar 를 `FurnaceTestServer` 에 올려 5틱 돌렸다. 실패는 서버 로그의 가장 안쪽 예외다.

| jar | 상태 | 처음 만난 오류 |
|---|---|---|
| `commandapi-bukkit-plugin-10.1.2.jar` | LOADED_NOT_ENABLED | `java.lang.ClassNotFoundException: com.mojang.brigadier.context.CommandContext` |
| `helper-5.6.14.jar` | LOADED_NOT_ENABLED | `java.lang.ClassNotFoundException: com.google.common.base.Supplier` |

상태: `NOT_LOADED`(로드 자체 실패) < `LOADED_NOT_ENABLED`(onLoad·onEnable 이 던짐) < `ENABLED_THEN_CRASHED` < `ENABLED`.

## 1. jar 별 Bukkit 계열 API(Bukkit·Paper·Spigot) 충족

| jar | 종류 | 클래스 수 | 요구 심볼 | 없음 | 충족 |
|---|---|---:|---:|---:|---:|
| `IF-0.12.2.jar` | 라이브러리 | 1042 | 191 | 94 | 50.8% |
| `XSeries-14.0.0.jar` | 라이브러리 | 332 | 948 | 694 | 26.8% |
| `adventure-platform-bukkit-4.4.1.jar` | 라이브러리 | 65 | 172 | 97 | 43.6% |
| `boosted-yaml-1.3.8.jar` | 라이브러리 | 292 | 0 | 0 | 100.0% |
| `bstats-base-3.2.1.jar` | 라이브러리 | 13 | 0 | 0 | 100.0% |
| `bstats-bukkit-3.2.1.jar` | 라이브러리 | 1 | 28 | 1 | 96.4% |
| `cloud-bukkit-2.0.1.jar` | 라이브러리 | 111 | 93 | 5 | 94.6% |
| `cloud-paper-2.0.1.jar` | 라이브러리 | 38 | 103 | 67 | 35.0% |
| `commandapi-bukkit-core-12.1.0.jar` | 라이브러리 | 155 | 147 | 49 | 66.7% |
| `commandapi-bukkit-plugin-10.1.2.jar` | 플러그인 | 308 | 272 | 105 | 61.4% |
| `commodore-2.2.jar` | 라이브러리 | 12 | 26 | 8 | 69.2% |
| `fastboard-2.2.2.jar` | 라이브러리 | 8 | 13 | 0 | 100.0% |
| `helper-5.6.14.jar` | 플러그인 | 1197 | 441 | 184 | 58.3% |

## 2. 종류별 요구와 빠짐 (전체 jar 합집합)

| 종류 | 요구 클래스 | 없는 클래스 | 요구 멤버(있는 클래스 기준) | 없는 멤버 | 없는 클래스의 멤버 호출 |
|---|---:|---:|---:|---:|---:|
| Bukkit API | 291 | 206 | 663 | 167 | 730 |
| Paper API | 25 | 25 | 0 | 0 | 49 |
| Spigot·BungeeChat | 6 | 6 | 0 | 0 | 19 |
| CraftBukkit | 358 | 356 | 2 | 0 | 427 |
| net.minecraft·authlib | 245 | 239 | 120 | 113 | 706 |
| Adventure(Paper 제공) | 80 | 80 | 0 | 0 | 153 |
| 서버 제공 라이브러리(Guava·Gson·SnakeYAML 등) | 126 | 103 | 125 | 0 | 399 |

## 3. 없는 Bukkit 계열 클래스 순위 (요구하는 jar 수 → 멤버 호출 수)

전체 237개 중 위 80개. 이 순서가 다음 묶음의 우선순위 후보다.

| 클래스 | jar 수 | 멤버 호출(합) |
|---|---:|---:|
| `org.bukkit.Registry` | 4 | 19 |
| `org.bukkit.potion.PotionEffectType` | 4 | 7 |
| `net.md_5.bungee.chat.ComponentSerializer` | 4 | 5 |
| `net.md_5.bungee.api.chat.BaseComponent` | 4 | 4 |
| `org.bukkit.boss.BossBar` | 3 | 31 |
| `org.bukkit.scoreboard.DisplaySlot` | 3 | 19 |
| `org.bukkit.boss.BarColor` | 3 | 16 |
| `org.bukkit.boss.BarStyle` | 3 | 12 |
| `org.bukkit.plugin.messaging.Messenger` | 3 | 12 |
| `org.bukkit.command.ProxiedCommandSender` | 3 | 9 |
| `net.md_5.bungee.api.ChatMessageType` | 3 | 5 |
| `org.bukkit.entity.Player$Spigot` | 3 | 5 |
| `org.bukkit.boss.BarFlag` | 3 | 4 |
| `org.bukkit.block.Biome` | 3 | 2 |
| `org.bukkit.Sound` | 3 | 1 |
| `org.bukkit.plugin.messaging.PluginMessageListener` | 3 | 1 |
| `org.bukkit.inventory.InventoryView` | 2 | 19 |
| `org.bukkit.event.inventory.InventoryClickEvent` | 2 | 14 |
| `org.bukkit.Color` | 2 | 13 |
| `org.bukkit.inventory.meta.BookMeta` | 2 | 11 |
| `org.bukkit.SoundCategory` | 2 | 10 |
| `org.bukkit.block.BlockState` | 2 | 8 |
| `org.bukkit.event.inventory.InventoryDragEvent` | 2 | 8 |
| `org.bukkit.profile.PlayerProfile` | 2 | 6 |
| `io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler` | 2 | 5 |
| `org.bukkit.potion.PotionEffect` | 2 | 5 |
| `org.bukkit.Particle$DustTransition` | 2 | 4 |
| `org.bukkit.command.RemoteConsoleCommandSender` | 2 | 4 |
| `org.bukkit.event.inventory.ClickType` | 2 | 4 |
| `org.bukkit.event.inventory.InventoryCloseEvent` | 2 | 4 |
| `org.bukkit.event.player.PlayerRegisterChannelEvent` | 2 | 4 |
| `net.md_5.bungee.api.chat.TextComponent` | 2 | 3 |
| `org.bukkit.Particle` | 2 | 3 |
| `org.bukkit.event.inventory.InventoryOpenEvent` | 2 | 3 |
| `org.bukkit.inventory.meta.LeatherArmorMeta` | 2 | 3 |
| `org.bukkit.FluidCollisionMode` | 2 | 2 |
| `org.bukkit.Particle$DustOptions` | 2 | 2 |
| `org.bukkit.Particle$Trail` | 2 | 2 |
| `org.bukkit.Vibration` | 2 | 2 |
| `org.bukkit.Vibration$Destination$BlockDestination` | 2 | 2 |
| `org.bukkit.inventory.meta.SkullMeta` | 2 | 2 |
| `org.bukkit.profile.PlayerTextures` | 2 | 2 |
| `org.bukkit.scoreboard.Objective` | 2 | 2 |
| `org.bukkit.scoreboard.Team` | 2 | 2 |
| `io.papermc.paper.plugin.configuration.PluginMeta` | 2 | 1 |
| `org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason` | 2 | 1 |
| `org.bukkit.help.HelpTopic` | 2 | 1 |
| `org.bukkit.inventory.Recipe` | 2 | 1 |
| `org.bukkit.Vibration$Destination` | 2 | 0 |
| `org.bukkit.advancement.Advancement` | 2 | 0 |
| `org.bukkit.event.entity.CreatureSpawnEvent` | 2 | 0 |
| `org.bukkit.loot.LootTable` | 2 | 0 |
| `org.bukkit.DyeColor` | 1 | 21 |
| `org.bukkit.Instrument` | 1 | 18 |
| `org.bukkit.entity.ArmorStand` | 1 | 18 |
| `org.bukkit.WorldBorder` | 1 | 15 |
| `org.bukkit.map.MapView` | 1 | 14 |
| `org.bukkit.inventory.EntityEquipment` | 1 | 12 |
| `org.bukkit.inventory.MerchantRecipe` | 1 | 11 |
| `org.bukkit.inventory.meta.MapMeta` | 1 | 11 |
| `org.bukkit.TreeSpecies` | 1 | 10 |
| `org.bukkit.inventory.meta.PotionMeta` | 1 | 10 |
| `org.bukkit.SkullType` | 1 | 9 |
| `org.bukkit.entity.Pig` | 1 | 9 |
| `org.bukkit.Note$Tone` | 1 | 8 |
| `org.bukkit.inventory.meta.components.CustomModelDataComponent` | 1 | 8 |
| `com.destroystokyo.paper.Title$Builder` | 1 | 6 |
| `org.bukkit.FireworkEffect` | 1 | 6 |
| `org.bukkit.FireworkEffect$Builder` | 1 | 6 |
| `org.bukkit.attribute.AttributeModifier` | 1 | 6 |
| `org.bukkit.inventory.meta.TropicalFishBucketMeta` | 1 | 6 |
| `com.destroystokyo.paper.event.server.AsyncTabCompleteEvent` | 1 | 5 |
| `org.bukkit.Art` | 1 | 5 |
| `org.bukkit.block.Skull` | 1 | 5 |
| `org.bukkit.entity.Ageable` | 1 | 5 |
| `org.bukkit.inventory.meta.CompassMeta` | 1 | 5 |
| `com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent` | 1 | 4 |
| `com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent` | 1 | 4 |
| `org.bukkit.Note` | 1 | 4 |
| `org.bukkit.block.Banner` | 1 | 4 |

### 패키지별 (없는 클래스 수, 그 클래스들의 멤버 호출 합)

| 패키지 | 없는 클래스 | 멤버 호출 |
|---|---:|---:|
| `org.bukkit` | 27 | 169 |
| `org.bukkit.entity` | 62 | 118 |
| `org.bukkit.inventory.meta` | 18 | 72 |
| `org.bukkit.boss` | 4 | 63 |
| `org.bukkit.inventory` | 7 | 45 |
| `org.bukkit.event.inventory` | 9 | 37 |
| `org.bukkit.scoreboard` | 5 | 26 |
| `org.bukkit.block` | 7 | 25 |
| `org.bukkit.event.player` | 9 | 20 |
| `org.bukkit.potion` | 4 | 20 |
| `org.bukkit.map` | 2 | 16 |
| `org.bukkit.material` | 8 | 15 |
| `org.bukkit.block.data` | 7 | 14 |
| `org.bukkit.command` | 3 | 14 |
| `org.bukkit.plugin.messaging` | 2 | 13 |
| `org.bukkit.attribute` | 4 | 10 |
| `org.bukkit.event.entity` | 6 | 10 |
| `com.destroystokyo.paper.event.brigadier` | 2 | 8 |
| `io.papermc.paper.command.brigadier` | 4 | 8 |
| `io.papermc.paper.threadedregions.scheduler` | 4 | 8 |
| `org.bukkit.inventory.meta.components` | 1 | 8 |
| `org.bukkit.profile` | 2 | 8 |
| `org.bukkit.util.io` | 2 | 8 |
| `com.destroystokyo.paper` | 2 | 7 |
| `com.destroystokyo.paper.event.server` | 2 | 7 |
| `net.md_5.bungee.api.chat` | 2 | 7 |
| `net.md_5.bungee.chat` | 2 | 7 |
| `net.md_5.bungee.api` | 2 | 5 |
| `org.bukkit.inventory.meta.trim` | 3 | 5 |
| `org.bukkit.block.data.type` | 2 | 4 |
| `io.papermc.paper.registry` | 2 | 3 |
| `org.bukkit.block.banner` | 2 | 3 |
| `io.papermc.paper.plugin.bootstrap` | 1 | 2 |
| `org.bukkit.configuration.serialization` | 1 | 2 |
| `com.destroystokyo.paper.brigadier` | 1 | 1 |
| `io.papermc.paper.brigadier` | 1 | 1 |
| `io.papermc.paper.plugin.configuration` | 1 | 1 |
| `io.papermc.paper.plugin.lifecycle.event` | 1 | 1 |
| `io.papermc.paper.plugin.lifecycle.event.registrar` | 1 | 1 |
| `io.papermc.paper.plugin.lifecycle.event.types` | 3 | 1 |
| `org.bukkit.enchantments` | 1 | 1 |
| `org.bukkit.help` | 1 | 1 |
| `org.bukkit.metadata` | 1 | 1 |
| `org.bukkit.permissions` | 1 | 1 |
| `org.bukkit.util` | 2 | 1 |
| `org.bukkit.advancement` | 1 | 0 |
| `org.bukkit.loot` | 1 | 0 |
| `org.bukkit.plugin` | 1 | 0 |

## 4. 클래스는 있는데 없는 멤버 순위

전체 271개 중 위 120개 (요구하는 jar 수 순).

| 멤버 | jar 수 |
|---|---:|
| `net.minecraft.server.MinecraftServer#server` | 9 |
| `org.bukkit.entity.Player#updateCommands()V` | 4 |
| `org.bukkit.Server#getMessenger()Lorg/bukkit/plugin/messaging/Messenger;` | 3 |
| `org.bukkit.entity.Player#sendPluginMessage(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;[B)V` | 3 |
| `org.bukkit.entity.Player#spigot()Lorg/bukkit/entity/Player$Spigot;` | 3 |
| `org.bukkit.util.Vector#multiply(I)Lorg/bukkit/util/Vector;` | 3 |
| `net.minecraft.server.MinecraftServer#ax` | 2 |
| `net.minecraft.world.item.ItemStack#h()Lnet/minecraft/core/Holder;` | 2 |
| `net.minecraft.world.item.ItemStack#i()Lnet/minecraft/core/Holder;` | 2 |
| `org.bukkit.Bukkit#addRecipe(Lorg/bukkit/inventory/Recipe;)Z` | 2 |
| `org.bukkit.Bukkit#getOnlineMode()Z` | 2 |
| `org.bukkit.Server#getGlobalRegionScheduler()Lio/papermc/paper/threadedregions/scheduler/GlobalRegionScheduler;` | 2 |
| `org.bukkit.World#playSound(Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V` | 2 |
| `org.bukkit.World#spawn(Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity;` | 2 |
| `org.bukkit.entity.LivingEntity#getEyeHeight()D` | 2 |
| `org.bukkit.entity.Player#getTargetBlockExact(ILorg/bukkit/FluidCollisionMode;)Lorg/bukkit/block/Block;` | 2 |
| `org.bukkit.entity.Player#playSound(Lorg/bukkit/Location;Ljava/lang/String;FF)V` | 2 |
| `org.bukkit.entity.Player#playSound(Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V` | 2 |
| `org.bukkit.entity.Player#playSound(Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V` | 2 |
| `org.bukkit.entity.Player#resetTitle()V` | 2 |
| `org.bukkit.entity.Player#sendTitle(Ljava/lang/String;Ljava/lang/String;III)V` | 2 |
| `org.bukkit.entity.Player#setPlayerListHeaderFooter(Ljava/lang/String;Ljava/lang/String;)V` | 2 |
| `org.bukkit.entity.Player#stopSound(Ljava/lang/String;)V` | 2 |
| `org.bukkit.inventory.ItemStack#<init>(Lorg/bukkit/Material;IS)V` | 2 |
| `org.bukkit.inventory.meta.ItemMeta#displayName(Lnet/kyori/adventure/text/Component;)V` | 2 |
| `org.bukkit.inventory.meta.ItemMeta#lore()Ljava/util/List;` | 2 |
| `org.bukkit.inventory.meta.ItemMeta#lore(Ljava/util/List;)V` | 2 |
| `org.bukkit.util.Vector#toLocation(Lorg/bukkit/World;)Lorg/bukkit/Location;` | 2 |
| `net.minecraft.server.MinecraftServer#aA` | 1 |
| `net.minecraft.server.MinecraftServer#aA()Lnet/minecraft/server/CustomFunctionData;` | 1 |
| `net.minecraft.server.MinecraftServer#aB()Lnet/minecraft/server/AdvancementDataWorld;` | 1 |
| `net.minecraft.server.MinecraftServer#aB()Lnet/minecraft/server/packs/repository/ResourcePackRepository;` | 1 |
| `net.minecraft.server.MinecraftServer#aC()Lnet/minecraft/commands/CommandDispatcher;` | 1 |
| `net.minecraft.server.MinecraftServer#aC()Lnet/minecraft/server/CustomFunctionData;` | 1 |
| `net.minecraft.server.MinecraftServer#aD()Lnet/minecraft/server/AdvancementDataWorld;` | 1 |
| `net.minecraft.server.MinecraftServer#aD()Lnet/minecraft/server/packs/repository/ResourcePackRepository;` | 1 |
| `net.minecraft.server.MinecraftServer#aE()Lnet/minecraft/commands/CommandDispatcher;` | 1 |
| `net.minecraft.server.MinecraftServer#aE()Lnet/minecraft/server/AdvancementDataWorld;` | 1 |
| `net.minecraft.server.MinecraftServer#aE()Lnet/minecraft/server/CustomFunctionData;` | 1 |
| `net.minecraft.server.MinecraftServer#aF()Lnet/minecraft/server/CustomFunctionData;` | 1 |
| `net.minecraft.server.MinecraftServer#aF()Lnet/minecraft/server/packs/repository/ResourcePackRepository;` | 1 |
| `net.minecraft.server.MinecraftServer#aG()Lnet/minecraft/commands/CommandDispatcher;` | 1 |
| `net.minecraft.server.MinecraftServer#aG()Lnet/minecraft/server/packs/repository/ResourcePackRepository;` | 1 |
| `net.minecraft.server.MinecraftServer#aH()Lnet/minecraft/commands/CommandDispatcher;` | 1 |
| `net.minecraft.server.MinecraftServer#aH()Lnet/minecraft/world/level/storage/loot/LootDataManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aI()Lnet/minecraft/world/item/crafting/CraftingManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aJ()Lnet/minecraft/world/level/storage/loot/LootDataManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aR()Lnet/minecraft/util/profiling/GameProfilerFiller;` | 1 |
| `net.minecraft.server.MinecraftServer#aS()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aT()Lnet/minecraft/util/profiling/GameProfilerFiller;` | 1 |
| `net.minecraft.server.MinecraftServer#aT()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aT()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.MinecraftServer#aU()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.MinecraftServer#aU()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.MinecraftServer#aV()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.MinecraftServer#aX()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aY()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;` | 1 |
| `net.minecraft.server.MinecraftServer#aY()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.MinecraftServer#aZ()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.MinecraftServer#aZ()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.MinecraftServer#ar` | 1 |
| `net.minecraft.server.MinecraftServer#as` | 1 |
| `net.minecraft.server.MinecraftServer#at` | 1 |
| `net.minecraft.server.MinecraftServer#au` | 1 |
| `net.minecraft.server.MinecraftServer#av` | 1 |
| `net.minecraft.server.MinecraftServer#ay` | 1 |
| `net.minecraft.server.MinecraftServer#az` | 1 |
| `net.minecraft.server.MinecraftServer#az()Lnet/minecraft/server/AdvancementDataWorld;` | 1 |
| `net.minecraft.server.MinecraftServer#b(Ljava/util/function/BooleanSupplier;)V` | 1 |
| `net.minecraft.server.MinecraftServer#ba()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.MinecraftServer#ba()Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;` | 1 |
| `net.minecraft.server.MinecraftServer#bb()Lnet/minecraft/core/LayeredRegistryAccess;` | 1 |
| `net.minecraft.server.MinecraftServer#bb()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.MinecraftServer#bc()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.MinecraftServer#bc()Lnet/minecraft/server/ReloadableServerRegistries$a;` | 1 |
| `net.minecraft.server.MinecraftServer#be()Lnet/minecraft/server/ReloadableServerRegistries$b;` | 1 |
| `net.minecraft.server.MinecraftServer#bl()Z` | 1 |
| `net.minecraft.server.MinecraftServer#bq()Z` | 1 |
| `net.minecraft.server.MinecraftServer#bw()Z` | 1 |
| `net.minecraft.server.MinecraftServer#bx()Z` | 1 |
| `net.minecraft.server.MinecraftServer#c(Ljava/util/function/BooleanSupplier;)V` | 1 |
| `net.minecraft.server.MinecraftServer#vanillaCommandDispatcher` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aT()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aU()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aU()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aV()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aY()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aZ()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#aZ()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#ba()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#bb()Lnet/minecraft/world/level/storage/SaveData;` | 1 |
| `net.minecraft.server.dedicated.DedicatedServer#bc()Lnet/minecraft/core/IRegistryCustom$Dimension;` | 1 |
| `net.minecraft.server.level.ServerPlayer#sendMerchantOffers(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_20_R1/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_20_R2/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_20_R3/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_20_R4/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_21_R1/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_21_R2/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_21_R3/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_21_R4/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.Entity#getBukkitEntity()Lorg/bukkit/craftbukkit/v1_21_R5/entity/CraftEntity;` | 1 |
| `net.minecraft.world.entity.player.Player#containerMenu` | 1 |
| `net.minecraft.world.entity.player.Player#getInventory()Lnet/minecraft/world/entity/player/Inventory;` | 1 |
| `net.minecraft.world.item.ItemStack#H()I` | 1 |
| `net.minecraft.world.item.ItemStack#I()I` | 1 |
| `net.minecraft.world.item.ItemStack#L()I` | 1 |
| `net.minecraft.world.item.ItemStack#M()I` | 1 |
| `net.minecraft.world.item.ItemStack#N()I` | 1 |
| `net.minecraft.world.item.ItemStack#a()Lnet/minecraft/core/component/DataComponentMap;` | 1 |
| `net.minecraft.world.item.ItemStack#a(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z` | 1 |
| `net.minecraft.world.item.ItemStack#b` | 1 |
| `net.minecraft.world.item.ItemStack#b()Z` | 1 |
| `net.minecraft.world.item.ItemStack#b(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z` | 1 |
| `net.minecraft.world.item.ItemStack#c()Lnet/minecraft/world/item/Item;` | 1 |
| `net.minecraft.world.item.ItemStack#d()Lnet/minecraft/core/component/DataComponentPatch;` | 1 |
| `net.minecraft.world.item.ItemStack#d()Lnet/minecraft/world/item/Item;` | 1 |
| `net.minecraft.world.item.ItemStack#e()Lnet/minecraft/core/component/DataComponentPatch;` | 1 |
| `net.minecraft.world.item.ItemStack#e()Z` | 1 |
| `net.minecraft.world.item.ItemStack#f` | 1 |

## 5. CraftBukkit·net.minecraft 요구 (H5~H7 의 수요)

`org.bukkit.craftbukkit.*`·`net.minecraft.*` 를 상수 풀에 직접 이름으로 가진 클래스. 대부분 리플렉션 문자열로 가려져 정적으로는 일부만 보인다(그래서 실제 플러그인 기동이 필요하다).

| 클래스 | jar 수 |
|---|---:|
| `com.mojang.authlib.GameProfile` | 3 |
| `com.mojang.authlib.properties.Property` | 2 |
| `com.mojang.authlib.properties.PropertyMap` | 2 |
| `com.mojang.serialization.Codec` | 2 |
| `com.mojang.serialization.DataResult` | 2 |
| `com.mojang.serialization.JsonOps` | 2 |
| `net.minecraft.core.BlockPosition` | 2 |
| `net.minecraft.core.HolderLookup` | 2 |
| `net.minecraft.core.HolderLookup$a` | 2 |
| `net.minecraft.network.chat.ComponentSerialization` | 2 |
| `net.minecraft.network.chat.IChatBaseComponent` | 2 |
| `net.minecraft.network.chat.IChatBaseComponent$ChatSerializer` | 2 |
| `net.minecraft.server.level.EntityPlayer` | 2 |
| `net.minecraft.world.item.ItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_21_R1.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_21_R2.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_21_R3.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_21_R4.inventory.CraftItemStack` | 2 |
| `org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack` | 2 |
| `com.mojang.datafixers.util.Pair` | 1 |
| `com.mojang.serialization.MapCodec` | 1 |
| `net.minecraft.EnumChatFormat` | 1 |
| `net.minecraft.advancements.Advancement` | 1 |
| `net.minecraft.advancements.AdvancementHolder` | 1 |
| `net.minecraft.advancements.critereon.CriterionConditionValue` | 1 |
| `net.minecraft.advancements.critereon.CriterionConditionValue$DoubleRange` | 1 |
| `net.minecraft.advancements.critereon.CriterionConditionValue$IntegerRange` | 1 |
| `net.minecraft.commands.CommandBuildContext` | 1 |
| `net.minecraft.commands.CommandBuildContext$a` | 1 |
| `net.minecraft.commands.CommandDispatcher` | 1 |
| `net.minecraft.commands.CommandListenerWrapper` | 1 |
| `net.minecraft.commands.CommandResultCallback` | 1 |
| `net.minecraft.commands.CustomFunction` | 1 |
| `net.minecraft.commands.CustomFunction$c` | 1 |
| `net.minecraft.commands.CustomFunction$d` | 1 |
| `net.minecraft.commands.FunctionInstantiationException` | 1 |
| `net.minecraft.commands.ICompletionProvider` | 1 |
| `net.minecraft.commands.arguments.ArgumentAngle` | 1 |
| `net.minecraft.commands.arguments.ArgumentChat` | 1 |
| `net.minecraft.commands.arguments.ArgumentChatComponent` | 1 |
| `net.minecraft.commands.arguments.ArgumentChatFormat` | 1 |
| `net.minecraft.commands.arguments.ArgumentCriterionValue` | 1 |
| `net.minecraft.commands.arguments.ArgumentCriterionValue$a` | 1 |
| `net.minecraft.commands.arguments.ArgumentCriterionValue$b` | 1 |
| `net.minecraft.commands.arguments.ArgumentDimension` | 1 |
| `net.minecraft.commands.arguments.ArgumentEntity` | 1 |
| `net.minecraft.commands.arguments.ArgumentMathOperation` | 1 |
| `net.minecraft.commands.arguments.ArgumentMathOperation$a` | 1 |
| `net.minecraft.commands.arguments.ArgumentMinecraftKeyRegistered` | 1 |
| `net.minecraft.commands.arguments.ArgumentNBTTag` | 1 |
| `net.minecraft.commands.arguments.ArgumentParticle` | 1 |
| `net.minecraft.commands.arguments.ArgumentProfile` | 1 |
| `net.minecraft.commands.arguments.ArgumentScoreboardCriteria` | 1 |
| `net.minecraft.commands.arguments.ArgumentScoreboardObjective` | 1 |
| `net.minecraft.commands.arguments.ArgumentScoreboardSlot` | 1 |
| `net.minecraft.commands.arguments.ArgumentScoreboardTeam` | 1 |

전체 603개.

## 6. 서버가 줘야 하는 라이브러리(Paper 는 싣고 있음)

| 라이브러리 | 요구 클래스(서로 다른 것) | 서버 클래스패스에 전부 있나 |
|---|---:|---|
| com.google.common(Guava) | 67 | 아니오(일부 또는 전부 없음) |
| com.google.gson(Gson) | 22 | 예 |
| com.mojang.brigadier | 30 | 아니오(일부 또는 전부 없음) |
| net.kyori(Adventure) | 80 | 아니오(일부 또는 전부 없음) |
| org.apache.logging(log4j) | 2 | 아니오(일부 또는 전부 없음) |
| org.slf4j | 1 | 예 |
| org.yaml.snakeyaml(SnakeYAML) | 4 | 아니오(일부 또는 전부 없음) |

("서버 클래스패스"는 호환 모듈 시험 클래스패스 기준이다. 플러그인이 `libraries:` 로 선언한 것은 자동 로딩(R1)이 받는다.)

## 7. jar 별 상세 (없는 클래스, 위 30개)

### `IF-0.12.2.jar`

없는 클래스 19개: `MerchantRecipe`(11), `InventoryClickEvent`(9), `InventoryView`(7), `InventoryDragEvent`(6), `Item`(3), `EntityPickupItemEvent`(3), `Registry`(2), `ClickType`(2), `InventoryAction`(2), `InventoryCloseEvent`(2), `PlayerDropItemEvent`(2), `SkullMeta`(2), `PlayerProfile`(2), `DragType`(1), `InventoryOpenEvent`(1), `TradeSelectEvent`(1), `PlayerTextures`(1), `ChatColor`, `InventoryType$SlotType`

### `XSeries-14.0.0.jar`

없는 클래스 157개: `DyeColor`(21), `Instrument`(18), `WorldBorder`(15), `MapView`(14), `Registry`(12), `EntityEquipment`(12), `InventoryView`(12), `MapMeta`(11), `Color`(10), `TreeSpecies`(10), `PotionMeta`(10), `SkullType`(9), `Note$Tone`(8), `BlockState`(8), `BookMeta`(8), `CustomModelDataComponent`(8), `FireworkEffect`(6), `FireworkEffect$Builder`(6), `AttributeModifier`(6), `TropicalFishBucketMeta`(6), `Art`(5), `Skull`(5), `BossBar`(5), `Ageable`(5), `CompassMeta`(5), `PotionEffectType`(5), `Note`(4), `Banner`(4), `AbstractHorse`(4), `Bee`(4) …

### `adventure-platform-bukkit-4.4.1.jar`

없는 클래스 18개: `BossBar`(12), `SoundCategory`(10), `BarColor`(7), `Title$Builder`(6), `BarStyle`(5), `ChatMessageType`(3), `BarFlag`(3), `BookMeta`(3), `TranslationRegistry`(2), `Damageable`(2), `Title`(1), `UnsafeValues`(1), `CommandSender$Spigot`(1), `ProxiedCommandSender`(1), `Player$Spigot`(1), `BookMeta$Spigot`(1), `BaseComponent`, `Wither`

### `boosted-yaml-1.3.8.jar`

없는 클래스 없음.

### `bstats-base-3.2.1.jar`

없는 클래스 없음.

### `bstats-bukkit-3.2.1.jar`

없는 클래스 없음.

### `cloud-bukkit-2.0.1.jar`

없는 클래스 없음.

### `cloud-paper-2.0.1.jar`

없는 클래스 22개: `AsyncTabCompleteEvent`(5), `CommandRegisteredEvent`(4), `Commands`(3), `Registry`(3), `AsyncTabCompleteEvent$Completion`(2), `CommandSourceStack`(2), `MessageComponentSerializer`(2), `BootstrapContext`(2), `RegistryAccess`(2), `BukkitBrigadierCommandSource`(1), `PaperBrigadier`(1), `CommandRegistrationFlag`(1), `PluginMeta`(1), `LifecycleEventManager`(1), `ReloadableRegistrarEvent`(1), `LifecycleEvents`(1), `RegistryKey`(1), `EntityScheduler`(1), `GlobalRegionScheduler`(1), `RegionScheduler`(1), `LifecycleEventType`, `LifecycleEventType$Prioritizable`

### `commandapi-bukkit-core-12.1.0.jar`

없는 클래스 18개: `DisplaySlot`(7), `ProxiedCommandSender`(4), `Messenger`(4), `RemoteConsoleCommandSender`(2), `PlayerRegisterChannelEvent`(2), `ComponentSerializer`(1), `FluidCollisionMode`(1), `Biome`(1), `PluginMessageListener`(1), `Objective`(1), `Team`(1), `Sound`, `Advancement`, `BlockState`, `HelpTopic`, `Recipe`, `LootTable`, `PotionEffectType`

### `commandapi-bukkit-plugin-10.1.2.jar`

없는 클래스 36개: `DisplaySlot`(7), `GlobalRegionScheduler`(4), `ProxiedCommandSender`(4), `Messenger`(4), `Axis`(3), `Color`(3), `ComponentSerializer`(2), `Registry`(2), `RemoteConsoleCommandSender`(2), `PlayerRegisterChannelEvent`(2), `Scoreboard`(2), `AsyncScheduler`(1), `TextComponent`(1), `FluidCollisionMode`(1), `Particle`(1), `Particle$DustOptions`(1), `Particle$DustTransition`(1), `Particle$Trail`(1), `Sound`(1), `Vibration`(1), `Vibration$Destination$BlockDestination`(1), `Biome`(1), `HelpTopic`(1), `Recipe`(1), `Permission`(1), `PotionEffectType`(1), `Objective`(1), `ScoreboardManager`(1), `Team`(1), `PluginMeta` …

### `commodore-2.2.jar`

없는 클래스 2개: `AsyncPlayerSendCommandsEvent`(4), `PlayerCommandSendEvent`(2)

### `fastboard-2.2.2.jar`

없는 클래스 없음.

### `helper-5.6.14.jar`

없는 클래스 37개: `ArmorStand`(18), `BossBar`(14), `BarColor`(9), `Pig`(9), `BarStyle`(7), `InventoryClickEvent`(5), `DisplaySlot`(5), `Messenger`(4), `BukkitObjectInputStream`(4), `BukkitObjectOutputStream`(4), `EntityDamageByEntityEvent`(3), `AsyncPlayerPreLoginEvent`(3), `PlayerInteractEntityEvent`(3), `BaseComponent`(2), `ConfigurationSerialization`(2), `Player$Spigot`(2), `PigZapEvent`(2), `ClickType`(2), `InventoryCloseEvent`(2), `InventoryDragEvent`(2), `InventoryOpenEvent`(2), `AsyncPlayerPreLoginEvent$Result`(2), `PlayerFishEvent`(2), `ChatMessageType`(1), `ComponentSerializer`(1), `CreatureSpawnEvent$SpawnReason`(1), `PlayerDeathEvent`(1), `PlayerLoginEvent`(1), `PlayerLoginEvent$Result`(1), `LeatherArmorMeta`(1) …

