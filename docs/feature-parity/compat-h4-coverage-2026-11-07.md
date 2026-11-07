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
| `helper-5.6.14.jar` | LOADED_NOT_ENABLED | `java.lang.NoSuchMethodError: 'org.bukkit.plugin.java.JavaPlugin org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(java.lang.Class)'` |

상태: `NOT_LOADED`(로드 자체 실패) < `LOADED_NOT_ENABLED`(onLoad·onEnable 이 던짐) < `ENABLED_THEN_CRASHED` < `ENABLED`.

## 1. jar 별 Bukkit 계열 API(Bukkit·Paper·Spigot) 충족

| jar | 종류 | 클래스 수 | 요구 심볼 | 없음 | 충족 |
|---|---|---:|---:|---:|---:|
| `IF-0.12.2.jar` | 라이브러리 | 1042 | 191 | 98 | 48.7% |
| `XSeries-14.0.0.jar` | 라이브러리 | 332 | 946 | 699 | 26.1% |
| `adventure-platform-bukkit-4.4.1.jar` | 라이브러리 | 65 | 172 | 97 | 43.6% |
| `boosted-yaml-1.3.8.jar` | 라이브러리 | 292 | 0 | 0 | 100.0% |
| `bstats-base-3.2.1.jar` | 라이브러리 | 13 | 0 | 0 | 100.0% |
| `bstats-bukkit-3.2.1.jar` | 라이브러리 | 1 | 28 | 4 | 85.7% |
| `cloud-bukkit-2.0.1.jar` | 라이브러리 | 111 | 93 | 19 | 79.6% |
| `cloud-paper-2.0.1.jar` | 라이브러리 | 38 | 103 | 72 | 30.1% |
| `commandapi-bukkit-core-12.1.0.jar` | 라이브러리 | 155 | 144 | 62 | 56.9% |
| `commandapi-bukkit-plugin-10.1.2.jar` | 플러그인 | 308 | 267 | 131 | 50.9% |
| `commodore-2.2.jar` | 라이브러리 | 12 | 26 | 15 | 42.3% |
| `fastboard-2.2.2.jar` | 라이브러리 | 8 | 13 | 0 | 100.0% |
| `helper-5.6.14.jar` | 플러그인 | 1197 | 428 | 228 | 46.7% |

## 2. 종류별 요구와 빠짐 (전체 jar 합집합)

| 종류 | 요구 클래스 | 없는 클래스 | 요구 멤버(있는 클래스 기준) | 없는 멤버 | 없는 클래스의 멤버 호출 |
|---|---:|---:|---:|---:|---:|
| Bukkit API | 291 | 224 | 610 | 177 | 791 |
| Paper API | 25 | 25 | 0 | 0 | 49 |
| Spigot·BungeeChat | 6 | 6 | 0 | 0 | 19 |
| CraftBukkit | 358 | 358 | 0 | 0 | 429 |
| net.minecraft·authlib | 245 | 245 | 0 | 0 | 828 |
| Adventure(Paper 제공) | 80 | 80 | 0 | 0 | 153 |
| 서버 제공 라이브러리(Guava·Gson·SnakeYAML 등) | 126 | 103 | 125 | 0 | 399 |

## 3. 없는 Bukkit 계열 클래스 순위 (요구하는 jar 수 → 멤버 호출 수)

전체 255개 중 위 80개. 이 순서가 다음 묶음의 우선순위 후보다.

