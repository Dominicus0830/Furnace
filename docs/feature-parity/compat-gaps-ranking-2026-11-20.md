# 호환 구멍 순위 (2026-11-20, NmsGapRanking 자동 생성)

표본 jar 100개 중 Mojang 이름으로 NMS 를 직접 부르는 것 9개, NMS 를 안 부르는 것 58개, Spigot 이름이라 건너뜀(이름 대응표가 있는 PC 에서 다시) 33개. 설계 `broad-support-version-scaling-2026-11-20.md` 4.4·5.4, T-234.

## 지표 — 부르는 심볼 중 지원 비율 (목표 95% 이상)

| 기준 | 지원 | 전체 | 비율 |
|---|--:|--:|--:|
| 심볼 단순 합 | 1595 | 1988 | 80.2% |
| 인기 가중 합 | 9228 | 11539 | 80.0% |

(심볼은 플러그인마다 서로 다른 것만 센다. 플러그인 하나가 같은 심볼을 여러 번 불러도 한 번.)

## 채울 순서 — 점수 순 (점수 = 부르는 플러그인마다 log10(내려받은 수 + 10) 의 합)

| # | 점수 | 플러그인 수 | 분류 | 구멍 | 쓰는 jar |
|--:|--:|--:|---|---|---|
| 1 | 29.6 | 5 | 서명 다름 | `FriendlyByteBuf.readBytes(byte[]) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, headpat__headpats-plugin-1.0.1+paper-dev.jar, squaremap__squaremap-paper-mc1.21.1-1.3.2.jar 외 |
| 2 | 24.3 | 4 | 서명 다름 | `FriendlyByteBuf.writeBoolean(boolean) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, headpat__headpats-plugin-1.0.1+paper-dev.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar 외 |
| 3 | 19.1 | 3 | 없음(Paper 에 있음) | `net.minecraft.network.protocol.common.custom.DiscardedPayload` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 4 | 19.1 | 3 | 없음(Paper 에 있음) | `net.minecraft.server.players.PlayerList` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 5 | 19.1 | 3 | 없음(Paper 에 있음) | `MinecraftServer.getPlayerList() → PlayerList` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 6 | 16.3 | 3 | 서명 다름 | `FriendlyByteBuf.writeInt(int) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, headpat__headpats-plugin-1.0.1+paper-dev.jar, voxy-server-side__voxy-server-side-paper.jar |
| 7 | 16.3 | 3 | 서명 다름 | `FriendlyByteBuf.writeLong(long) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, headpat__headpats-plugin-1.0.1+paper-dev.jar, voxy-server-side__voxy-server-side-paper.jar |
| 8 | 13.5 | 2 | 없음(Paper 에 있음) | `MinecraftServer.isSameThread() → boolean` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 9 | 13.3 | 2 | 없음(Paper 에 있음) | `Block.getExpDrop(BlockState, ServerLevel, BlockPos, ItemStack, boolean) → int` | crazyenchantments__CrazyEnchantments-2.5.2.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 10 | 13.3 | 2 | 없음(Paper 에 있음) | `net.minecraft.server.level.ServerPlayerGameMode` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 11 | 13.3 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.entity.ai.attributes.AttributeInstance` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 12 | 13.3 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.entity.ai.attributes.Attributes` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 13 | 13.3 | 2 | 없음(Paper 에 있음) | `ServerPlayer.gameMode : net.minecraft.server.level.ServerPlayerGameMode` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 14 | 11.5 | 2 | 없음(Paper 에 있음) | `net.minecraft.core.RegistryBlockID` | geyser__Geyser-Spigot.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 15 | 11.5 | 2 | 없음(Paper 에 있음) | `net.minecraft.resources.MinecraftKey` | geyser__Geyser-Spigot.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 16 | 11.5 | 2 | 없음(Paper 에 있음) | `net.minecraft.server.level.WorldServer` | geyser__Geyser-Spigot.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 17 | 11.5 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.level.block.state.IBlockData` | geyser__Geyser-Spigot.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 18 | 11.5 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.level.chunk.ChunkSection` | geyser__Geyser-Spigot.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 19 | 11.4 | 2 | 없음(Paper 에 있음) | `ServerLevel.paperConfig() → WorldConfiguration` | fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 20 | 11.2 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.level.entity.LevelEntityGetter` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 21 | 11.2 | 2 | 없음(Paper 에 있음) | `ServerLevel.getEntities() → LevelEntityGetter` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 22 | 11.2 | 2 | 없음(Paper 에 있음) | `Entity.discard() → void` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 23 | 11.2 | 2 | 없음(Paper 에 있음) | `Entity.load(CompoundTag) → void` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 24 | 11.2 | 2 | 없음(Paper 에 있음) | `Entity.save(CompoundTag) → boolean` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 25 | 11.2 | 2 | 없음(Paper 에 있음) | `Entity.setUUID(UUID) → void` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, fastasyncworldedit__FastAsyncWorldEdit-Paper-2.16.2.jar |
| 26 | 11.0 | 2 | 없음(Paper 에 있음) | `BlockState.CODEC : com.mojang.serialization.Codec` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 27 | 11.0 | 2 | 서명 다름 | `FriendlyByteBuf.writeBytes(byte[]) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 28 | 11.0 | 2 | 서명 다름 | `FriendlyByteBuf.writeByte(int) → FriendlyByteBuf` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 29 | 11.0 | 2 | 없음(Paper 에 있음) | `ServerPlayer.getBlockX() → int` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 30 | 11.0 | 2 | 없음(Paper 에 있음) | `ServerPlayer.getBlockZ() → int` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 31 | 11.0 | 2 | 없음(Paper 에 있음) | `ServerPlayer.sendSystemMessage(Component) → void` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 32 | 11.0 | 2 | 없음(Paper 에 있음) | `PalettedContainer.codecRW(IdMap, Codec, PalettedContainer$Strategy, Object) → Codec` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, voxy-server-side__voxy-server-side-paper.jar |
| 33 | 11.0 | 2 | 서명 다름 | `StateDefinition.getPossibleStates() → ImmutableList` | orebfuscator__orebfuscator-bukkit-5.6.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 34 | 10.9 | 2 | 없음(Paper 에 있음) | `net.minecraft.world.level.storage.LevelResource` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 35 | 10.9 | 2 | 없음(Paper 에 있음) | `MinecraftServer.getWorldPath(LevelResource) → Path` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 36 | 10.9 | 2 | 없음(Paper 에 있음) | `ServerPlayer.isInvisible() → boolean` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 37 | 10.9 | 2 | 없음(Paper 에 있음) | `DimensionType.getStorageFolder(ResourceKey, Path) → Path` | squaremap__squaremap-paper-mc1.21.1-1.3.2.jar, voxy-server-side__voxy-server-side-paper.jar |
| 38 | 10.8 | 2 | 없음(Paper 에 있음) | `BlockState.hasBlockEntity() → boolean` | axiom-paper-plugin__AxiomPaperPlugin-6.0.1-for-MC1.21.1.jar, orebfuscator__orebfuscator-bukkit-5.6.2.jar |
| 39 | 10.6 | 2 | 없음(Paper 에 있음) | `ServerChunkCache.getChunkFuture(int, int, ChunkStatus, boolean) → CompletableFuture` | orebfuscator__orebfuscator-bukkit-5.6.2.jar, squaremap__squaremap-paper-mc1.21.1-1.3.2.jar |
| 40 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.commands.CommandBuildContext` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 41 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.commands.CommandSourceStack` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 42 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.commands.arguments.blocks.BlockPredicateArgument` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 43 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.commands.arguments.item.ItemPredicateArgument` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 44 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.network.chat.ClickEvent$Action` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 45 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.network.chat.ClickEvent` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 46 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.network.chat.HoverEvent$Action` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 47 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.network.chat.HoverEvent` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 48 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 49 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.PackLocationInfo` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 50 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.PackSelectionConfig` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 51 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.PackType` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 52 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.PathPackResources$PathResourcesSupplier` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 53 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.Pack$Metadata` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 54 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.Pack$Position` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 55 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.Pack$ResourcesSupplier` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 56 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.PackCompatibility` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 57 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.PackSource` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 58 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.Pack` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 59 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.server.packs.repository.RepositorySource` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 60 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.world.entity.ExperienceOrb` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 61 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.world.entity.item.ItemEntity` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 62 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.world.food.FoodData` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 63 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.world.item.enchantment.ItemEnchantments` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 64 | 8.0 | 1 | 없음(Paper 에 있음) | `net.minecraft.world.level.block.BaseFireBlock` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 65 | 8.0 | 1 | 없음(Paper 에 있음) | `WorldVersion.getPackVersion(PackType) → int` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 66 | 8.0 | 1 | 서명 다름 | `RegistryFriendlyByteBuf.readBytes(byte[]) → FriendlyByteBuf` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 67 | 8.0 | 1 | 서명 다름 | `RegistryFriendlyByteBuf.writeBytes(byte[]) → FriendlyByteBuf` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 68 | 8.0 | 1 | 없음(Paper 에 있음) | `Style.withClickEvent(ClickEvent) → Style` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 69 | 8.0 | 1 | 없음(Paper 에 있음) | `Style.withHoverEvent(HoverEvent) → Style` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 70 | 8.0 | 1 | 없음(Paper 에 있음) | `ServerPlayer.causeFoodExhaustion(float) → void` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 71 | 8.0 | 1 | 없음(Paper 에 있음) | `ServerPlayer.getMainHandItem() → ItemStack` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 72 | 8.0 | 1 | 없음(Paper 에 있음) | `Entity.moveTo(Vec3) → void` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 73 | 8.0 | 1 | 없음(Paper 에 있음) | `Player.getAttribute(Holder) → AttributeInstance` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 74 | 8.0 | 1 | 없음(Paper 에 있음) | `Player.getFoodData() → FoodData` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 75 | 8.0 | 1 | 없음(Paper 에 있음) | `Player.getMainHandItem() → ItemStack` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 76 | 8.0 | 1 | 없음(Paper 에 있음) | `Player.isCreative() → boolean` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 77 | 8.0 | 1 | 없음(Paper 에 있음) | `Player.isCrouching() → boolean` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 78 | 8.0 | 1 | 없음(Paper 에 있음) | `ItemStack.getEnchantments() → ItemEnchantments` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 79 | 8.0 | 1 | 없음(Paper 에 있음) | `ItemStack.isCorrectToolForDrops(BlockState) → boolean` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |
| 80 | 8.0 | 1 | 없음(Paper 에 있음) | `ItemStack.setDamageValue(int) → void` | veinminer__veinminer-paper-2.14.0+1.21.1.jar |

읽는 법: 여러 판을 한 jar 에 담는 플러그인(예: Geyser·orebfuscator)은 `WorldServer`·`IBlockData` 같은 Spigot 이름 코드 길도 함께 가져서 "클래스 없음"으로 잡힌다 — 그 길은 Mojang 이름 서버에서 안 쓰이므로 진짜 구멍이 아니다. 이름 대응표가 있는 PC 에서 같은 표본을 Spigot 대응표를 켜고 다시 돌리면 가려진다.

구멍 총 302건(위 80건만 — 전체는 같은 실행의 .tsv). "Paper 에도 없음"·"표 모름"은 구멍이 아니라 뺐다.

## 문자열로 가리키는 NMS 클래스 — `Class.forName` 감지 대상

플러그인이 클래스 이름 문자열로 "이 서버가 Mojang 이름 Paper 인가"를 찾아보는 것들이다. **shim 에 빈 클래스를 만들면 이 감지가 참이 되어 플러그인이 NMS 경로로 들어갔다가 첫 멤버에서 실패한다** — 아래 클래스는 주요 멤버를 채운 뒤에만 연다(설계 4.4).

| 플러그인 수 | 클래스 | shim 에 지금 있음 |
|--:|---|---|
| 2 | `net.minecraft.world.entity.AgeableMob` | 아니오 |
| 2 | `net.minecraft.world.entity.Avatar` | 아니오 |
| 2 | `net.minecraft.world.entity.Display` | 아니오 |
| 2 | `net.minecraft.world.entity.Display$BlockDisplay` | 아니오 |
| 2 | `net.minecraft.world.entity.Display$ItemDisplay` | 아니오 |
| 2 | `net.minecraft.world.entity.Display$TextDisplay` | 아니오 |
| 2 | `net.minecraft.world.entity.Entity` | 예 |
| 2 | `net.minecraft.world.entity.Interaction` | 아니오 |
| 2 | `net.minecraft.world.entity.LivingEntity` | 예 |
| 2 | `net.minecraft.world.entity.Mob` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Bee` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Cat` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Chicken` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Cow` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Fox` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.MushroomCow` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Panda` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Parrot` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Pig` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.PolarBear` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Rabbit` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Sheep` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.Turtle` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.armadillo.Armadillo` | 예 |
| 2 | `net.minecraft.world.entity.animal.axolotl.Axolotl` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.bee.Bee` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.camel.Camel` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.chicken.Chicken` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.cow.Cow` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.cow.MushroomCow` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.equine.Horse` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.equine.Llama` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.feline.Cat` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.fish.TropicalFish` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.fox.Fox` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.frog.Frog` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.goat.Goat` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.panda.Panda` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.parrot.Parrot` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.pig.Pig` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.rabbit.Rabbit` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.sheep.Sheep` | 아니오 |
| 2 | `net.minecraft.world.entity.animal.sniffer.Sniffer` | 예 |
| 2 | `net.minecraft.world.entity.animal.wolf.Wolf` | 아니오 |
| 2 | `net.minecraft.world.entity.decoration.ArmorStand` | 아니오 |
| 2 | `net.minecraft.world.entity.decoration.Mannequin` | 아니오 |
| 2 | `net.minecraft.world.entity.monster.Creeper` | 아니오 |
| 2 | `net.minecraft.world.entity.monster.SpellcasterIllager` | 아니오 |
| 2 | `net.minecraft.world.entity.npc.Villager` | 아니오 |
| 2 | `net.minecraft.world.entity.player.Player` | 예 |
| 2 | `net.minecraft.world.entity.raid.Raider` | 아니오 |
| 1 | `net.minecraft.network.EnumProtocol$b` | 아니오 |
| 1 | `net.minecraft.network.NetworkManager` | 아니오 |
| 1 | `net.minecraft.server.BiomeFog` | 아니오 |
| 1 | `net.minecraft.server.level.EntityPlayer$1` | 아니오 |
| 1 | `net.minecraft.server.level.ServerPlayer` | 예 |
| 1 | `net.minecraft.server.network.PlayerConnection` | 아니오 |
| 1 | `net.minecraft.server.network.ServerCommonPacketListenerImpl` | 예 |

전체 58개.
