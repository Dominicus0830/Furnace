# NMS·CraftBukkit 호출 빈도 표 (H5, 생성 파일)

`./gradlew :furnace-compat-paper:nmsReport` 가 만든 파일이다. 직접 고치지 않는다. 해석과 설계는 [`compat-h5-2026-11-07.md`](compat-h5-2026-11-07.md).

실제 Bukkit·Paper 플러그인과 플러그인 라이브러리 jar(Maven Central 에 있는 것만)의 클래스 파일 상수 풀에서 `net.minecraft.*`,
`org.bukkit.craftbukkit.*`, `com.mojang.*`, 그리고 NMS 접근에 따라붙는 리플렉션 호출·문자열 리터럴을 읽어 센 값이다.
**정적 분석이라 요구 상한**이다(안 타는 코드 경로도 센다). "멤버 참조"는 jar 마다 서로 다른 `owner#이름`(오버로드는 합침)의 수이지 실행 시 호출 횟수가 아니다.
버전 접미사 패키지(`v1_21_R1`)는 지우고 합쳤다(같은 클래스를 버전별 모듈이 되풀이해 부르는 것은 한 번으로 센다).
리플렉션 문자열로 조립한 이름은 문자열 조각만 보이므로 **하한**이다. 이름 체계(Spigot 난독화 / Mojang)는 멤버 이름이 1~2글자인지,
클래스 이름이 `$a` 꼴인지, 알려진 이름 모양인지로 어림한 값이다(어림 규칙은 `NmsScan` 소스).

## 0. 표본 (jar 별 요약)

| jar | 종류 | 클래스 | CraftBukkit(클래스/멤버) | NMS(클래스/멤버) | 구 NMS 패키지(클래스) | 버전 접미사 | 이름 체계 어림 | 리플렉션 호출 | NMS 이름 리터럴 | 매니페스트 매핑 |
|---|---|---:|---:|---:|---:|---|---|---:|---:|---|
| `IF-0.12.2.jar` | 라이브러리 | 1042 | 19/20 | 68/214 | 33 | v1_16_R3…v1_21_R7 (15개) | Spigot(난독화) | 25 | 0 | — |
| `ParticleNativeAPI-core-4.5.1.jar` | 라이브러리 | 176 | 0/0 | 0/0 | 0 | — | — | 30 | 68 | — |
| `XSeries-14.0.0.jar` | 라이브러리 | 332 | 0/0 | 0/0 | 0 | — | — | 137 | 4 | — |
| `adventure-platform-bukkit-4.4.1.jar` | 라이브러리 | 65 | 0/0 | 0/0 | 0 | — | — | 49 | 4 | — |
| `boosted-yaml-1.3.8.jar` | 라이브러리 | 292 | 0/0 | 0/0 | 0 | — | — | 0 | 0 | — |
| `bstats-base-3.2.1.jar` | 라이브러리 | 13 | 0/0 | 0/0 | 0 | — | — | 0 | 0 | — |
| `bstats-bukkit-3.2.1.jar` | 라이브러리 | 1 | 0/0 | 0/0 | 0 | — | — | 3 | 0 | — |
| `cloud-bukkit-2.0.1.jar` | 라이브러리 | 111 | 0/0 | 0/0 | 0 | — | — | 75 | 16 | — |
| `cloud-paper-2.0.1.jar` | 라이브러리 | 38 | 0/0 | 0/0 | 0 | — | — | 8 | 0 | — |
| `commandapi-bukkit-core-12.1.0.jar` | 라이브러리 | 155 | 0/0 | 0/0 | 0 | — | — | 2 | 0 | — |
| `commandapi-bukkit-plugin-10.1.2.jar` | 플러그인 | 308 | 14/26 | 144/347 | 0 | v1_20_R1…v1_21_R5 (9개) | Spigot(난독화) | 45 | 1 | — |
| `commandapi-bukkit-plugin-mojang-mapped-10.1.2.jar` | 플러그인 | 308 | 14/26 | 142/301 | 0 | v1_20_R1…v1_20_R3 (3개) | Mojang | 45 | 1 | `mojang` |
| `commodore-2.2.jar` | 라이브러리 | 12 | 0/0 | 0/0 | 0 | — | — | 14 | 5 | — |
| `fastboard-2.2.2.jar` | 라이브러리 | 8 | 0/0 | 0/0 | 0 | — | — | 18 | 5 | — |
| `glowingentities-2.0.2.jar` | 라이브러리 | 13 | 0/0 | 0/0 | 0 | — | — | 11 | 2 | — |
| `helper-5.6.14.jar` | 플러그인 | 1197 | 0/0 | 0/0 | 0 | v1_10_R1…v1_9_R2 (26개) | — | 117 | 6 | — |
| `reflection-remapper-0.1.3.jar` | 라이브러리 | 19 | 0/0 | 0/0 | 0 | — | — | 15 | 1 | — |
| `scoreboard-library-implementation-2.8.3.jar` | 라이브러리 | 108 | 0/0 | 0/0 | 0 | — | — | 24 | 52 | — |
| `signgui-2.5.4.jar` | 라이브러리 | 89 | 1/1 | 24/68 | 11 | v1_10_R1…v1_9_R2 (30개) | Spigot(난독화) | 48 | 0 | — |

