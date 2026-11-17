# NMS shim 점검 결과 (자동 생성 — NmsShimAudit)

점검한 jar 4개 중 Mojang 이름으로 NMS 를 직접 부르는 것 1개. 건너뜀(Spigot 이름 대응표 필요) 0개.

| jar | 이름 체계 | 심볼 | 있음 | 없는 심볼 |
|---|---|--:|--:|--:|
| FastAsyncWorldEdit-Paper-2.16.2.jar | Mojang | 655 | 577 | 77 |
| LuckPerms-Bukkit-5.5.91.jar | Mojang | 0 | 0 | 0 |
| ProtocolLib.jar | Mojang | 0 | 0 | 0 |
| Vault.jar | Spigot(assumed) | 0 | 0 | 0 |

## 요약

| 종류 | 서로 다른 심볼 수 |
|---|--:|
| 클래스 통째로 없음 | 18 |
| shim 클래스를 상속(정의 거부) | 1 |
| 멤버 — 서명 다름 | 18 |
| 멤버 — 없음(Paper 에 있음) | 37 |
| 멤버 — 없음(Paper 에도 없음) | 2 |
| 멤버 — 표 모름 | 1 |

## 클래스 통째로 없음 — 18건

| 심볼 | 플러그인 수 | shim 서명 | 쓰는 jar |
|---|--:|---|---|
| `net.minecraft.Util` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.util.GsonHelper` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.util.StringUtil` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.util.ThreadingDetector` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.util.thread.BlockableEventLoop` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.Clearable` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.InteractionResultHolder` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.InteractionResult` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.ItemInteractionResult` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.item.DyeColor` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.WorldGenLevel` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.entity.LevelEntityGetter` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.entity.PersistentEntitySectionManager` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.levelgen.structure.BoundingBox` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.levelgen.structure.StructureStart` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `net.minecraft.world.level.storage.WorldData` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `org.bukkit.craftbukkit.block.CraftBlockState` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `org.bukkit.craftbukkit.util.BlockStateListPopulator` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |

## 서명 다름 (shim 에 같은 이름이 있는데 서술자가 다르다) — 18건