| 클래스 | jar 수 | 멤버 호출(합) |
|---|---:|---:|
| `org.bukkit.scheduler.BukkitScheduler` | 6 | 13 |
| `org.bukkit.command.Command` | 5 | 16 |
| `org.bukkit.Registry` | 4 | 19 |
| `org.bukkit.command.PluginCommand` | 4 | 10 |
| `org.bukkit.command.BlockCommandSender` | 4 | 7 |
| `org.bukkit.potion.PotionEffectType` | 4 | 7 |
| `org.bukkit.command.CommandMap` | 4 | 6 |
| `net.md_5.bungee.chat.ComponentSerializer` | 4 | 5 |
| `net.md_5.bungee.api.chat.BaseComponent` | 4 | 4 |
| `org.bukkit.command.SimpleCommandMap` | 4 | 2 |
| `org.bukkit.boss.BossBar` | 3 | 31 |
| `org.bukkit.scoreboard.DisplaySlot` | 3 | 19 |
| `org.bukkit.boss.BarColor` | 3 | 16 |
| `org.bukkit.boss.BarStyle` | 3 | 12 |
| `org.bukkit.plugin.messaging.Messenger` | 3 | 12 |
| `net.md_5.bungee.api.ChatMessageType` | 3 | 5 |
| `org.bukkit.command.ProxiedCommandSender` | 3 | 5 |
| `org.bukkit.entity.Player$Spigot` | 3 | 5 |
| `org.bukkit.boss.BarFlag` | 3 | 4 |
| `org.bukkit.block.Biome` | 3 | 2 |
| `org.bukkit.Sound` | 3 | 1 |
| `org.bukkit.plugin.messaging.PluginMessageListener` | 3 | 0 |
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
| `org.bukkit.command.PluginIdentifiableCommand` | 2 | 2 |
| `org.bukkit.inventory.meta.SkullMeta` | 2 | 2 |
| `org.bukkit.profile.PlayerTextures` | 2 | 2 |
| `org.bukkit.scheduler.BukkitRunnable` | 2 | 2 |
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

### 패키지별 (없는 클래스 수, 그 클래스들의 멤버 호출 합)

| 패키지 | 없는 클래스 | 멤버 호출 |
|---|---:|---:|
| `org.bukkit` | 27 | 169 |
| `org.bukkit.entity` | 62 | 118 |
| `org.bukkit.inventory.meta` | 18 | 72 |
| `org.bukkit.boss` | 4 | 63 |
| `org.bukkit.command` | 12 | 53 |
| `org.bukkit.inventory` | 7 | 45 |
| `org.bukkit.event.inventory` | 9 | 37 |
| `org.bukkit.scoreboard` | 5 | 26 |
| `org.bukkit.block` | 7 | 25 |
| `org.bukkit.event.player` | 9 | 20 |
| `org.bukkit.potion` | 4 | 20 |
| `org.bukkit.map` | 2 | 16 |
| `org.bukkit.scheduler` | 3 | 16 |
| `org.bukkit.material` | 8 | 15 |
| `org.bukkit.block.data` | 7 | 14 |
| `org.bukkit.plugin.messaging` | 2 | 12 |
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
| `org.bukkit.plugin` | 4 | 6 |
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
| `org.bukkit.event.server` | 3 | 1 |
| `org.bukkit.help` | 1 | 1 |
| `org.bukkit.metadata` | 1 | 1 |
| `org.bukkit.permissions` | 1 | 1 |
| `org.bukkit.util` | 2 | 1 |
| `org.bukkit.advancement` | 1 | 0 |
| `org.bukkit.loot` | 1 | 0 |

## 4. 클래스는 있는데 없는 멤버 순위

전체 177개 중 위 120개 (요구하는 jar 수 순).