서버 내부(NMS·CraftBukkit)에 닿는 jar: 14 / 19개.

## 1. 버전 접미사(`v1_X_RN`) 분포

| 접미사 | jar 수 | jar |
|---|---:|---|
| `v1_10_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_11_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_12_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_13_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_13_R2` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_14_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_15_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_16_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_16_R2` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_16_R3` | 3 | `IF-0.12.2.jar`, `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_17_R1` | 3 | `IF-0.12.2.jar`, `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_18_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_18_R2` | 3 | `IF-0.12.2.jar`, `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_19_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_19_R2` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_19_R3` | 3 | `IF-0.12.2.jar`, `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_20_R1` | 5 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `commandapi-bukkit-plugin-mojang-mapped-10.1.2.jar`, `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_20_R2` | 4 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `commandapi-bukkit-plugin-mojang-mapped-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_20_R3` | 4 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `commandapi-bukkit-plugin-mojang-mapped-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_20_R4` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R1` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R2` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R3` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R4` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R5` | 3 | `IF-0.12.2.jar`, `commandapi-bukkit-plugin-10.1.2.jar`, `signgui-2.5.4.jar` |
| `v1_21_R6` | 1 | `IF-0.12.2.jar` |
| `v1_21_R7` | 1 | `IF-0.12.2.jar` |
| `v1_7_R1` | 1 | `helper-5.6.14.jar` |
| `v1_7_R2` | 1 | `helper-5.6.14.jar` |
| `v1_7_R3` | 1 | `helper-5.6.14.jar` |
| `v1_7_R4` | 1 | `helper-5.6.14.jar` |
| `v1_8_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_8_R2` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_8_R3` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_9_R1` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |
| `v1_9_R2` | 2 | `helper-5.6.14.jar`, `signgui-2.5.4.jar` |

`Class#getPackage` + `Package#getName` 을 함께 부르고 서버 내부에 닿는 jar(패키지 이름에서 버전 접미사를 읽는 옛 방식의 후보): 8개.

## 2. CraftBukkit 클래스 (접미사 제거, jar 수 → 멤버 참조 수)

서로 다른 클래스 32개 중 위 32개.