| 심볼 | 플러그인 수 | shim 서명 | 쓰는 jar |
|---|--:|---|---|
| `DefaultedRegistry.iterator() → Iterator` | 1 | `()Ljava/util/Iterator;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `HolderLookup$RegistryLookup.getOrThrow(ResourceKey) → Holder$Reference` | 1 | `(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference; ; (Lnet/minecraft/tags/TagKey;)Lnet/minecraft/core/HolderSet$Named;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `HolderLookup$RegistryLookup.listElements() → Stream` | 1 | `()Ljava/util/stream/Stream;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `RegistryAccess$Frozen.lookupOrThrow(ResourceKey) → HolderLookup$RegistryLookup` | 1 | `(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/HolderLookup$RegistryLookup;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `RegistryAccess$Frozen.registryOrThrow(ResourceKey) → Registry` | 1 | `(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `RegistryAccess.lookupOrThrow(ResourceKey) → HolderLookup$RegistryLookup` | 1 | `(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/HolderLookup$RegistryLookup;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `WritableRegistry.stream() → Stream` | 1 | `()Ljava/util/stream/Stream;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ListTag.add(Object) → boolean` | 1 | `(ILjava/lang/Object;)V ; (ILnet/minecraft/nbt/Tag;)V ; (Ljava/lang/Object;)Z` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ListTag.iterator() → Iterator` | 1 | `()Ljava/util/Iterator;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getFluidIfLoaded(BlockPos) → FluidState` | 1 | `(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getMaxBuildHeight() → int` | 1 | `()I` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getSectionIndex(int) → int` | 1 | `(I)I` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getSectionsCount() → int` | 1 | `()I` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `LevelAccessor.registryAccess() → RegistryAccess` | 1 | `()Lnet/minecraft/core/RegistryAccess;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Level.getSectionIndex(int) → int` | 1 | `(I)I` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ChunkAccess.getBlockState(BlockPos) → BlockState` | 1 | `(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `CraftWorld.getChunkAtAsync(int, int) → CompletableFuture` | 1 | `(II)Ljava/util/concurrent/CompletableFuture; ; (IILjava/util/function/Consumer;)V ; (IILorg/bukkit/World$ChunkLoadCallback;)V ; (IIZ)Ljava/util/concurrent/CompletableFuture; ; (IIZLjava/util/function/Consumer;)V ; (IIZZ)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/Location;)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/Location;Ljava/util/function/Consumer;)V ; (Lorg/bukkit/Location;Lorg/bukkit/World$ChunkLoadCallback;)V ; (Lorg/bukkit/Location;Z)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/Location;ZLjava/util/function/Consumer;)V ; (Lorg/bukkit/block/Block;)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/block/Block;Ljava/util/function/Consumer;)V ; (Lorg/bukkit/block/Block;Lorg/bukkit/World$ChunkLoadCallback;)V ; (Lorg/bukkit/block/Block;Z)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/block/Block;ZLjava/util/function/Consumer;)V` | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `CraftBlockData.getAsString() → String` | 1 | `()Ljava/lang/String; ; (Z)Ljava/lang/String;` | FastAsyncWorldEdit-Paper-2.16.2.jar |

## 멤버 없음 (Paper 1.21.1 에는 있다) — 38건

| 심볼 | 플러그인 수 | shim 서명 | 쓰는 jar |
|---|--:|---|---|
| `ServerLevel.captureBlockStates : boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.captureTreeGeneration : boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.capturedBlockStates : java.util.Map` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.generation : boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.spawnReason : org.bukkit.event.entity.CreatureSpawnEvent$SpawnReason` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `RegistryAccess$Frozen.createSerializationContext(DynamicOps) → RegistryOps` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `MinecraftServer.getWorldData() → WorldData` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerChunkCache.randomState() → RandomState` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.addFreshEntityWithPassengers(Entity, CreatureSpawnEvent$SpawnReason) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.addFreshEntity(Entity, CreatureSpawnEvent$SpawnReason) → boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.destroyBlock(BlockPos, boolean, Entity, int) → boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.enabledFeatures() → FeatureFlagSet` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getBiomeManager() → BiomeManager` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getChunkIfLoadedImmediately(int, int) → ChunkAccess` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getCurrentDifficultyAt(BlockPos) → DifficultyInstance` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getEntities() → LevelEntityGetter` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getShade(Direction, boolean) → float` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.getStructureManager() → StructureTemplateManager` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.paperConfig() → WorldConfiguration` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerLevel.structureManager() → StructureManager` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ServerPlayer.<init>(MinecraftServer, ServerLevel, GameProfile, ClientInformation) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `EntityType.create(Level) → Entity` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.absMoveTo(double, double, double, float, float) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.discard() → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.load(CompoundTag) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.save(CompoundTag) → boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Entity.setUUID(UUID) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ItemStack.applyComponents(DataComponentPatch) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ItemStack.getComponentsPatch() → DataComponentPatch` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ItemStack.getDescriptionId() → String` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ItemStack.useOn(UseOnContext) → InteractionResult` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Item.use(Level, Player, InteractionHand) → InteractionResultHolder` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `BlockState.useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult) → ItemInteractionResult` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `LevelChunk.setBiome(int, int, int, Holder) → void` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `ConfiguredFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos) → boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `PlacedFeature.place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos) → boolean` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `Structure.generate(RegistryAccess, ChunkGenerator, BiomeSource, RandomState, StructureTemplateManager, long, ChunkPos, int, LevelHeightAccessor, Predicate) → StructureStart` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |
| `com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_21_R1.PaperweightFakePlayer extends net.minecraft.server.level.ServerPlayer` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |

## 상속(정의 거부) — 1건

| 심볼 | 플러그인 수 | shim 서명 | 쓰는 jar |
|---|--:|---|---|
| `com.sk89q.worldedit.bukkit.adapter.ext.fawe.v1_21_R1.PaperweightFakePlayer extends net.minecraft.server.level.ServerPlayer` | 1 |  | FastAsyncWorldEdit-Paper-2.16.2.jar |

## Paper jar 전수 대조

Paper 의 NMS·CraftBukkit 클래스 7139개 중 shim 에 있는 것 946개, 없는 것 6193개.

shim 에 있는 클래스의 Paper 공개·보호 멤버 중 **서명 다름 570건**(같은 이름이 있으나 서술자가 다르다), 없음 9881건.

### 서명 다름 (전부)

- `net.minecraft.network.FriendlyByteBuf`
  - 메서드 `capacity(int) → FriendlyByteBuf   [shim: ()I ; (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `clear() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `discardReadBytes() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `discardSomeReadBytes() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `ensureWritable(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (IZ)I]`
  - 메서드 `getBytes(int, ByteBuf) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, ByteBuf, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, ByteBuf, int, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, OutputStream, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, ByteBuffer) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, byte[]) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `getBytes(int, byte[], int, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/GatheringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `markReaderIndex() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `markWriterIndex() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBlockPos(ByteBuf) → BlockPos   [shim: ()Lnet/minecraft/core/BlockPos;]`
  - 메서드 `readByteArray(ByteBuf) → byte[]   [shim: ()[B ; (I)[B]`
  - 메서드 `readByteArray(ByteBuf, int) → byte[]   [shim: ()[B ; (I)[B]`
  - 메서드 `readBytes(ByteBuf) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(ByteBuf, int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(ByteBuf, int, int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(OutputStream, int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(ByteBuffer) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(byte[]) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readBytes(byte[], int, int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/OutputStream;I)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/GatheringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `readLongArray(long[]) → long[]   [shim: ()[J]`
  - 메서드 `readLongArray(long[], int) → long[]   [shim: ()[J]`
  - 메서드 `readUUID(ByteBuf) → UUID   [shim: ()Ljava/util/UUID;]`
  - 메서드 `readVarIntArray(int) → int[]   [shim: ()[I]`
  - 메서드 `readerIndex(int) → FriendlyByteBuf   [shim: ()I ; (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `resetReaderIndex() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `resetWriterIndex() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf;]`
  - 메서드 `retain() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf; ; ()Lio/netty/util/ReferenceCounted; ; (I)Lio/netty/buffer/ByteBuf; ; (I)Lio/netty/util/ReferenceCounted;]`
  - 메서드 `retain(int) → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf; ; ()Lio/netty/util/ReferenceCounted; ; (I)Lio/netty/buffer/ByteBuf; ; (I)Lio/netty/util/ReferenceCounted;]`
  - 메서드 `setBoolean(int, boolean) → FriendlyByteBuf   [shim: (IZ)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setByte(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, ByteBuf) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, ByteBuf, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, ByteBuf, int, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, ByteBuffer) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, byte[]) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setBytes(int, byte[], int, int) → FriendlyByteBuf   [shim: (ILio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (ILio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (ILjava/io/InputStream;I)I ; (ILjava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (ILjava/nio/channels/FileChannel;JI)I ; (ILjava/nio/channels/ScatteringByteChannel;I)I ; (I[B)Lio/netty/buffer/ByteBuf; ; (I[BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setChar(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setDouble(int, double) → FriendlyByteBuf   [shim: (ID)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setFloat(int, float) → FriendlyByteBuf   [shim: (IF)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setIndex(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setInt(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setIntLE(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setLong(int, long) → FriendlyByteBuf   [shim: (IJ)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setLongLE(int, long) → FriendlyByteBuf   [shim: (IJ)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setMedium(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setMediumLE(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setShort(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setShortLE(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `setZero(int, int) → FriendlyByteBuf   [shim: (II)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `skipBytes(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `touch() → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf; ; ()Lio/netty/util/ReferenceCounted; ; (Ljava/lang/Object;)Lio/netty/buffer/ByteBuf; ; (Ljava/lang/Object;)Lio/netty/util/ReferenceCounted;]`
  - 메서드 `touch(Object) → FriendlyByteBuf   [shim: ()Lio/netty/buffer/ByteBuf; ; ()Lio/netty/util/ReferenceCounted; ; (Ljava/lang/Object;)Lio/netty/buffer/ByteBuf; ; (Ljava/lang/Object;)Lio/netty/util/ReferenceCounted;]`
  - 메서드 `writeBlockPos(ByteBuf, BlockPos) → void   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/network/FriendlyByteBuf;]`
  - 메서드 `writeBoolean(boolean) → FriendlyByteBuf   [shim: (Z)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeByte(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeByteArray(ByteBuf, byte[]) → void   [shim: ([B)Lnet/minecraft/network/FriendlyByteBuf;]`
  - 메서드 `writeBytes(ByteBuf) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeBytes(ByteBuf, int) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeBytes(ByteBuf, int, int) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeBytes(ByteBuffer) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeBytes(byte[]) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeBytes(byte[], int, int) → FriendlyByteBuf   [shim: (Lio/netty/buffer/ByteBuf;)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;I)Lio/netty/buffer/ByteBuf; ; (Lio/netty/buffer/ByteBuf;II)Lio/netty/buffer/ByteBuf; ; (Ljava/io/InputStream;I)I ; (Ljava/nio/ByteBuffer;)Lio/netty/buffer/ByteBuf; ; (Ljava/nio/channels/FileChannel;JI)I ; (Ljava/nio/channels/ScatteringByteChannel;I)I ; ([B)Lio/netty/buffer/ByteBuf; ; ([BII)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeChar(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeDouble(double) → FriendlyByteBuf   [shim: (D)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeFloat(float) → FriendlyByteBuf   [shim: (F)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeInt(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeIntLE(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeLong(long) → FriendlyByteBuf   [shim: (J)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeLongLE(long) → FriendlyByteBuf   [shim: (J)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeMedium(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeMediumLE(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeShort(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeShortLE(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writeUUID(ByteBuf, UUID) → void   [shim: (Ljava/util/UUID;)Lnet/minecraft/network/FriendlyByteBuf;]`
  - 메서드 `writeZero(int) → FriendlyByteBuf   [shim: (I)Lio/netty/buffer/ByteBuf;]`
  - 메서드 `writerIndex(int) → FriendlyByteBuf   [shim: ()I ; (I)Lio/netty/buffer/ByteBuf;]`
- `org.bukkit.craftbukkit.CraftWorld`
  - 메서드 `dropItem(Location, ItemStack) → Item   [shim: (Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item; ; (Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Item;]`
  - 메서드 `dropItemNaturally(Location, ItemStack) → Item   [shim: (Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item; ; (Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Item;]`
  - 메서드 `getBiome(int, int) → Biome   [shim: (II)Lorg/bukkit/block/Biome; ; (III)Lorg/bukkit/block/Biome; ; (Lorg/bukkit/Location;)Lorg/bukkit/block/Biome;]`
  - 메서드 `getBlockAt(Location) → Block   [shim: (III)Lorg/bukkit/block/Block; ; (Lorg/bukkit/Location;)Lorg/bukkit/block/Block;]`
  - 메서드 `getChunkAt(int, int, boolean) → Chunk   [shim: (II)Lorg/bukkit/Chunk; ; (Lorg/bukkit/Location;)Lorg/bukkit/Chunk; ; (Lorg/bukkit/block/Block;)Lorg/bukkit/Chunk;]`
  - 메서드 `getChunkAt(Location) → Chunk   [shim: (II)Lorg/bukkit/Chunk; ; (Lorg/bukkit/Location;)Lorg/bukkit/Chunk; ; (Lorg/bukkit/block/Block;)Lorg/bukkit/Chunk;]`
  - 메서드 `getChunkAt(Block) → Chunk   [shim: (II)Lorg/bukkit/Chunk; ; (Lorg/bukkit/Location;)Lorg/bukkit/Chunk; ; (Lorg/bukkit/block/Block;)Lorg/bukkit/Chunk;]`
  - 메서드 `getEntitiesByClass(Class[]) → Collection   [shim: (Ljava/lang/Class;)Ljava/util/Collection;]`
  - 메서드 `getGenerator() → ChunkGenerator   [shim: ()Lorg/bukkit/generator/ChunkGenerator;]`
  - 메서드 `isChunkLoaded(Chunk) → boolean   [shim: (II)Z]`
  - 메서드 `playEffect(Location, Effect, int) → void   [shim: (Lorg/bukkit/Location;Lorg/bukkit/Effect;I)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;II)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;I)V]`
  - 메서드 `playEffect(Location, Effect, Object) → void   [shim: (Lorg/bukkit/Location;Lorg/bukkit/Effect;I)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;II)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;I)V]`
  - 메서드 `playEffect(Player, Effect, int) → void   [shim: (Lorg/bukkit/Location;Lorg/bukkit/Effect;I)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;II)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;)V ; (Lorg/bukkit/Location;Lorg/bukkit/Effect;Ljava/lang/Object;I)V]`
  - 메서드 `playSound(Sound) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Sound, double, double, double) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Sound, Sound$Emitter) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, String, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, String, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, Sound, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, Sound, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, String, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, String, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, Sound, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, Sound, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `setBiome(int, int, int, Holder) → void   [shim: (IIILorg/bukkit/block/Biome;)V ; (IILorg/bukkit/block/Biome;)V ; (Lorg/bukkit/Location;Lorg/bukkit/block/Biome;)V]`
  - 메서드 `setBiome(int, int, Biome) → void   [shim: (IIILorg/bukkit/block/Biome;)V ; (IILorg/bukkit/block/Biome;)V ; (Lorg/bukkit/Location;Lorg/bukkit/block/Biome;)V]`
  - 메서드 `setSpawnLocation(int, int, int) → boolean   [shim: (III)Z ; (IIIF)Z ; (Lorg/bukkit/Location;)Z]`
  - 메서드 `spawn(Location, Class, CreatureSpawnEvent$SpawnReason, boolean, Consumer) → LivingEntity   [shim: (Lorg/bukkit/Location;Ljava/lang/Class;)Lorg/bukkit/entity/Entity; ; (Lorg/bukkit/Location;Ljava/lang/Class;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Entity;]`
  - 메서드 `spawnParticle(Particle, double, double, double, int) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, double, Object, boolean) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double, Object, boolean) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Ljava/util/List;Lorg/bukkit/entity/Player;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `vanillaBiomeProvider() → BiomeProvider   [shim: ()Lorg/bukkit/generator/BiomeProvider;]`
- `org.bukkit.craftbukkit.entity.CraftPlayer`
  - 메서드 `giveExp(int, boolean) → void   [shim: (I)V]`
  - 메서드 `playSound(Sound) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Sound, double, double, double) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Sound, Sound$Emitter) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, String, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, String, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, Sound, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Location, Sound, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, String, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, String, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, Sound, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `playSound(Entity, Sound, SoundCategory, float, float) → void   [shim: (Lorg/bukkit/Location;Ljava/lang/String;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/Location;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Ljava/lang/String;Lorg/bukkit/SoundCategory;FFJ)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FF)V ; (Lorg/bukkit/entity/Entity;Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;FFJ)V]`
  - 메서드 `sendBlockChange(Location, Material, byte) → void   [shim: (Lorg/bukkit/Location;Lorg/bukkit/block/data/BlockData;)V]`
  - 메서드 `sendMessage(UUID, String) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendMessage(UUID, String[]) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendMessage(SignedMessage, ChatType$Bound) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendMessage(Identity, Component, MessageType) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendMessage(Component, ChatType$Bound) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendTitle(Title) → void   [shim: (Ljava/lang/String;Ljava/lang/String;)V ; (Ljava/lang/String;Ljava/lang/String;III)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, double, double, double, int, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, double, Object, boolean) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, double, double, double, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `spawnParticle(Particle, Location, int, Object) → void   [shim: (Lorg/bukkit/Particle;DDDI)V ; (Lorg/bukkit/Particle;DDDIDDD)V ; (Lorg/bukkit/Particle;DDDIDDDD)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDIDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;DDDIDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;DDDILjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;I)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDD)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDDLjava/lang/Object;Z)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;IDDDLjava/lang/Object;)V ; (Lorg/bukkit/Particle;Lorg/bukkit/Location;ILjava/lang/Object;)V]`
  - 메서드 `stopSound(String) → void   [shim: (Ljava/lang/String;)V ; (Ljava/lang/String;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/Sound;)V ; (Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/SoundCategory;)V]`
  - 메서드 `stopSound(SoundStop) → void   [shim: (Ljava/lang/String;)V ; (Ljava/lang/String;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/Sound;)V ; (Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/SoundCategory;)V]`
  - 메서드 `stopSound(Sound) → void   [shim: (Ljava/lang/String;)V ; (Ljava/lang/String;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/Sound;)V ; (Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/SoundCategory;)V]`
  - 메서드 `stopSound(Sound, SoundCategory) → void   [shim: (Ljava/lang/String;)V ; (Ljava/lang/String;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/Sound;)V ; (Lorg/bukkit/Sound;Lorg/bukkit/SoundCategory;)V ; (Lorg/bukkit/SoundCategory;)V]`
  - 메서드 `teleport(Location, PlayerTeleportEvent$TeleportCause) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
  - 메서드 `teleport(Location, PlayerTeleportEvent$TeleportCause, TeleportFlag[]) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
- `org.bukkit.craftbukkit.CraftServer`
  - 메서드 `broadcast(Component) → int   [shim: (Ljava/lang/String;Ljava/lang/String;)I]`
  - 메서드 `broadcast(Component, String) → int   [shim: (Ljava/lang/String;Ljava/lang/String;)I]`
  - 메서드 `createBlockData(Material, Consumer) → BlockData   [shim: (Ljava/lang/String;)Lorg/bukkit/block/data/BlockData; ; (Lorg/bukkit/Material;)Lorg/bukkit/block/data/BlockData; ; (Lorg/bukkit/Material;Ljava/lang/String;)Lorg/bukkit/block/data/BlockData;]`
  - 메서드 `createBossBar(NamespacedKey, String, BarColor, BarStyle, BarFlag[]) → KeyedBossBar   [shim: (Ljava/lang/String;Lorg/bukkit/boss/BarColor;Lorg/bukkit/boss/BarStyle;[Lorg/bukkit/boss/BarFlag;)Lorg/bukkit/boss/BossBar;]`
  - 메서드 `createInventory(InventoryHolder, int, Component) → Inventory   [shim: (Lorg/bukkit/inventory/InventoryHolder;I)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;ILjava/lang/String;)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;Ljava/lang/String;)Lorg/bukkit/inventory/Inventory;]`
  - 메서드 `createInventory(InventoryHolder, InventoryType, Component) → Inventory   [shim: (Lorg/bukkit/inventory/InventoryHolder;I)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;ILjava/lang/String;)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;)Lorg/bukkit/inventory/Inventory; ; (Lorg/bukkit/inventory/InventoryHolder;Lorg/bukkit/event/inventory/InventoryType;Ljava/lang/String;)Lorg/bukkit/inventory/Inventory;]`
  - 메서드 `createWorld(String, World$Environment) → World   [shim: (Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;]`
  - 메서드 `createWorld(String, World$Environment, long) → World   [shim: (Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;]`
  - 메서드 `createWorld(String, World$Environment, long, ChunkGenerator) → World   [shim: (Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;]`
  - 메서드 `createWorld(String, World$Environment, ChunkGenerator) → World   [shim: (Lorg/bukkit/WorldCreator;)Lorg/bukkit/World;]`
  - 메서드 `dispatchCommand(CommandSourceStack, String) → boolean   [shim: (Lorg/bukkit/command/CommandSender;Ljava/lang/String;)Z]`
  - 메서드 `getCommandMap() → SimpleCommandMap   [shim: ()Lorg/bukkit/command/CommandMap;]`
  - 메서드 `getDatapackManager() → PaperDatapackManager   [shim: ()Lio/papermc/paper/datapack/DatapackManager;]`
  - 메서드 `getGlobalRegionScheduler() → FoliaGlobalRegionScheduler   [shim: ()Lio/papermc/paper/threadedregions/scheduler/GlobalRegionScheduler;]`
  - 메서드 `getItemFactory() → CraftItemFactory   [shim: ()Lorg/bukkit/inventory/ItemFactory;]`
  - 메서드 `getOfflinePlayer(GameProfile) → OfflinePlayer   [shim: (Ljava/lang/String;)Lorg/bukkit/OfflinePlayer; ; (Ljava/util/UUID;)Lorg/bukkit/OfflinePlayer; ; (Ljava/util/UUID;Ljava/lang/String;)Lorg/bukkit/OfflinePlayer;]`
  - 메서드 `getOnlinePlayers() → List   [shim: ()Ljava/util/Collection;]`
  - 메서드 `getRegistry(Class) → Registry   [shim: (Ljava/lang/Class;)Lorg/bukkit/Registry;]`
  - 메서드 `getScheduler() → CraftScheduler   [shim: ()Lorg/bukkit/scheduler/BukkitScheduler;]`
  - 메서드 `getTag(String, NamespacedKey, Class) → Tag   [shim: (Ljava/lang/String;Lorg/bukkit/NamespacedKey;Ljava/lang/Class;)Lorg/bukkit/Tag;]`
  - 메서드 `getTags(String, Class) → Iterable   [shim: (Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Iterable;]`
  - 메서드 `getWorld(Key) → World   [shim: (Ljava/lang/String;)Lorg/bukkit/World; ; (Ljava/util/UUID;)Lorg/bukkit/World;]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `org.bukkit.craftbukkit.entity.CraftEntity`
  - 메서드 `addAttachment(Plugin) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, int) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, String, boolean) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, String, boolean, int) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `getEffectivePermissions() → Set   [shim: ()Ljava/util/Set;]`
  - 메서드 `getLocation(Location) → Location   [shim: ()Lorg/bukkit/Location; ; (Lorg/bukkit/Location;)Lorg/bukkit/Location;]`
  - 메서드 `getPersistentDataContainer() → CraftPersistentDataContainer   [shim: ()Lio/papermc/paper/persistence/PersistentDataContainerView; ; ()Lorg/bukkit/persistence/PersistentDataContainer;]`
  - 메서드 `hasPermission(Permission) → boolean   [shim: (Ljava/lang/String;)Z ; (Lorg/bukkit/permissions/Permission;)Z]`
  - 메서드 `isPermissionSet(Permission) → boolean   [shim: (Ljava/lang/String;)Z ; (Lorg/bukkit/permissions/Permission;)Z]`
  - 메서드 `isSneaking() → boolean   [shim: ()Z]`
  - 메서드 `recalculatePermissions() → void   [shim: ()V]`
  - 메서드 `removeAttachment(PermissionAttachment) → void   [shim: (Lorg/bukkit/permissions/PermissionAttachment;)V]`
  - 메서드 `sendMessage(UUID, String) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `sendMessage(UUID, String[]) → void   [shim: (Ljava/lang/String;)V ; ([Ljava/lang/String;)V]`
  - 메서드 `teleport(Location, PlayerTeleportEvent$TeleportCause) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
  - 메서드 `teleport(Location, PlayerTeleportEvent$TeleportCause, TeleportFlag[]) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
  - 메서드 `teleport(Entity) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
  - 메서드 `teleport(Entity, PlayerTeleportEvent$TeleportCause) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
  - 메서드 `teleportAsync(Location, PlayerTeleportEvent$TeleportCause, TeleportFlag[]) → CompletableFuture   [shim: (Lorg/bukkit/Location;)Ljava/util/concurrent/CompletableFuture; ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Ljava/util/concurrent/CompletableFuture;]`
- `net.minecraft.world.level.Level`
  - 메서드 `getBlockEntity(BlockPos, boolean) → BlockEntity   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;]`
  - 메서드 `getChunk(int, int, ChunkStatus) → ChunkAccess   [shim: (II)Lnet/minecraft/world/level/chunk/ChunkAccess; ; (II)Lnet/minecraft/world/level/chunk/LevelChunk; ; (IILnet/minecraft/world/level/chunk/status/ChunkStatus;)Lnet/minecraft/world/level/chunk/ChunkAccess; ; (IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess; ; (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/chunk/ChunkAccess;]`
  - 메서드 `getChunkIfLoaded(BlockPos) → LevelChunk   [shim: (II)Lnet/minecraft/world/level/chunk/LevelChunk;]`
  - 메서드 `getFluidIfLoaded(BlockPos) → FluidState   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;]`
  - 메서드 `getHeightmapPos(Heightmap$Types, BlockPos) → BlockPos   [shim: (Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;]`
  - 메서드 `hasChunkAt(BlockPos) → boolean   [shim: (II)Z ; (Lnet/minecraft/core/BlockPos;)Z]`
  - 메서드 `playSound(Entity, BlockPos, SoundEvent, SoundSource, float, float) → void   [shim: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V]`
  - 메서드 `playSound(Player, double, double, double, Holder, SoundSource, float, float) → void   [shim: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V]`
  - 메서드 `playSound(Player, double, double, double, SoundEvent, SoundSource) → void   [shim: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V]`
  - 메서드 `playSound(Player, double, double, double, SoundEvent, SoundSource, float, float) → void   [shim: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V]`
  - 메서드 `playSound(Player, Entity, SoundEvent, SoundSource, float, float) → void   [shim: (Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V]`
- `org.bukkit.craftbukkit.block.data.CraftBlockData`
  - 메서드 `createBlockState() → BlockState   [shim: ()Lorg/bukkit/block/BlockState;]`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `getAsString() → String   [shim: ()Ljava/lang/String; ; (Z)Ljava/lang/String;]`
  - 메서드 `getAsString(boolean) → String   [shim: ()Ljava/lang/String; ; (Z)Ljava/lang/String;]`
  - 메서드 `getMaterial() → Material   [shim: ()Lorg/bukkit/Material;]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `matches(BlockData) → boolean   [shim: (Lorg/bukkit/block/data/BlockData;)Z]`
  - 메서드 `newData(BlockType, String) → CraftBlockData   [shim: (Lorg/bukkit/Material;Ljava/lang/String;)Lorg/bukkit/craftbukkit/block/data/CraftBlockData;]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
  - 메서드 `toString(Map) → String   [shim: ()Ljava/lang/String;]`
- `org.bukkit.craftbukkit.entity.CraftHumanEntity`
  - 메서드 `closeInventory() → void   [shim: ()V ; (Lorg/bukkit/event/inventory/InventoryCloseEvent$Reason;)V]`
  - 메서드 `closeInventory(InventoryCloseEvent$Reason) → void   [shim: ()V ; (Lorg/bukkit/event/inventory/InventoryCloseEvent$Reason;)V]`
  - 메서드 `getGameMode() → GameMode   [shim: ()Lorg/bukkit/GameMode;]`
  - 메서드 `getInventory() → PlayerInventory   [shim: ()Lorg/bukkit/inventory/Inventory; ; ()Lorg/bukkit/inventory/PlayerInventory;]`
  - 메서드 `getItemInHand() → ItemStack   [shim: ()Lorg/bukkit/inventory/ItemStack;]`
  - 메서드 `getOpenInventory() → InventoryView   [shim: ()Lorg/bukkit/inventory/InventoryView;]`
  - 메서드 `openInventory(Inventory) → InventoryView   [shim: (Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;]`
  - 메서드 `openInventory(InventoryView) → void   [shim: (Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView;]`
  - 메서드 `setGameMode(GameMode) → void   [shim: (Lorg/bukkit/GameMode;)V]`
  - 메서드 `setItemInHand(ItemStack) → void   [shim: (Lorg/bukkit/inventory/ItemStack;)V]`
- `org.bukkit.craftbukkit.entity.CraftMinecartCommand`
  - 메서드 `addAttachment(Plugin) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, int) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, String, boolean) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `addAttachment(Plugin, String, boolean, int) → PermissionAttachment   [shim: (Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;I)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;Z)Lorg/bukkit/permissions/PermissionAttachment; ; (Lorg/bukkit/plugin/Plugin;Ljava/lang/String;ZI)Lorg/bukkit/permissions/PermissionAttachment;]`
  - 메서드 `getEffectivePermissions() → Set   [shim: ()Ljava/util/Set;]`
  - 메서드 `getHandle() → MinecartCommandBlock   [shim: ()Lnet/minecraft/world/entity/Entity;]`
  - 메서드 `hasPermission(Permission) → boolean   [shim: (Ljava/lang/String;)Z ; (Lorg/bukkit/permissions/Permission;)Z]`
  - 메서드 `isPermissionSet(Permission) → boolean   [shim: (Ljava/lang/String;)Z ; (Lorg/bukkit/permissions/Permission;)Z]`
  - 메서드 `recalculatePermissions() → void   [shim: ()V]`
  - 메서드 `removeAttachment(PermissionAttachment) → void   [shim: (Lorg/bukkit/permissions/PermissionAttachment;)V]`
- `org.bukkit.craftbukkit.profile.CraftPlayerProfile`
  - 메서드 `getProperty(GameProfile, String) → Property   [shim: (Ljava/lang/String;)Lcom/destroystokyo/paper/profile/ProfileProperty;]`
  - 메서드 `getProperty(String) → Property   [shim: (Ljava/lang/String;)Lcom/destroystokyo/paper/profile/ProfileProperty;]`
  - 메서드 `setProperty(ProfileProperty) → void   [shim: (Ljava/lang/String;Lcom/destroystokyo/paper/profile/ProfileProperty;)V]`
  - 메서드 `setProperty(String, Property) → void   [shim: (Ljava/lang/String;Lcom/destroystokyo/paper/profile/ProfileProperty;)V]`
  - 메서드 `toString(PropertyMap) → String   [shim: ()Ljava/lang/String;]`
  - 메서드 `validateSkullProfile(GameProfile) → GameProfile   [shim: (Lorg/domi/furnace/compat/paper/ProfileValue;)Lorg/domi/furnace/compat/paper/ProfileValue;]`
  - 메서드 `validateSkullProfile(ResolvableProfile) → ResolvableProfile   [shim: (Lorg/domi/furnace/compat/paper/ProfileValue;)Lorg/domi/furnace/compat/paper/ProfileValue;]`
- `org.bukkit.craftbukkit.profile.CraftProfileProperty`
  - 메서드 `decodePropertyValue(String) → JsonObject   [shim: (Ljava/lang/String;)Lorg/domi/furnace/api/config/ConfigSection;]`
  - 메서드 `deserialize(Map) → Property   [shim: (Ljava/util/Map;)Lcom/destroystokyo/paper/profile/ProfileProperty;]`
  - 메서드 `equals(Property, Property) → boolean   [shim: (Lcom/destroystokyo/paper/profile/ProfileProperty;Lcom/destroystokyo/paper/profile/ProfileProperty;)Z ; (Ljava/lang/Object;)Z]`
  - 메서드 `hasValidSignature(Property) → boolean   [shim: (Lcom/destroystokyo/paper/profile/ProfileProperty;)Z]`
  - 메서드 `hashCode(Property) → int   [shim: ()I ; (Lcom/destroystokyo/paper/profile/ProfileProperty;)I]`
  - 메서드 `serialize(Property) → Map   [shim: (Lcom/destroystokyo/paper/profile/ProfileProperty;)Ljava/util/Map;]`
  - 메서드 `toString(Property) → String   [shim: ()Ljava/lang/String; ; (Lcom/destroystokyo/paper/profile/ProfileProperty;)Ljava/lang/String; ; (Ljava/util/List;)Ljava/lang/String;]`
- `net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase`
  - 메서드 `canBeReplaced(Fluid) → boolean   [shim: ()Z ; (Lnet/minecraft/world/item/context/BlockPlaceContext;)Z]`
  - 메서드 `getShape(BlockGetter, BlockPos, CollisionContext) → VoxelShape   [shim: (Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;]`
  - 메서드 `is(HolderSet) → boolean   [shim: (Lnet/minecraft/core/Holder;)Z ; (Lnet/minecraft/tags/TagKey;)Z ; (Lnet/minecraft/tags/TagKey;Ljava/util/function/Predicate;)Z ; (Lnet/minecraft/world/level/block/Block;)Z]`
  - 메서드 `is(ResourceKey) → boolean   [shim: (Lnet/minecraft/core/Holder;)Z ; (Lnet/minecraft/tags/TagKey;)Z ; (Lnet/minecraft/tags/TagKey;Ljava/util/function/Predicate;)Z ; (Lnet/minecraft/world/level/block/Block;)Z]`
  - 메서드 `onPlace(Level, BlockPos, BlockState, boolean, UseOnContext) → void   [shim: (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V]`
- `org.bukkit.craftbukkit.block.CraftBlock`
  - 메서드 `getBiome() → Biome   [shim: ()Lorg/bukkit/block/Biome;]`
  - 메서드 `getDrops(ItemStack, Entity) → Collection   [shim: ()Ljava/util/Collection; ; (Lorg/bukkit/inventory/ItemStack;)Ljava/util/Collection;]`
  - 메서드 `getFace(BlockFace) → Block   [shim: (Lorg/bukkit/block/Block;)Lorg/bukkit/block/BlockFace;]`
  - 메서드 `getFace(BlockFace, int) → Block   [shim: (Lorg/bukkit/block/Block;)Lorg/bukkit/block/BlockFace;]`
  - 메서드 `setBiome(Biome) → void   [shim: (Lorg/bukkit/block/Biome;)V]`
- `net.minecraft.world.level.border.WorldBorder`
  - 메서드 `isWithinBounds(double, double, double) → boolean   [shim: (DD)Z ; (Lnet/minecraft/core/BlockPos;)Z]`
  - 메서드 `isWithinBounds(ChunkPos) → boolean   [shim: (DD)Z ; (Lnet/minecraft/core/BlockPos;)Z]`
  - 메서드 `isWithinBounds(AABB) → boolean   [shim: (DD)Z ; (Lnet/minecraft/core/BlockPos;)Z]`
  - 메서드 `isWithinBounds(Vec3) → boolean   [shim: (DD)Z ; (Lnet/minecraft/core/BlockPos;)Z]`
  - 필드 `world : net.minecraft.server.level.ServerLevel   [shim: Lorg/domi/furnace/api/world/World;]`
- `net.minecraft.server.network.ServerCommonPacketListenerImpl`
  - 메서드 `disconnect(Component) → void   [shim: (Lnet/minecraft/network/chat/Component;)V]`
  - 메서드 `disconnect(Component, PlayerKickEvent$Cause) → void   [shim: (Lnet/minecraft/network/chat/Component;)V]`
  - 메서드 `disconnect(DisconnectionDetails, PlayerKickEvent$Cause) → void   [shim: (Lnet/minecraft/network/chat/Component;)V]`
  - 메서드 `disconnect(Component, PlayerKickEvent$Cause) → void   [shim: (Lnet/minecraft/network/chat/Component;)V]`
  - 메서드 `send(Packet, PacketSendListener) → void   [shim: (Lnet/minecraft/network/protocol/Packet;)V]`
- `org.bukkit.craftbukkit.inventory.CraftItemStack`
  - 메서드 `asNMSCopy(List) → List   [shim: (Lorg/bukkit/inventory/ItemStack;)Lnet/minecraft/world/item/ItemStack;]`
  - 메서드 `getItemMeta(ItemStack) → ItemMeta   [shim: ()Lorg/bukkit/inventory/meta/ItemMeta;]`
  - 메서드 `getItemMeta(ItemStack, ItemType) → ItemMeta   [shim: ()Lorg/bukkit/inventory/meta/ItemMeta;]`
  - 메서드 `getItemMeta(ItemStack, ItemType, Set) → ItemMeta   [shim: ()Lorg/bukkit/inventory/meta/ItemMeta;]`
  - 메서드 `setItemMeta(ItemStack, ItemMeta) → boolean   [shim: (Lorg/bukkit/inventory/meta/ItemMeta;)Z]`
- `org.bukkit.craftbukkit.util.CraftMagicNumbers`
  - 메서드 `getBlock(MaterialData) → BlockState   [shim: (Lorg/bukkit/Material;)Lnet/minecraft/world/level/block/Block; ; (Lorg/bukkit/Material;B)Lnet/minecraft/world/level/block/state/BlockState;]`
  - 메서드 `getItem(Material, short) → Item   [shim: (Lorg/bukkit/Material;)Lnet/minecraft/world/item/Item;]`
  - 메서드 `getMaterial(String, int) → Material   [shim: (Lnet/minecraft/world/item/Item;)Lorg/bukkit/Material; ; (Lnet/minecraft/world/level/block/Block;)Lorg/bukkit/Material;]`
  - 메서드 `getMaterial(BlockState) → MaterialData   [shim: (Lnet/minecraft/world/item/Item;)Lorg/bukkit/Material; ; (Lnet/minecraft/world/level/block/Block;)Lorg/bukkit/Material;]`
- `net.minecraft.core.Direction`
  - 메서드 `getClockWise(Direction$Axis) → Direction   [shim: ()Lnet/minecraft/core/Direction;]`
  - 메서드 `getCounterClockWise(Direction$Axis) → Direction   [shim: ()Lnet/minecraft/core/Direction;]`
  - 메서드 `getNearest(float, float, float) → Direction   [shim: (DDD)Lnet/minecraft/core/Direction;]`
  - 메서드 `getNearest(Vec3) → Direction   [shim: (DDD)Lnet/minecraft/core/Direction;]`
- `org.bukkit.craftbukkit.metadata.BlockMetadataStore`
  - 메서드 `getMetadata(Block, String) → List   [shim: (Ljava/lang/Object;Ljava/lang/String;)Ljava/util/List;]`
  - 메서드 `hasMetadata(Block, String) → boolean   [shim: (Ljava/lang/Object;Ljava/lang/String;)Z]`
  - 메서드 `removeMetadata(Block, String, Plugin) → void   [shim: (Ljava/lang/Object;Ljava/lang/String;Lorg/bukkit/plugin/Plugin;)V]`
  - 메서드 `setMetadata(Block, String, MetadataValue) → void   [shim: (Ljava/lang/Object;Ljava/lang/String;Lorg/bukkit/metadata/MetadataValue;)V]`
- `net.minecraft.world.entity.Entity`
  - 메서드 `getX(double) → double   [shim: ()D]`
  - 메서드 `getY(double) → double   [shim: ()D]`
  - 메서드 `getZ(double) → double   [shim: ()D]`
- `org.bukkit.craftbukkit.entity.CraftLivingEntity`
  - 메서드 `launchProjectile(Class) → Projectile   [shim: (Ljava/lang/Class;)Lorg/bukkit/entity/Projectile; ; (Ljava/lang/Class;Lorg/bukkit/util/Vector;)Lorg/bukkit/entity/Projectile; ; (Ljava/lang/Class;Lorg/bukkit/util/Vector;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Projectile;]`
  - 메서드 `launchProjectile(Class, Vector) → Projectile   [shim: (Ljava/lang/Class;)Lorg/bukkit/entity/Projectile; ; (Ljava/lang/Class;Lorg/bukkit/util/Vector;)Lorg/bukkit/entity/Projectile; ; (Ljava/lang/Class;Lorg/bukkit/util/Vector;Ljava/util/function/Consumer;)Lorg/bukkit/entity/Projectile;]`
  - 메서드 `teleport(Location, PlayerTeleportEvent$TeleportCause) → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
- `net.minecraft.network.Connection`
  - 메서드 `disconnect(DisconnectionDetails) → void   [shim: (Lnet/minecraft/network/chat/Component;)V]`
  - 메서드 `send(Packet, PacketSendListener) → void   [shim: (Lnet/minecraft/network/protocol/Packet;)V]`
  - 메서드 `send(Packet, PacketSendListener, boolean) → void   [shim: (Lnet/minecraft/network/protocol/Packet;)V]`
- `net.minecraft.world.level.dimension.DimensionType`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.world.entity.animal.CatVariant`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.world.entity.decoration.PaintingVariant`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.server.ServerLinks`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.core.Rotations`
  - 필드 `x : float   [shim: F]`
  - 필드 `y : float   [shim: F]`
  - 필드 `z : float   [shim: F]`
- `net.minecraft.world.entity.animal.FrogVariant`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `org.bukkit.craftbukkit.util.CraftChatMessage`
  - 메서드 `fromJSONOrString(String, boolean) → Component   [shim: (Ljava/lang/String;)Lnet/minecraft/network/chat/Component;]`
  - 메서드 `fromJSONOrString(String, boolean, boolean) → Component   [shim: (Ljava/lang/String;)Lnet/minecraft/network/chat/Component;]`
  - 메서드 `fromJSONOrString(String, boolean, boolean, int, boolean) → Component   [shim: (Ljava/lang/String;)Lnet/minecraft/network/chat/Component;]`
- `net.minecraft.stats.Stat`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.world.level.WorldDataConfiguration`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.core.Vec3i`
  - 필드 `x : int   [shim: I]`
  - 필드 `y : int   [shim: I]`
  - 필드 `z : int   [shim: I]`
- `net.minecraft.core.DefaultedRegistry`
  - 메서드 `byId(int) → Object   [shim: (I)Ljava/lang/Object;]`
  - 메서드 `get(ResourceLocation) → Object   [shim: (Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object; ; (Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;]`
  - 메서드 `getKey(Object) → ResourceLocation   [shim: (Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;]`
- `net.minecraft.world.item.ItemStack`
  - 메서드 `copy(boolean) → ItemStack   [shim: ()Lnet/minecraft/world/item/ItemStack;]`
  - 메서드 `is(Predicate) → boolean   [shim: (Lnet/minecraft/core/Holder;)Z ; (Lnet/minecraft/core/HolderSet;)Z ; (Lnet/minecraft/tags/TagKey;)Z ; (Lnet/minecraft/world/item/Item;)Z]`
- `net.minecraft.world.level.levelgen.placement.PlacedFeature`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `net.minecraft.world.level.levelgen.feature.ConfiguredFeature`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `net.minecraft.world.level.chunk.LevelChunk`
  - 메서드 `getBlockState(int, int, int) → BlockState   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;]`
  - 메서드 `getFluidIfLoaded(BlockPos) → FluidState   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;]`
- `net.minecraft.world.level.LevelReader`
  - 메서드 `getHeight() → int   [shim: ()I ; (Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I]`
  - 메서드 `getMinBuildHeight() → int   [shim: ()I]`
- `org.bukkit.craftbukkit.block.CraftDecoratedPot`
  - 메서드 `getInventory() → DecoratedPotInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
  - 메서드 `getSnapshotInventory() → DecoratedPotInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
- `org.bukkit.craftbukkit.block.CraftJukebox`
  - 메서드 `getInventory() → JukeboxInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
  - 메서드 `getSnapshotInventory() → JukeboxInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
- `net.minecraft.network.chat.FilterMask`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `net.minecraft.stats.RecipeBookSettings`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `org.bukkit.craftbukkit.entity.CraftEnderman`
  - 메서드 `getHandle() → EnderMan   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
  - 메서드 `teleport() → boolean   [shim: (Lorg/bukkit/Location;)Z ; (Lorg/bukkit/Location;Lorg/bukkit/event/player/PlayerTeleportEvent$TeleportCause;)Z]`
- `org.bukkit.craftbukkit.entity.CraftLightningStrike`
  - 메서드 `getHandle() → LightningBolt   [shim: ()Lnet/minecraft/world/entity/Entity;]`
  - 메서드 `spigot() → LightningStrike$Spigot   [shim: ()Lorg/bukkit/command/CommandSender$Spigot; ; ()Lorg/bukkit/entity/Entity$Spigot;]`
- `net.minecraft.world.entity.animal.WolfVariant`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `org.bukkit.craftbukkit.block.CraftChiseledBookshelf`
  - 메서드 `getInventory() → ChiseledBookshelfInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
  - 메서드 `getSnapshotInventory() → ChiseledBookshelfInventory   [shim: ()Lorg/bukkit/inventory/Inventory;]`
- `net.minecraft.world.level.chunk.PalettedContainer`
  - 메서드 `write(FriendlyByteBuf, ChunkPacketInfo, int) → void   [shim: (Lnet/minecraft/network/FriendlyByteBuf;)V]`
  - 필드 `data : net.minecraft.world.level.chunk.PalettedContainer$Data   [shim: Lnet/minecraft/world/level/chunk/PalettedContainer$Data;]`
- `net.minecraft.world.level.chunk.LevelChunkSection`
  - 메서드 `write(FriendlyByteBuf, ChunkPacketInfo, int) → void   [shim: (Lnet/minecraft/network/FriendlyByteBuf;)V]`
  - 필드 `states : net.minecraft.world.level.chunk.PalettedContainer   [shim: Lnet/minecraft/world/level/chunk/PalettedContainer;]`
- `net.minecraft.core.HolderSet`
  - 메서드 `direct(Function, Collection) → HolderSet$Direct   [shim: (Ljava/util/List;)Lnet/minecraft/core/HolderSet$Direct; ; ([Lnet/minecraft/core/Holder;)Lnet/minecraft/core/HolderSet$Direct;]`
  - 메서드 `direct(Function, Object[]) → HolderSet$Direct   [shim: (Ljava/util/List;)Lnet/minecraft/core/HolderSet$Direct; ; ([Lnet/minecraft/core/Holder;)Lnet/minecraft/core/HolderSet$Direct;]`
- `net.minecraft.network.protocol.login.ClientboundGameProfilePacket`
  - 메서드 `gameProfile() → GameProfile   [shim: ()Ljava/lang/Object;]`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.world.level.chunk.status.ChunkStep`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `net.minecraft.core.RegistrationInfo`
  - 메서드 `equals(Object) → boolean   [shim: (Ljava/lang/Object;)Z]`
  - 메서드 `hashCode() → int   [shim: ()I]`
- `net.minecraft.network.protocol.game.ClientboundTabListPacket`
  - 필드 `footer : net.minecraft.network.chat.Component   [shim: Lnet/minecraft/network/chat/Component;]`
  - 필드 `header : net.minecraft.network.chat.Component   [shim: Lnet/minecraft/network/chat/Component;]`
- `net.minecraft.world.level.block.state.properties.IntegerProperty`
  - 필드 `max : int   [shim: I]`
  - 필드 `min : int   [shim: I]`
- `net.minecraft.server.level.ServerLevel`
  - 필드 `chunkSource : net.minecraft.server.level.ServerChunkCache   [shim: Lnet/minecraft/server/level/ServerChunkCache;]`
- `net.minecraft.world.item.Item`
  - 메서드 `getDescriptionId(ItemStack) → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.server.level.ServerChunkCache`
  - 메서드 `close() → void   [shim: (Z)V]`
- `net.minecraft.world.entity.player.Player`
  - 필드 `gameProfile : com.mojang.authlib.GameProfile   [shim: Lcom/mojang/authlib/GameProfile;]`
- `net.minecraft.world.level.chunk.ChunkAccess`
  - 메서드 `getBlockState(int, int, int) → BlockState   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;]`
- `net.minecraft.world.level.block.Block`
  - 필드 `stateDefinition : net.minecraft.world.level.block.state.StateDefinition   [shim: Lnet/minecraft/world/level/block/state/StateDefinition;]`
- `net.minecraft.server.level.ChunkMap`
  - 필드 `level : net.minecraft.server.level.ServerLevel   [shim: Lnet/minecraft/server/level/ServerLevel;]`
- `org.bukkit.craftbukkit.entity.CraftArmorStand`
  - 메서드 `getHandle() → ArmorStand   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.world.phys.shapes.VoxelShape`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `org.bukkit.craftbukkit.entity.CraftAreaEffectCloud`
  - 메서드 `getHandle() → AreaEffectCloud   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftFishHook`
  - 메서드 `getHandle() → FishingHook   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftMob`
  - 메서드 `getHandle() → Mob   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftZombie`
  - 메서드 `getHandle() → Zombie   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.CraftChunk`
  - 메서드 `getBlock(int, int, int) → Block   [shim: (III)Lorg/bukkit/block/Block;]`
- `net.minecraft.world.level.LevelAccessor`
  - 메서드 `hasChunk(int, int) → boolean   [shim: (II)Z]`
- `org.bukkit.craftbukkit.entity.CraftMinecartMobSpawner`
  - 메서드 `getHandle() → MinecartSpawner   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftPanda`
  - 메서드 `getHandle() → Panda   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftVillager`
  - 메서드 `getHandle() → Villager   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.world.level.storage.LevelStorageSource$LevelStorageAccess`
  - 메서드 `getLevelDirectory() → LevelStorageSource$LevelDirectory   [shim: ()Ljava/nio/file/Path;]`
- `org.bukkit.craftbukkit.block.CraftBlockEntityState`
  - 메서드 `getSnapshotNBT() → CompoundTag   [shim: ()Lorg/domi/furnace/api/nbt/NbtCompound;]`
- `org.bukkit.craftbukkit.entity.CraftBee`
  - 메서드 `getHandle() → Bee   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftBoat`
  - 메서드 `getHandle() → Boat   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftFirework`
  - 메서드 `getHandle() → FireworkRocketEntity   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftFox`
  - 메서드 `getHandle() → Fox   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftFallingBlock`
  - 메서드 `getHandle() → FallingBlockEntity   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftTextDisplay`
  - 메서드 `getHandle() → Display$TextDisplay   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftItem`
  - 메서드 `getHandle() → ItemEntity   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator`
  - 필드 `settings : net.minecraft.core.Holder   [shim: Lnet/minecraft/core/Holder;]`
- `net.minecraft.advancements.AdvancementProgress`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.world.level.storage.LevelStorageSource`
  - 필드 `baseDir : java.nio.file.Path   [shim: Ljava/nio/file/Path;]`
- `org.bukkit.craftbukkit.entity.CraftCreeper`
  - 메서드 `getHandle() → Creeper   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPiglin`
  - 메서드 `getHandle() → Piglin   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.world.level.BlockGetter`
  - 메서드 `getBlockEntity(BlockPos, BlockEntityType) → Optional   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;]`
- `org.bukkit.craftbukkit.entity.CraftItemFrame`
  - 메서드 `getHandle() → ItemFrame   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftVex`
  - 메서드 `getHandle() → Vex   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftAllay`
  - 메서드 `getHandle() → Allay   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftArrow`
  - 메서드 `getHandle() → Arrow   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftLlama`
  - 메서드 `getHandle() → Llama   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftMushroomCow`
  - 메서드 `getHandle() → MushroomCow   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWither`
  - 메서드 `getHandle() → WitherBoss   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftZoglin`
  - 메서드 `getHandle() → Zoglin   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.core.SectionPos`
  - 메서드 `of(EntityAccess) → SectionPos   [shim: (III)Lnet/minecraft/core/SectionPos; ; (J)Lnet/minecraft/core/SectionPos; ; (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/SectionPos; ; (Lnet/minecraft/core/Position;)Lnet/minecraft/core/SectionPos; ; (Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;]`
- `net.minecraft.world.phys.Vec3`
  - 메서드 `closerThan(Vec3, double, double) → boolean   [shim: (Lnet/minecraft/core/Position;D)Z]`
- `org.bukkit.craftbukkit.entity.CraftEnderDragon`
  - 메서드 `getHandle() → EnderDragon   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftTNTPrimed`
  - 메서드 `getHandle() → PrimedTnt   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftTropicalFish`
  - 메서드 `getHandle() → TropicalFish   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftVillagerZombie`
  - 메서드 `getHandle() → ZombieVillager   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWolf`
  - 메서드 `getHandle() → Wolf   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftAgeable`
  - 메서드 `getHandle() → AgeableMob   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftEnderSignal`
  - 메서드 `getHandle() → EyeOfEnder   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftGuardian`
  - 메서드 `getHandle() → Guardian   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftStrider`
  - 메서드 `getHandle() → Strider   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWarden`
  - 메서드 `getHandle() → Warden   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftCat`
  - 메서드 `getHandle() → Cat   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftHorse`
  - 메서드 `getHandle() → Horse   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftInteraction`
  - 메서드 `getHandle() → Interaction   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftShulkerBullet`
  - 메서드 `getHandle() → ShulkerBullet   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftTrident`
  - 메서드 `getHandle() → ThrownTrident   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftWanderingTrader`
  - 메서드 `getHandle() → WanderingTrader   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.world.level.GameType`
  - 메서드 `byName(String, GameType) → GameType   [shim: (Ljava/lang/String;)Lnet/minecraft/world/level/GameType;]`
- `org.bukkit.craftbukkit.entity.CraftAnimals`
  - 메서드 `getHandle() → Animal   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftExperienceOrb`
  - 메서드 `getHandle() → ExperienceOrb   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftGoat`
  - 메서드 `getHandle() → Goat   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftHoglin`
  - 메서드 `getHandle() → Hoglin   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPhantom`
  - 메서드 `getHandle() → Phantom   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPig`
  - 메서드 `getHandle() → Pig   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPigZombie`
  - 메서드 `getHandle() → ZombifiedPiglin   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSkeletonHorse`
  - 메서드 `getHandle() → SkeletonHorse   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSniffer`
  - 메서드 `getHandle() → Sniffer   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftTurtle`
  - 메서드 `getHandle() → Turtle   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.inventory.CraftItemFactory`
  - 메서드 `enchantWithLevels(ItemStack, int, RegistryKeySet, Random) → ItemStack   [shim: (Lorg/bukkit/inventory/ItemStack;IZLjava/util/Random;)Lorg/bukkit/inventory/ItemStack;]`
- `net.minecraft.nbt.CompoundTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket`
  - 메서드 `createPlayerInitializing(Collection, ServerPlayer) → ClientboundPlayerInfoUpdatePacket   [shim: (Ljava/util/Collection;)Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;]`
- `net.minecraft.resources.ResourceLocation`
  - 메서드 `withPath(UnaryOperator) → ResourceLocation   [shim: (Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;]`
- `org.bukkit.craftbukkit.entity.CraftDolphin`
  - 메서드 `getHandle() → Dolphin   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftMinecartFurnace`
  - 메서드 `getHandle() → MinecartFurnace   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftRavager`
  - 메서드 `getHandle() → Ravager   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftShulker`
  - 메서드 `getHandle() → Shulker   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftThrownPotion`
  - 메서드 `getHandle() → ThrownPotion   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `net.minecraft.nbt.Tag`
  - 필드 `TAG_ANY_NUMERIC : byte   [shim: I]`
- `org.bukkit.craftbukkit.entity.CraftCamel`
  - 메서드 `getHandle() → Camel   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftMinecartHopper`
  - 메서드 `getHandle() → MinecartHopper   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftMinecartTNT`
  - 메서드 `getHandle() → MinecartTNT   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftSkeleton`
  - 메서드 `getHandle() → Skeleton   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWitch`
  - 메서드 `getHandle() → Witch   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.nbt.LongArrayTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.StringTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.network.protocol.game.ClientboundSystemChatPacket`
  - 메서드 `isSkippable() → boolean   [shim: ()Z]`
- `net.minecraft.world.level.LevelSettings`
  - 필드 `hardcore : boolean   [shim: Z]`
- `org.bukkit.craftbukkit.entity.CraftAxolotl`
  - 메서드 `getHandle() → Axolotl   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftBat`
  - 메서드 `getHandle() → Bat   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftChicken`
  - 메서드 `getHandle() → Chicken   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftEnderCrystal`
  - 메서드 `getHandle() → EndCrystal   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftEndermite`
  - 메서드 `getHandle() → Endermite   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftEvoker`
  - 메서드 `getHandle() → Evoker   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftEvokerFangs`
  - 메서드 `getHandle() → EvokerFangs   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftFrog`
  - 메서드 `getHandle() → Frog   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftGhast`
  - 메서드 `getHandle() → Ghast   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftItemDisplay`
  - 메서드 `getHandle() → Display$ItemDisplay   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftLeash`
  - 메서드 `getHandle() → LeashFenceKnotEntity   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftOcelot`
  - 메서드 `getHandle() → Ocelot   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftOminousItemSpawner`
  - 메서드 `getHandle() → OminousItemSpawner   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftPainting`
  - 메서드 `getHandle() → Painting   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftRabbit`
  - 메서드 `getHandle() → Rabbit   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSheep`
  - 메서드 `getHandle() → Sheep   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSlime`
  - 메서드 `getHandle() → Slime   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftTadpole`
  - 메서드 `getHandle() → Tadpole   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.nbt.ByteArrayTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.ByteTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.DoubleTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.EndTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.FloatTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.IntArrayTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.IntTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.ListTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.LongTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.nbt.ShortTag`
  - 메서드 `getType() → TagType   [shim: ()Lnet/minecraft/nbt/TagType;]`
- `net.minecraft.network.protocol.game.ClientboundAwardStatsPacket`
  - 메서드 `stats() → Object2IntMap   [shim: ()Ljava/lang/Object;]`
- `net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket`
  - 필드 `blockState : net.minecraft.world.level.block.state.BlockState   [shim: Lnet/minecraft/world/level/block/state/BlockState;]`
- `net.minecraft.network.protocol.game.ServerboundContainerClickPacket`
  - 메서드 `getChangedSlots() → Int2ObjectMap   [shim: ()Ljava/lang/Object;]`
- `org.bukkit.craftbukkit.entity.CraftBogged`
  - 메서드 `getHandle() → Bogged   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftParrot`
  - 메서드 `getHandle() → Parrot   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.network.chat.TextColor`
  - 필드 `name : java.lang.String   [shim: Ljava/lang/String;]`
- `net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket`
  - 메서드 `create(BlockEntity) → ClientboundBlockEntityDataPacket   [shim: (Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/function/BiFunction;)Lnet/minecraft/network/protocol/game/ClientboundBlockEntityDataPacket;]`
- `net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket`
  - 메서드 `isSkippable() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ClientboundMoveEntityPacket`
  - 메서드 `toString() → String   [shim: ()Ljava/lang/String;]`
- `net.minecraft.network.protocol.game.ClientboundPlayerChatPacket`
  - 메서드 `isSkippable() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket`
  - 메서드 `isSkippable() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket`
  - 필드 `pos : net.minecraft.core.BlockPos   [shim: Lnet/minecraft/core/BlockPos;]`
- `net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ClientboundTagQueryPacket`
  - 메서드 `isSkippable() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.handshake.ClientIntentionPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket`
  - 메서드 `isTerminal() → boolean   [shim: ()Z]`
- `net.minecraft.world.level.ChunkPos`
  - 메서드 `getChessboardDistance(int, int) → int   [shim: (Lnet/minecraft/world/level/ChunkPos;)I]`
- `net.minecraft.world.level.material.MapColor`
  - 필드 `MATERIAL_COLORS : net.minecraft.world.level.material.MapColor[]   [shim: [Lnet/minecraft/world/level/material/MapColor;]`
- `org.bukkit.craftbukkit.entity.CraftBlockDisplay`
  - 메서드 `getHandle() → Display$BlockDisplay   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftGlowSquid`
  - 메서드 `getHandle() → GlowSquid   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftIronGolem`
  - 메서드 `getHandle() → IronGolem   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPolarBear`
  - 메서드 `getHandle() → PolarBear   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftPufferFish`
  - 메서드 `getHandle() → Pufferfish   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSnowman`
  - 메서드 `getHandle() → SnowGolem   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSpectralArrow`
  - 메서드 `getHandle() → SpectralArrow   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftVindicator`
  - 메서드 `getHandle() → Vindicator   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWitherSkull`
  - 메서드 `getHandle() → WitherSkull   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `net.minecraft.ChatFormatting`
  - 필드 `code : char   [shim: C]`
- `net.minecraft.core.BlockPos$MutableBlockPos`
  - 메서드 `set(AxisCycle, int, int, int) → BlockPos$MutableBlockPos   [shim: (DDD)Lnet/minecraft/core/BlockPos$MutableBlockPos; ; (III)Lnet/minecraft/core/BlockPos$MutableBlockPos; ; (J)Lnet/minecraft/core/BlockPos$MutableBlockPos; ; (Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos$MutableBlockPos;]`
- `net.minecraft.core.HolderSet$ListBacked`
  - 메서드 `spliterator() → Spliterator   [shim: ()Ljava/util/Spliterator;]`
- `net.minecraft.network.chat.MutableComponent`
  - 메서드 `create(ComponentContents) → MutableComponent   [shim: (Lorg/domi/furnace/api/text/Content;)Lnet/minecraft/network/chat/MutableComponent;]`
- `org.bukkit.craftbukkit.entity.CraftChestBoat`
  - 메서드 `getHandle() → ChestBoat   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftLargeFireball`
  - 메서드 `getHandle() → LargeFireball   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftPillager`
  - 메서드 `getHandle() → Pillager   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `net.minecraft.core.Registry$2`
  - 메서드 `getOrThrow(TagKey) → HolderSet$Named   [shim: (Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference; ; (Lnet/minecraft/tags/TagKey;)Lnet/minecraft/core/HolderSet$Named;]`
- `net.minecraft.world.level.block.state.StateDefinition`
  - 메서드 `getPossibleStates() → ImmutableList   [shim: ()Ljava/util/List;]`
- `net.minecraft.world.level.chunk.PalettedContainerRO`
  - 메서드 `write(FriendlyByteBuf, ChunkPacketInfo, int) → void   [shim: (Lnet/minecraft/network/FriendlyByteBuf;)V]`
- `net.minecraft.world.level.chunk.status.ChunkPyramid`
  - 메서드 `steps() → ImmutableList   [shim: ()Ljava/util/List;]`
- `org.bukkit.craftbukkit.entity.CraftArmadillo`
  - 메서드 `getHandle() → Armadillo   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftBlaze`
  - 메서드 `getHandle() → Blaze   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftBreeze`
  - 메서드 `getHandle() → Breeze   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftBreezeWindCharge`
  - 메서드 `getHandle() → BreezeWindCharge   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftCaveSpider`
  - 메서드 `getHandle() → CaveSpider   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftCod`
  - 메서드 `getHandle() → Cod   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftCow`
  - 메서드 `getHandle() → Cow   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftCreature`
  - 메서드 `getHandle() → PathfinderMob   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftDrowned`
  - 메서드 `getHandle() → Drowned   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftEgg`
  - 메서드 `getHandle() → ThrownEgg   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftEnderPearl`
  - 메서드 `getHandle() → ThrownEnderpearl   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftGiant`
  - 메서드 `getHandle() → Giant   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftGlowItemFrame`
  - 메서드 `getHandle() → GlowItemFrame   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftIllusioner`
  - 메서드 `getHandle() → Illusioner   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftLlamaSpit`
  - 메서드 `getHandle() → LlamaSpit   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftMagmaCube`
  - 메서드 `getHandle() → MagmaCube   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftMarker`
  - 메서드 `getHandle() → Marker   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftPiglinBrute`
  - 메서드 `getHandle() → PiglinBrute   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftProjectile`
  - 메서드 `getHandle() → Projectile   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftSalmon`
  - 메서드 `getHandle() → Salmon   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSilverfish`
  - 메서드 `getHandle() → Silverfish   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSmallFireball`
  - 메서드 `getHandle() → SmallFireball   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftSnowball`
  - 메서드 `getHandle() → Snowball   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftSpider`
  - 메서드 `getHandle() → Spider   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftSquid`
  - 메서드 `getHandle() → Squid   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftThrownExpBottle`
  - 메서드 `getHandle() → ThrownExperienceBottle   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `org.bukkit.craftbukkit.entity.CraftTraderLlama`
  - 메서드 `getHandle() → TraderLlama   [shim: ()Lnet/minecraft/world/entity/Entity; ; ()Lnet/minecraft/world/entity/LivingEntity;]`
- `org.bukkit.craftbukkit.entity.CraftWindCharge`
  - 메서드 `getHandle() → WindCharge   [shim: ()Lnet/minecraft/world/entity/Entity;]`
- `net.minecraft.network.chat.Component$Serializer`
  - 메서드 `fromJson(JsonElement, HolderLookup$Provider) → MutableComponent   [shim: (Ljava/lang/String;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/network/chat/MutableComponent;]`
- `net.minecraft.network.syncher.EntityDataSerializers$1`
  - 메서드 `copy(ItemStack) → ItemStack   [shim: (Ljava/lang/Object;)Ljava/lang/Object;]`
- `net.minecraft.world.level.EmptyBlockGetter`
  - 메서드 `getFluidIfLoaded(BlockPos) → FluidState   [shim: (Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;]`
- `net.minecraft.world.level.chunk.PalettedContainer$Data`
  - 메서드 `write(FriendlyByteBuf, ChunkPacketInfo, int) → void   [shim: (Lnet/minecraft/network/FriendlyByteBuf;)V]`

### 클래스별 빠진 멤버 수 (상위 60 — 플러그인이 쓴 것이 많은 순)

| 클래스 | Paper 메서드 | 필드 | 메서드 없음 | 필드 없음 | 코퍼스가 쓴 구멍 |
|---|--:|--:|--:|--:|--:|
| `net.minecraft.world.entity.Entity` | 485 | 91 | 461 | 91 | 7 |
| `net.minecraft.server.level.ServerLevel` | 162 | 12 | 143 | 9 | 6 |
| `net.minecraft.world.item.ItemStack` | 120 | 12 | 91 | 11 | 4 |
| `net.minecraft.server.MinecraftServer` | 193 | 42 | 182 | 39 | 1 |
| `net.minecraft.server.level.ServerPlayer` | 177 | 42 | 174 | 41 | 1 |
| `net.minecraft.world.entity.EntityType` | 47 | 134 | 44 | 134 | 1 |
| `net.minecraft.world.item.Item` | 57 | 6 | 48 | 6 | 1 |
| `org.bukkit.craftbukkit.block.data.CraftBlockData` | 53 | 0 | 38 | 0 | 1 |
| `net.minecraft.server.level.ServerChunkCache` | 52 | 6 | 35 | 4 | 1 |
| `net.minecraft.world.level.chunk.ChunkGenerator` | 29 | 3 | 29 | 3 | 1 |
| `net.minecraft.world.level.levelgen.structure.Structure` | 18 | 3 | 18 | 3 | 1 |
| `net.minecraft.world.level.levelgen.placement.PlacedFeature` | 9 | 4 | 5 | 4 | 1 |
| `net.minecraft.world.level.levelgen.feature.ConfiguredFeature` | 8 | 3 | 2 | 3 | 1 |
| `net.minecraft.world.entity.LivingEntity` | 319 | 92 | 313 | 92 | 0 |
| `org.bukkit.craftbukkit.entity.CraftPlayer` | 371 | 1 | 263 | 1 | 0 |
| `net.minecraft.world.entity.player.Player` | 205 | 50 | 202 | 49 | 0 |
| `org.bukkit.craftbukkit.CraftServer` | 247 | 12 | 170 | 12 | 0 |
| `org.bukkit.craftbukkit.CraftWorld` | 276 | 1 | 146 | 1 | 0 |
| `net.minecraft.world.level.Level` | 172 | 44 | 126 | 39 | 0 |
| `net.minecraft.network.FriendlyByteBuf` | 282 | 4 | 63 | 4 | 0 |
| `org.bukkit.craftbukkit.entity.CraftLivingEntity` | 147 | 0 | 126 | 0 | 0 |
| `org.bukkit.craftbukkit.entity.CraftEntity` | 163 | 4 | 95 | 4 | 0 |
| `net.minecraft.network.protocol.game.ClientGamePacketListener` | 110 | 0 | 110 | 0 | 0 |
| `net.minecraft.SharedConstants` | 6 | 116 | 1 | 89 | 0 |
| `net.minecraft.core.registries.Registries` | 5 | 111 | 5 | 76 | 0 |
| `org.bukkit.craftbukkit.entity.CraftHumanEntity` | 93 | 1 | 67 | 0 | 0 |
| `net.minecraft.world.level.chunk.ChunkAccess` | 74 | 17 | 61 | 15 | 0 |
| `net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase` | 106 | 3 | 69 | 2 | 0 |
| `net.minecraft.core.registries.BuiltInRegistries` | 3 | 80 | 3 | 73 | 0 |
| `net.minecraft.server.network.ServerGamePacketListenerImpl` | 76 | 2 | 74 | 1 | 0 |
| `net.minecraft.world.level.levelgen.feature.Feature` | 13 | 62 | 13 | 62 | 0 |
| `net.minecraft.world.level.block.state.BlockBehaviour` | 61 | 12 | 61 | 12 | 0 |
| `net.minecraft.world.level.block.Block` | 66 | 17 | 51 | 15 | 0 |
| `net.minecraft.server.dedicated.DedicatedServer` | 63 | 2 | 62 | 2 | 0 |
| `net.minecraft.world.level.storage.PrimaryLevelData` | 64 | 6 | 57 | 5 | 0 |
| `net.minecraft.world.level.GameRules` | 9 | 52 | 8 | 52 | 0 |
| `net.minecraft.network.Connection` | 46 | 21 | 36 | 20 | 0 |
| `org.bukkit.craftbukkit.util.CraftMagicNumbers` | 69 | 2 | 53 | 1 | 0 |
| `net.minecraft.server.level.ChunkMap` | 52 | 12 | 48 | 9 | 0 |
| `net.minecraft.world.level.block.entity.StructureBlockEntity` | 44 | 15 | 43 | 15 | 0 |
| `net.minecraft.util.Mth` | 97 | 10 | 54 | 3 | 0 |
| `org.bukkit.craftbukkit.entity.CraftArmorStand` | 63 | 0 | 50 | 0 | 0 |
| `net.minecraft.core.component.DataComponents` | 2 | 58 | 2 | 48 | 0 |
| `net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity` | 31 | 19 | 31 | 19 | 0 |
| `org.bukkit.craftbukkit.block.CraftBlock` | 87 | 0 | 44 | 0 | 0 |
| `net.minecraft.network.protocol.game.ServerGamePacketListener` | 49 | 0 | 49 | 0 | 0 |
| `net.minecraft.world.level.block.entity.BlockEntityType` | 6 | 45 | 4 | 45 | 0 |
| `net.minecraft.world.level.border.WorldBorder` | 46 | 4 | 40 | 3 | 0 |
| `net.minecraft.world.entity.animal.armadillo.Armadillo` | 39 | 6 | 39 | 6 | 0 |
| `net.minecraft.world.entity.animal.sniffer.Sniffer` | 39 | 5 | 39 | 5 | 0 |
| `org.bukkit.craftbukkit.CraftOfflinePlayer` | 54 | 0 | 41 | 0 | 0 |
| `net.minecraft.world.level.block.entity.CrafterBlockEntity` | 30 | 10 | 29 | 10 | 0 |
| `net.minecraft.world.level.saveddata.maps.MapItemSavedData` | 23 | 16 | 23 | 16 | 0 |
| `net.minecraft.world.level.block.entity.DecoratedPotBlockEntity` | 30 | 9 | 29 | 9 | 0 |
| `net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity` | 28 | 10 | 27 | 10 | 0 |
| `net.minecraft.server.network.ServerCommonPacketListenerImpl` | 32 | 8 | 24 | 7 | 0 |
| `net.minecraft.world.level.dimension.DimensionType` | 29 | 11 | 22 | 11 | 0 |
| `net.minecraft.world.phys.shapes.VoxelShape` | 36 | 1 | 34 | 1 | 0 |
| `net.minecraft.world.level.chunk.LevelChunk` | 48 | 4 | 32 | 1 | 0 |
| `org.bukkit.craftbukkit.entity.CraftAreaEffectCloud` | 36 | 0 | 34 | 0 | 0 |

### shim 에 없는 Paper 클래스 (패키지별 개수)

- `net.minecraft.world.level.block` 288개
- `net.minecraft.util.datafix.fixes` 208개
- `org.bukkit.craftbukkit.block.impl` 173개
- `net.minecraft.world.item` 118개
- `net.minecraft.world.entity.ai.behavior` 99개
- `net.minecraft.advancements.critereon` 95개
- `net.minecraft.util.datafix.schemas` 90개
- `net.minecraft.server.commands` 86개
- `org.bukkit.craftbukkit.inventory` 79개
- `net.minecraft.util` 65개
- `net.minecraft.world.level.levelgen.feature` 65개
- `net.minecraft.world.entity.ai.goal` 62개
- `net.minecraft.world.inventory` 54개
- `org.bukkit.craftbukkit.block.data.type` 54개
- `net.minecraft.world.entity` 51개
- `net.minecraft.world.level.storage.loot.functions` 47개
- `net.minecraft.world.item.crafting` 44개
- `net.minecraft.world.entity.monster` 42개
- `net.minecraft.gametest.framework` 37개
- `net.minecraft.commands.arguments` 36개
- `org.bukkit.craftbukkit.util` 36개
- `net.minecraft.world.level` 34개
- `org.bukkit.craftbukkit.entity` 34개
- `net.minecraft.world.entity.animal` 33개
- `net.minecraft.world.level.levelgen` 33개
- `net.minecraft.world.level.levelgen.feature.configurations` 33개
- `net.minecraft.world.level.levelgen.structure.templatesystem` 33개
- `net.minecraft.world.level.levelgen.structure.structures` 32개
- `net.minecraft.data.worldgen` 29개
- `net.minecraft.world.entity.projectile` 28개
- `net.minecraft.world.entity.ai.sensing` 26개
- `net.minecraft.network` 25개
- `net.minecraft.world.level.storage.loot.predicates` 25개
- `net.minecraft.server` 24개
- `net.minecraft.data.tags` 23개
- `net.minecraft.world.item.enchantment.effects` 23개
- `net.minecraft.world.level.levelgen.placement` 23개
- `org.bukkit.craftbukkit` 23개
- `net.minecraft.network.chat` 22개
- `net.minecraft.server.level` 22개

전체 목록은 같은 이름의 `.tsv` 파일에 있다. "Paper 에도 없음"·"표 모름"은 위에 싣지 않았다.