| 멤버 | jar 수 |
|---|---:|
| `org.bukkit.Bukkit#getScheduler()Lorg/bukkit/scheduler/BukkitScheduler;` | 4 |
| `org.bukkit.entity.Player#updateCommands()V` | 4 |
| `org.bukkit.Server#getMessenger()Lorg/bukkit/plugin/messaging/Messenger;` | 3 |
| `org.bukkit.Server#getScheduler()Lorg/bukkit/scheduler/BukkitScheduler;` | 3 |
| `org.bukkit.entity.Player#sendPluginMessage(Lorg/bukkit/plugin/Plugin;Ljava/lang/String;[B)V` | 3 |
| `org.bukkit.entity.Player#spigot()Lorg/bukkit/entity/Player$Spigot;` | 3 |
| `org.bukkit.util.Vector#multiply(I)Lorg/bukkit/util/Vector;` | 3 |
| `org.bukkit.Bukkit#addRecipe(Lorg/bukkit/inventory/Recipe;)Z` | 2 |
| `org.bukkit.Bukkit#getOnlineMode()Z` | 2 |
| `org.bukkit.Bukkit#getPluginCommand(Ljava/lang/String;)Lorg/bukkit/command/PluginCommand;` | 2 |
| `org.bukkit.Server#getGlobalRegionScheduler()Lio/papermc/paper/threadedregions/scheduler/GlobalRegionScheduler;` | 2 |
| `org.bukkit.Server#isPrimaryThread()Z` | 2 |
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
| `org.bukkit.plugin.java.JavaPlugin#getCommand(Ljava/lang/String;)Lorg/bukkit/command/PluginCommand;` | 2 |
| `org.bukkit.plugin.java.JavaPlugin#getProvidingPlugin(Ljava/lang/Class;)Lorg/bukkit/plugin/java/JavaPlugin;` | 2 |
| `org.bukkit.util.Vector#toLocation(Lorg/bukkit/World;)Lorg/bukkit/Location;` | 2 |
| `org.bukkit.Bukkit#createBossBar(Ljava/lang/String;Lorg/bukkit/boss/BarColor;Lorg/bukkit/boss/BarStyle;[Lorg/bukkit/boss/BarFlag;)Lorg/bukkit/boss/BossBar;` | 1 |
| `org.bukkit.Bukkit#createInventory(Lorg/bukkit/inventory/InventoryHolder;ILnet/kyori/adventure/text/Component;)Lorg/bukkit/inventory/Inventory;` | 1 |
| `org.bukkit.Bukkit#createInventory(Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;Ljava/lang/String;)Lorg/bukkit/inventory/Inventory;` | 1 |
| `org.bukkit.Bukkit#createInventory(Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;Lnet/kyori/adventure/text/Component;)Lorg/bukkit/inventory/Inventory;` | 1 |
| `org.bukkit.Bukkit#createMap(Lorg/bukkit/World;)Lorg/bukkit/map/MapView;` | 1 |
| `org.bukkit.Bukkit#createMerchant(Ljava/lang/String;)Lorg/bukkit/inventory/Merchant;` | 1 |
| `org.bukkit.Bukkit#createMerchant(Lnet/kyori/adventure/text/Component;)Lorg/bukkit/inventory/Merchant;` | 1 |
| `org.bukkit.Bukkit#createPlayerProfile(Ljava/util/UUID;)Lorg/bukkit/profile/PlayerProfile;` | 1 |
| `org.bukkit.Bukkit#createWorldBorder()Lorg/bukkit/WorldBorder;` | 1 |
| `org.bukkit.Bukkit#getEntity(Ljava/util/UUID;)Lorg/bukkit/entity/Entity;` | 1 |
| `org.bukkit.Bukkit#getHelpMap()Lorg/bukkit/help/HelpMap;` | 1 |
| `org.bukkit.Bukkit#getItemFactory()Lorg/bukkit/inventory/ItemFactory;` | 1 |
| `org.bukkit.Bukkit#getOfflinePlayer(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;` | 1 |
| `org.bukkit.Bukkit#getOfflinePlayer(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;` | 1 |
| `org.bukkit.Bukkit#getOfflinePlayers()[Lorg/bukkit/OfflinePlayer;` | 1 |
| `org.bukkit.Bukkit#getScoreboardManager()Lorg/bukkit/scoreboard/ScoreboardManager;` | 1 |
| `org.bukkit.Bukkit#getServicesManager()Lorg/bukkit/plugin/ServicesManager;` | 1 |
| `org.bukkit.Bukkit#getUnsafe()Lorg/bukkit/UnsafeValues;` | 1 |
| `org.bukkit.Bukkit#getWorld(Lorg/bukkit/NamespacedKey;)Lorg/bukkit/World;` | 1 |
| `org.bukkit.Bukkit#hasWhitelist()Z` | 1 |
| `org.bukkit.Bukkit#isPrimaryThread()Z` | 1 |
| `org.bukkit.Bukkit#recipeIterator()Ljava/util/Iterator;` | 1 |
| `org.bukkit.Chunk#getBlock(III)Lorg/bukkit/block/Block;` | 1 |
| `org.bukkit.Chunk#load()Z` | 1 |
| `org.bukkit.Chunk#load(Z)Z` | 1 |
| `org.bukkit.Location#getChunk()Lorg/bukkit/Chunk;` | 1 |
| `org.bukkit.Material#WALL_SIGN` | 1 |
| `org.bukkit.Material#getId()I` | 1 |
| `org.bukkit.Material#getNewData(B)Lorg/bukkit/material/MaterialData;` | 1 |
| `org.bukkit.Material#isInteractable()Z` | 1 |
| `org.bukkit.NamespacedKey#asString()Ljava/lang/String;` | 1 |
| `org.bukkit.OfflinePlayer#getLocation()Lorg/bukkit/Location;` | 1 |
| `org.bukkit.Server#createBossBar(Ljava/lang/String;Lorg/bukkit/boss/BarColor;Lorg/bukkit/boss/BarStyle;[Lorg/bukkit/boss/BarFlag;)Lorg/bukkit/boss/BossBar;` | 1 |
| `org.bukkit.Server#getAsyncScheduler()Lio/papermc/paper/threadedregions/scheduler/AsyncScheduler;` | 1 |
| `org.bukkit.Server#getCommandMap()Lorg/bukkit/command/CommandMap;` | 1 |
| `org.bukkit.Server#getHelpMap()Lorg/bukkit/help/HelpMap;` | 1 |
| `org.bukkit.Server#getOfflinePlayer(Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;` | 1 |
| `org.bukkit.Server#getOfflinePlayer(Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer;` | 1 |
| `org.bukkit.Server#getRegionScheduler()Lio/papermc/paper/threadedregions/scheduler/RegionScheduler;` | 1 |
| `org.bukkit.Server#getServicesManager()Lorg/bukkit/plugin/ServicesManager;` | 1 |
| `org.bukkit.World#dropItemNaturally(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;` | 1 |
| `org.bukkit.World#getGameRuleValue(Lorg/bukkit/GameRule;)Ljava/lang/Object;` | 1 |
| `org.bukkit.World#getSpawnLocation()Lorg/bukkit/Location;` | 1 |
| `org.bukkit.World#getWorldBorder()Lorg/bukkit/WorldBorder;` | 1 |
| `org.bukkit.World#isGameRule(Ljava/lang/String;)Z` | 1 |
| `org.bukkit.World#playEffect(Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V` | 1 |
| `org.bukkit.World#setGameRule(Lorg/bukkit/GameRule;Ljava/lang/Object;)Z` | 1 |
| `org.bukkit.World#spawn(Lorg/bukkit/Location;Ljava/lang/Class;Lorg/bukkit/event/entity/CreatureSpawnEvent$SpawnReason;ZLjava/util/function/Consumer;)Lorg/bukkit/entity/LivingEntity;` | 1 |
| `org.bukkit.World#spawn(Lorg/bukkit/Location;Ljava/lang/Class;Lorg/bukkit/util/Consumer;)Lorg/bukkit/entity/Entity;` | 1 |
| `org.bukkit.World#spawn(Lorg/bukkit/Location;Ljava/lang/Class;ZLjava/util/function/Consumer;)Lorg/bukkit/entity/Entity;` | 1 |
| `org.bukkit.World#spawnParticle(Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V` | 1 |
| `org.bukkit.World#spawnParticle(Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V` | 1 |
| `org.bukkit.World#spawnParticle(Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V` | 1 |
| `org.bukkit.World#spawnParticle(Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V` | 1 |
| `org.bukkit.block.Block#breakNaturally()Z` | 1 |
| `org.bukkit.block.Block#getBiome()Lorg/bukkit/block/Biome;` | 1 |
| `org.bukkit.block.Block#getData()B` | 1 |
| `org.bukkit.block.Block#getState()Lorg/bukkit/block/BlockState;` | 1 |
| `org.bukkit.block.Block#setBiome(Lorg/bukkit/block/Biome;)V` | 1 |
| `org.bukkit.command.CommandSender#sendMessage(Lnet/kyori/adventure/text/Component;)V` | 1 |
| `org.bukkit.command.CommandSender#spigot()Lorg/bukkit/command/CommandSender$Spigot;` | 1 |
| `org.bukkit.enchantments.Enchantment#getByName(Ljava/lang/String;)Lorg/bukkit/enchantments/Enchantment;` | 1 |
| `org.bukkit.enchantments.Enchantment#getName()Ljava/lang/String;` | 1 |
| `org.bukkit.enchantments.Enchantment#values()[Lorg/bukkit/enchantments/Enchantment;` | 1 |
| `org.bukkit.entity.Entity#getEntityId()I` | 1 |
| `org.bukkit.entity.Entity#getScheduler()Lio/papermc/paper/threadedregions/scheduler/EntityScheduler;` | 1 |
| `org.bukkit.entity.Entity#setCustomNameVisible(Z)V` | 1 |
| `org.bukkit.entity.Entity#setFallDistance(F)V` | 1 |
| `org.bukkit.entity.Entity#setFireTicks(I)V` | 1 |
| `org.bukkit.entity.Entity#setGlowing(Z)V` | 1 |
| `org.bukkit.entity.Entity#setGravity(Z)V` | 1 |
| `org.bukkit.entity.Entity#setInvulnerable(Z)V` | 1 |
| `org.bukkit.entity.Entity#setPortalCooldown(I)V` | 1 |
| `org.bukkit.entity.Entity#setSilent(Z)V` | 1 |
| `org.bukkit.entity.Entity#setTicksLived(I)V` | 1 |
| `org.bukkit.entity.EntityType#fromName(Ljava/lang/String;)Lorg/bukkit/entity/EntityType;` | 1 |
| `org.bukkit.entity.EntityType#getEntityClass()Ljava/lang/Class;` | 1 |
| `org.bukkit.entity.EntityType#getName()Ljava/lang/String;` | 1 |
| `org.bukkit.entity.HumanEntity#closeInventory()V` | 1 |
| `org.bukkit.entity.HumanEntity#getItemOnCursor()Lorg/bukkit/inventory/ItemStack;` | 1 |
| `org.bukkit.entity.HumanEntity#getOpenInventory()Lorg/bukkit/inventory/InventoryView;` | 1 |
| `org.bukkit.entity.HumanEntity#openInventory(Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;` | 1 |
| `org.bukkit.entity.HumanEntity#setItemOnCursor(Lorg/bukkit/inventory/ItemStack;)V` | 1 |
| `org.bukkit.entity.LivingEntity#addPotionEffect(Lorg/bukkit/potion/PotionEffect;)Z` | 1 |
| `org.bukkit.entity.LivingEntity#getAttribute(Lorg/bukkit/attribute/Attribute;)Lorg/bukkit/attribute/AttributeInstance;` | 1 |
| `org.bukkit.entity.LivingEntity#getEntityId()I` | 1 |
| `org.bukkit.entity.LivingEntity#getEquipment()Lorg/bukkit/inventory/EntityEquipment;` | 1 |
| `org.bukkit.entity.LivingEntity#getEyeLocation()Lorg/bukkit/Location;` | 1 |
| `org.bukkit.entity.LivingEntity#launchProjectile(Ljava/lang/Class;)Lorg/bukkit/entity/Projectile;` | 1 |
| `org.bukkit.entity.LivingEntity#setAI(Z)V` | 1 |

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