| 클래스 | jar 수 | 멤버 참조 |
|---|---:|---:|
| `org.bukkit.craftbukkit.inventory.CraftItemStack` | 3 | 7 |
| `org.bukkit.craftbukkit.CraftParticle` | 2 | 8 |
| `org.bukkit.craftbukkit.CraftServer` | 2 | 6 |
| `org.bukkit.craftbukkit.help.SimpleHelpMap` | 2 | 6 |
| `org.bukkit.craftbukkit.CraftLootTable` | 2 | 4 |
| `org.bukkit.craftbukkit.CraftSound` | 2 | 4 |
| `org.bukkit.craftbukkit.command.VanillaCommandWrapper` | 2 | 4 |
| `org.bukkit.craftbukkit.potion.CraftPotionEffectType` | 2 | 4 |
| `org.bukkit.craftbukkit.CraftWorld` | 2 | 2 |
| `org.bukkit.craftbukkit.block.data.CraftBlockData` | 2 | 2 |
| `org.bukkit.craftbukkit.command.ProxiedNativeCommandSender` | 2 | 2 |
| `org.bukkit.craftbukkit.entity.CraftEntity` | 2 | 2 |
| `org.bukkit.craftbukkit.entity.CraftPlayer` | 2 | 2 |
| `org.bukkit.craftbukkit.help.CustomHelpTopic` | 2 | 2 |
| `org.bukkit.craftbukkit.command.BukkitCommandWrapper` | 2 | 0 |
| `org.bukkit.craftbukkit.inventory.view.CraftAnvilView` | 1 | 2 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryAnvil` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryBeacon` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryCartography` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryLoom` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryMerchant` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventorySmithing` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventorySmithingNew` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryView` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftBeaconView` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftEnchantmentView` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftLoomView` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftMerchantView` | 1 | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftStonecutterView` | 1 | 1 |

## 3. CraftBukkit 멤버 참조 상위 (참조한 jar 수)

서로 다른 멤버 참조 45개 중 위 40개.

| 멤버 | jar 수 |
|---|---:|
| `org.bukkit.craftbukkit.inventory.CraftItemStack#asNMSCopy` | 3 |
| `org.bukkit.craftbukkit.CraftLootTable#<init>` | 2 |
| `org.bukkit.craftbukkit.CraftLootTable#minecraftToBukkit` | 2 |
| `org.bukkit.craftbukkit.CraftParticle#createParticleParam` | 2 |
| `org.bukkit.craftbukkit.CraftParticle#minecraftToBukkit` | 2 |
| `org.bukkit.craftbukkit.CraftParticle#toBukkit` | 2 |
| `org.bukkit.craftbukkit.CraftParticle#toNMS` | 2 |
| `org.bukkit.craftbukkit.CraftServer#getCommandMap` | 2 |
| `org.bukkit.craftbukkit.CraftServer#getServer` | 2 |
| `org.bukkit.craftbukkit.CraftServer#syncCommands` | 2 |
| `org.bukkit.craftbukkit.CraftSound#getBukkit` | 2 |
| `org.bukkit.craftbukkit.CraftSound#minecraftToBukkit` | 2 |
| `org.bukkit.craftbukkit.CraftWorld#getHandle` | 2 |
| `org.bukkit.craftbukkit.block.data.CraftBlockData#fromData` | 2 |
| `org.bukkit.craftbukkit.command.ProxiedNativeCommandSender#<init>` | 2 |
| `org.bukkit.craftbukkit.command.VanillaCommandWrapper#<init>` | 2 |
| `org.bukkit.craftbukkit.command.VanillaCommandWrapper#getListener` | 2 |
| `org.bukkit.craftbukkit.entity.CraftEntity#getHandle` | 2 |
| `org.bukkit.craftbukkit.entity.CraftPlayer#getHandle` | 2 |
| `org.bukkit.craftbukkit.help.CustomHelpTopic#<init>` | 2 |
| `org.bukkit.craftbukkit.help.SimpleHelpMap#clear` | 2 |
| `org.bukkit.craftbukkit.help.SimpleHelpMap#initializeCommands` | 2 |
| `org.bukkit.craftbukkit.help.SimpleHelpMap#initializeGeneralTopics` | 2 |
| `org.bukkit.craftbukkit.inventory.CraftItemStack#asBukkitCopy` | 2 |
| `org.bukkit.craftbukkit.inventory.CraftItemStack#getItemMeta` | 2 |
| `org.bukkit.craftbukkit.potion.CraftPotionEffectType#getByKey` | 2 |
| `org.bukkit.craftbukkit.potion.CraftPotionEffectType#minecraftToBukkit` | 2 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryAnvil#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryBeacon#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryCartography#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryEnchanting#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryGrindstone#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryLoom#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryMerchant#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventorySmithing#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventorySmithingNew#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryStonecutter#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.CraftInventoryView#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftAnvilView#<init>` | 1 |
| `org.bukkit.craftbukkit.inventory.view.CraftAnvilView#updateFromLegacy` | 1 |