없는 클래스 20개: `MerchantRecipe`(11), `InventoryClickEvent`(9), `InventoryView`(7), `InventoryDragEvent`(6), `Item`(3), `EntityPickupItemEvent`(3), `Registry`(2), `ClickType`(2), `InventoryAction`(2), `InventoryCloseEvent`(2), `PlayerDropItemEvent`(2), `SkullMeta`(2), `PlayerProfile`(2), `DragType`(1), `InventoryOpenEvent`(1), `TradeSelectEvent`(1), `PlayerTextures`(1), `BukkitScheduler`(1), `ChatColor`, `InventoryType$SlotType`

### `XSeries-14.0.0.jar`

없는 클래스 159개: `DyeColor`(21), `Instrument`(18), `WorldBorder`(15), `MapView`(14), `Registry`(12), `EntityEquipment`(12), `InventoryView`(12), `MapMeta`(11), `Color`(10), `TreeSpecies`(10), `PotionMeta`(10), `SkullType`(9), `Note$Tone`(8), `BlockState`(8), `BookMeta`(8), `CustomModelDataComponent`(8), `FireworkEffect`(6), `FireworkEffect$Builder`(6), `AttributeModifier`(6), `TropicalFishBucketMeta`(6), `Art`(5), `Skull`(5), `BossBar`(5), `Ageable`(5), `CompassMeta`(5), `PotionEffectType`(5), `Note`(4), `Banner`(4), `AbstractHorse`(4), `Bee`(4) …