## 4. net.minecraft 클래스 (jar 수 → 멤버 참조 수, 이름 모양)

서로 다른 클래스 314개 중 위 40개.

| 클래스 | jar 수 | 멤버 참조 | 이름 모양 |
|---|---:|---:|---|
| `net.minecraft.world.item.ItemStack` | 3 | 31 | 공통·불명 |
| `net.minecraft.server.level.EntityPlayer` | 3 | 12 | Spigot |
| `net.minecraft.core.BlockPosition` | 3 | 8 | Spigot |
| `net.minecraft.core.BlockPos` | 3 | 7 | Mojang |
| `net.minecraft.network.chat.ComponentSerialization` | 3 | 4 | 공통·불명 |
| `net.minecraft.server.level.ServerPlayer` | 3 | 4 | Mojang |
| `net.minecraft.network.chat.Component` | 3 | 3 | 공통·불명 |
| `net.minecraft.network.chat.IChatBaseComponent` | 3 | 2 | Spigot |
| `net.minecraft.core.HolderLookup` | 3 | 0 | 공통·불명 |
| `net.minecraft.server.MinecraftServer` | 2 | 56 | 공통·불명 |
| `net.minecraft.core.registries.Registries` | 2 | 42 | 공통·불명 |
| `net.minecraft.core.registries.BuiltInRegistries` | 2 | 13 | 공통·불명 |
| `net.minecraft.server.dedicated.DedicatedServer` | 2 | 10 | 공통·불명 |
| `net.minecraft.commands.arguments.ResourceArgument` | 2 | 9 | 공통·불명 |
| `net.minecraft.core.particles.DustColorTransitionOptions` | 2 | 9 | 공통·불명 |
| `net.minecraft.commands.arguments.selector.EntitySelector` | 2 | 8 | 공통·불명 |
| `net.minecraft.core.particles.ColorParticleOption` | 2 | 8 | 공통·불명 |
| `net.minecraft.commands.arguments.ResourceKeyArgument` | 2 | 6 | 공통·불명 |
| `net.minecraft.core.particles.TrailParticleOption` | 2 | 6 | 공통·불명 |
| `net.minecraft.server.MinecraftServer$ReloadableResources` | 2 | 6 | 공통·불명 |
| `net.minecraft.world.level.WorldDataConfiguration` | 2 | 6 | 공통·불명 |
| `net.minecraft.core.particles.VibrationParticleOption` | 2 | 5 | 공통·불명 |
| `net.minecraft.world.scores.ScoreHolder` | 2 | 5 | 공통·불명 |
| `net.minecraft.advancements.Advancement` | 2 | 4 | 공통·불명 |
| `net.minecraft.advancements.AdvancementHolder` | 2 | 4 | 공통·불명 |
| `net.minecraft.resources.ResourceKey` | 2 | 4 | 공통·불명 |
| `net.minecraft.world.item.crafting.RecipeHolder` | 2 | 4 | 공통·불명 |
| `net.minecraft.world.level.storage.loot.LootDataManager` | 2 | 4 | 공통·불명 |
| `net.minecraft.commands.CommandBuildContext` | 2 | 3 | 공통·불명 |
| `net.minecraft.commands.functions.CommandFunction` | 2 | 3 | 공통·불명 |
| `net.minecraft.core.HolderLookup$Provider` | 2 | 3 | 공통·불명 |
| `net.minecraft.core.component.PatchedDataComponentMap` | 2 | 3 | 공통·불명 |
| `net.minecraft.core.particles.SculkChargeParticleOptions` | 2 | 3 | 공통·불명 |
| `net.minecraft.core.particles.ShriekParticleOption` | 2 | 3 | 공통·불명 |
| `net.minecraft.commands.execution.ExecutionContext` | 2 | 2 | 공통·불명 |
| `net.minecraft.commands.functions.InstantiatedFunction` | 2 | 2 | 공통·불명 |
| `net.minecraft.commands.synchronization.ArgumentUtils` | 2 | 2 | 공통·불명 |
| `net.minecraft.core.HolderLookup$a` | 2 | 2 | 난독화(`$a`) |
| `net.minecraft.core.LayeredRegistryAccess` | 2 | 2 | 공통·불명 |
| `net.minecraft.network.chat.IChatBaseComponent$ChatSerializer` | 2 | 2 | Spigot |

## 5. net.minecraft 멤버 참조 상위 — 읽을 수 있는 이름 (참조한 jar 수)

서로 다른 멤버 참조 559개 중 위 40개.

| 멤버 | jar 수 |
|---|---:|
| `net.minecraft.advancements.Advancement#bukkit` | 2 |
| `net.minecraft.advancements.AdvancementHolder#toBukkit` | 2 |
| `net.minecraft.core.BlockPos#<init>` | 2 |
| `net.minecraft.core.BlockPosition#<init>` | 2 |
| `net.minecraft.network.chat.ComponentSerialization#CODEC` | 2 |
| `net.minecraft.server.MinecraftServer#server` | 2 |
| `net.minecraft.server.MinecraftServer#vanillaCommandDispatcher` | 2 |
| `net.minecraft.server.MinecraftServer$ReloadableResources#close` | 2 |
| `net.minecraft.world.entity.Entity#getBukkitEntity` | 2 |
| `net.minecraft.world.item.ItemStack#h` | 2 |
| `net.minecraft.world.item.crafting.RecipeHolder#toBukkitRecipe` | 2 |
| `net.minecraft.world.level.WorldDataConfiguration#<init>` | 2 |
| `net.minecraft.world.level.storage.loot.LootDataManager#getLootTable` | 2 |
| `net.minecraft.ChatFormatting#getChar` | 1 |
| `net.minecraft.ChatFormatting#getColor` | 1 |
| `net.minecraft.advancements.Advancement#getId` | 1 |
| `net.minecraft.advancements.AdvancementHolder#id` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Doubles#getMax` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Doubles#getMin` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Doubles#max` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Doubles#min` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Ints#getMax` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Ints#getMin` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Ints#max` | 1 |
| `net.minecraft.advancements.critereon.MinMaxBounds$Ints#min` | 1 |
| `net.minecraft.commands.CommandBuildContext#configurable` | 1 |
| `net.minecraft.commands.CommandBuildContext#simple` | 1 |
| `net.minecraft.commands.CommandFunction#getEntries` | 1 |
| `net.minecraft.commands.CommandFunction#getId` | 1 |
| `net.minecraft.commands.CommandFunction$Entry#toString` | 1 |
| `net.minecraft.commands.CommandListenerWrapper#getBukkitSender` | 1 |
| `net.minecraft.commands.CommandSourceStack#enabledFeatures` | 1 |
| `net.minecraft.commands.CommandSourceStack#getAvailableSounds` | 1 |
| `net.minecraft.commands.CommandSourceStack#getBukkitSender` | 1 |
| `net.minecraft.commands.CommandSourceStack#getEntity` | 1 |
| `net.minecraft.commands.CommandSourceStack#getLevel` | 1 |
| `net.minecraft.commands.CommandSourceStack#getPosition` | 1 |
| `net.minecraft.commands.CommandSourceStack#getRotation` | 1 |
| `net.minecraft.commands.CommandSourceStack#withEntity` | 1 |
| `net.minecraft.commands.CommandSourceStack#withLevel` | 1 |