### `adventure-platform-bukkit-4.4.1.jar`

없는 클래스 18개: `BossBar`(12), `SoundCategory`(10), `BarColor`(7), `Title$Builder`(6), `BarStyle`(5), `ChatMessageType`(3), `BarFlag`(3), `BookMeta`(3), `TranslationRegistry`(2), `Damageable`(2), `Title`(1), `UnsafeValues`(1), `CommandSender$Spigot`(1), `ProxiedCommandSender`(1), `Player$Spigot`(1), `BookMeta$Spigot`(1), `BaseComponent`, `Wither`

### `boosted-yaml-1.3.8.jar`

없는 클래스 없음.

### `bstats-base-3.2.1.jar`

없는 클래스 없음.

### `bstats-bukkit-3.2.1.jar`

없는 클래스 1개: `BukkitScheduler`(1)

### `cloud-bukkit-2.0.1.jar`

없는 클래스 6개: `Command`(2), `BlockCommandSender`(1), `CommandMap`(1), `PluginIdentifiableCommand`(1), `BukkitScheduler`(1), `SimpleCommandMap`

### `cloud-paper-2.0.1.jar`

없는 클래스 24개: `AsyncTabCompleteEvent`(5), `CommandRegisteredEvent`(4), `Commands`(3), `Registry`(3), `AsyncTabCompleteEvent$Completion`(2), `CommandSourceStack`(2), `MessageComponentSerializer`(2), `BootstrapContext`(2), `RegistryAccess`(2), `BlockCommandSender`(2), `BukkitBrigadierCommandSource`(1), `PaperBrigadier`(1), `CommandRegistrationFlag`(1), `PluginMeta`(1), `LifecycleEventManager`(1), `ReloadableRegistrarEvent`(1), `LifecycleEvents`(1), `RegistryKey`(1), `EntityScheduler`(1), `GlobalRegionScheduler`(1), `RegionScheduler`(1), `PluginIdentifiableCommand`(1), `LifecycleEventType`, `LifecycleEventType$Prioritizable`