1~2글자 난독화로 보이는 멤버 참조(Spigot 이름 체계, jar 별 서로 다른 것의 합): 358개. 소유 클래스별 상위 12개: `net.minecraft.server.MinecraftServer`(17), `net.minecraft.core.registries.Registries`(16), `net.minecraft.world.entity.player.EntityHuman`(14), `net.minecraft.world.item.ItemStack`(13), `net.minecraft.world.level.block.entity.TileEntitySign`(9), `net.minecraft.commands.CommandListenerWrapper`(8), `net.minecraft.core.registries.BuiltInRegistries`(8), `net.minecraft.core.IRegistry`(6), `net.minecraft.network.protocol.game.PacketPlayInUpdateSign`(6), `net.minecraft.server.DataPackResources`(6), `net.minecraft.server.level.EntityPlayer`(6), `net.minecraft.world.inventory.ContainerAnvil`(6)

## 6. 구 NMS 패키지 `net.minecraft.server.v1_X_RN.*` 클래스

서로 다른 클래스 40개 중 위 40개.

| 클래스 | jar 수 | 멤버 참조 | 이름 모양 |
|---|---:|---:|---|
| `net.minecraft.server.EntityPlayer` | 2 | 4 | Spigot |
| `net.minecraft.server.BlockPosition` | 2 | 3 | Spigot |
| `net.minecraft.server.ChatComponentText` | 2 | 2 | Spigot |
| `net.minecraft.server.IChatBaseComponent` | 2 | 1 | Spigot |
| `net.minecraft.server.ContainerAnvil` | 1 | 6 | 공통·불명 |
| `net.minecraft.server.Slot` | 1 | 6 | 공통·불명 |
| `net.minecraft.server.TileEntitySign` | 1 | 6 | 공통·불명 |
| `net.minecraft.server.ContainerSmithing` | 1 | 4 | 공통·불명 |
| `net.minecraft.server.EntityHuman` | 1 | 4 | Spigot |
| `net.minecraft.server.ItemStack` | 1 | 4 | 공통·불명 |
| `net.minecraft.server.ContainerBeacon` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerCartography` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerEnchantTable` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerGrindstone` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerLoom` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerMerchant` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerProperty` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.ContainerStonecutter` | 1 | 3 | 공통·불명 |
| `net.minecraft.server.PacketPlayInUpdateSign` | 1 | 3 | Spigot |
| `net.minecraft.server.ContainerAccess` | 1 | 2 | 공통·불명 |
| `net.minecraft.server.InventoryCraftResult` | 1 | 2 | 공통·불명 |
| `net.minecraft.server.MerchantRecipe` | 1 | 2 | 공통·불명 |
| `net.minecraft.server.MerchantRecipeList` | 1 | 2 | 공통·불명 |
| `net.minecraft.server.NetworkManager` | 1 | 2 | Spigot |
| `net.minecraft.server.PlayerConnection` | 1 | 2 | Spigot |
| `net.minecraft.server.Container` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.ContainerProperties` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.EnumColor` | 1 | 1 | Spigot |
| `net.minecraft.server.IChatBaseComponent$ChatSerializer` | 1 | 1 | Spigot |
| `net.minecraft.server.ICrafting` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.IInventory` | 1 | 1 | Spigot |
| `net.minecraft.server.InventoryLargeChest` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.InventoryMerchant` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.InventorySubcontainer` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.MerchantWrapper` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.NonNullList` | 1 | 1 | 공통·불명 |
| `net.minecraft.server.PacketPlayOutOpenSignEditor` | 1 | 1 | Spigot |
| `net.minecraft.server.ITileInventory` | 1 | 0 | 공통·불명 |
| `net.minecraft.server.ItemBanner` | 1 | 0 | 공통·불명 |
| `net.minecraft.server.Packet` | 1 | 0 | 공통·불명 |

## 7. NMS 와 함께 부르는 Mojang·Netty 라이브러리

| 갈래 | 서로 다른 클래스 | 참조한 jar 수(최대) | 많이 쓰는 클래스(jar 수) |
|---|---:|---:|---|
| `com.mojang.authlib` | 3 | 4 | `com.mojang.authlib.GameProfile`(4), `com.mojang.authlib.properties.Property`(2), `com.mojang.authlib.properties.PropertyMap`(2) |
| `com.mojang.serialization`·`datafixers` | 5 | 3 | `com.mojang.serialization.Codec`(3), `com.mojang.serialization.DataResult`(3), `com.mojang.serialization.JsonOps`(3), `com.mojang.datafixers.util.Pair`(2) |
| `com.mojang.brigadier` | 30 | 6 | `com.mojang.brigadier.tree.CommandNode`(6), `com.mojang.brigadier.CommandDispatcher`(5), `com.mojang.brigadier.exceptions.CommandSyntaxException`(5), `com.mojang.brigadier.tree.LiteralCommandNode`(5) |
| `io.netty` | 10 | 3 | `io.netty.channel.Channel`(3), `io.netty.buffer.ByteBuf`(2), `io.netty.buffer.Unpooled`(2), `io.netty.channel.ChannelPipeline`(2) |

## 8. 리플렉션과 문자열로 가려진 접근

서버 내부에 닿는 jar 중 리플렉션 호출(`Class#forName`·`getMethod`·`Method#invoke`·`MethodHandles.Lookup#find*` 등)을 가진 jar: 14개.

서버 내부에 닿는 jar 가 문자열로 들고 있는 대표 식별자(이름이 일반적이라 과대 계상될 수 있음): `getHandle`(8), `sendPacket`(5), `getBukkitEntity`(4), `playerConnection`(4), `send`(4), `asNMSCopy`(3), `connection`(3), `asCraftMirror`(2), `channel`(2), `getServer`(2), `asBukkitCopy`(1), `getProfile`(1), `packet_handler`(1)

NMS·CraftBukkit·Mojang 클래스 이름 리터럴(전체 이름이 문자열): 137개. 앞 25개:

- `net/minecraft/core/BlockPos`
- `net/minecraft/core/BlockPosition`
- `net/minecraft/core/IRegistry`
- `net/minecraft/core/Registry`
- `net/minecraft/core/particles/BlockParticleOption`
- `net/minecraft/core/particles/ColorParticleOption`
- `net/minecraft/core/particles/DustColorTransitionOptions`
- `net/minecraft/core/particles/DustParticleOptions`
- `net/minecraft/core/particles/ItemParticleOption`
- `net/minecraft/core/particles/Particle`
- `net/minecraft/core/particles/ParticleOptions`
- `net/minecraft/core/particles/ParticleParam`
- `net/minecraft/core/particles/ParticleParamBlock`
- `net/minecraft/core/particles/ParticleParamItem`
- `net/minecraft/core/particles/ParticleParamRedstone`
- `net/minecraft/core/particles/ParticleType`
- `net/minecraft/core/particles/ParticleTypes`
- `net/minecraft/core/particles/Particles`
- `net/minecraft/core/particles/PowerParticleOption`
- `net/minecraft/core/particles/SculkChargeParticleOptions`
- `net/minecraft/core/particles/ShriekParticleOption`
- `net/minecraft/core/particles/SimpleParticleType`
- `net/minecraft/core/particles/SpellParticleOption`
- `net/minecraft/core/particles/VibrationParticleOption`
- `net/minecraft/core/registries/BuiltInRegistries`

패키지 조각 리터럴(뒤에 버전·클래스 이름을 이어 붙이는 꼴, `.` 로 끝남): 5개.

- `net.minecraft.server.`
- `org.bukkit.craftbukkit.`
- `net.minecraft.`
- `net.minecraft.*`
- `net.minecraft.*.*`