### `commandapi-bukkit-core-12.1.0.jar`

없는 클래스 23개: `DisplaySlot`(7), `Messenger`(4), `Command`(3), `BlockCommandSender`(2), `CommandMap`(2), `ProxiedCommandSender`(2), `RemoteConsoleCommandSender`(2), `PlayerRegisterChannelEvent`(2), `ComponentSerializer`(1), `FluidCollisionMode`(1), `Biome`(1), `PluginCommand`(1), `SimpleCommandMap`(1), `Objective`(1), `Team`(1), `Sound`, `Advancement`, `BlockState`, `HelpTopic`, `Recipe`, `LootTable`, `PluginMessageListener`, `PotionEffectType`

### `commandapi-bukkit-plugin-10.1.2.jar`

없는 클래스 43개: `DisplaySlot`(7), `Command`(5), `BukkitScheduler`(5), `GlobalRegionScheduler`(4), `Messenger`(4), `Axis`(3), `Color`(3), `ComponentSerializer`(2), `Registry`(2), `BlockCommandSender`(2), `CommandMap`(2), `ProxiedCommandSender`(2), `RemoteConsoleCommandSender`(2), `PlayerRegisterChannelEvent`(2), `Scoreboard`(2), `AsyncScheduler`(1), `TextComponent`(1), `FluidCollisionMode`(1), `Particle`(1), `Particle$DustOptions`(1), `Particle$DustTransition`(1), `Particle$Trail`(1), `Sound`(1), `Vibration`(1), `Vibration$Destination$BlockDestination`(1), `Biome`(1), `PluginCommand`(1), `SimpleCommandMap`(1), `HelpTopic`(1), `Recipe`(1) …

### `commodore-2.2.jar`

없는 클래스 4개: `AsyncPlayerSendCommandsEvent`(4), `Command`(4), `PlayerCommandSendEvent`(2), `PluginCommand`(1)

### `fastboard-2.2.2.jar`

없는 클래스 없음.

### `helper-5.6.14.jar`

없는 클래스 52개: `ArmorStand`(18), `BossBar`(14), `BarColor`(9), `Pig`(9), `BarStyle`(7), `PluginCommand`(7), `InventoryClickEvent`(5), `DisplaySlot`(5), `Messenger`(4), `BukkitScheduler`(4), `BukkitObjectInputStream`(4), `BukkitObjectOutputStream`(4), `EntityDamageByEntityEvent`(3), `AsyncPlayerPreLoginEvent`(3), `PlayerInteractEntityEvent`(3), `BaseComponent`(2), `Command`(2), `ConfigurationSerialization`(2), `Player$Spigot`(2), `PigZapEvent`(2), `ClickType`(2), `InventoryCloseEvent`(2), `InventoryDragEvent`(2), `InventoryOpenEvent`(2), `AsyncPlayerPreLoginEvent$Result`(2), `PlayerFishEvent`(2), `RegisteredServiceProvider`(2), `ServicePriority`(2), `ServicesManager`(2), `ChatMessageType`(1) …

