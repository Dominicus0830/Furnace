# NMS 소스 조사 표 (생성 파일, 2026-11-11)

`python3 -I tools/compat/nms-source-survey.py <클론 폴더> furnace-compat-paper/src/main/java` 가 만든 표다. 직접 고치지 않는다.
해석과 계획은 [`compat-nms-adapter-plan-2026-11-11.md`](compat-nms-adapter-plan-2026-11-11.md).

표본: 널리 쓰이는 플러그인 10개의 1.21.1 대응 NMS 모듈 **소스**(GitHub). FAWE 2.16.2 `adapter-1_21`, WorldEdit `adapter-1.21`(2024-11-09),
Citizens2 `v1_21_R2`(1.21.1 모듈이 지워져 1.21.3 판으로 근사), Shopkeepers `v1_21_R1`, Denizen `v1_21`(1.21.1 판), dynmap `bukkit-helper-121`,
DecentHolograms `nms-v1_21_R1`, TAB `bukkit/v1_21_R1`, LibsDisguises `nms/legacy/v1_21_R1`, squaremap `paper`+`common`(1.21.3 판).
클래스 단위로 "그 클래스를 쓰는 플러그인 수"를 센다(멤버·리플렉션 문자열은 세지 않는다). **Spigot 이름 클래스(dynmap·TAB·DecentHolograms 의 `IBlockData`·`MinecraftKey`·`PacketPlayOut…`)는 Mojang 이름과 따로 세어져 클래스별 숫자가 낮다** — 독립 검토의 수작업 합산으로는 `ResourceLocation` 9, `BlockState`·`Component`·`ServerLevel`·`Registry` 7, `LevelChunk`·`ChunkAccess` 5, 게임 패킷은 서로 다른 40종(2개 이상이 쓰는 것 15종). "이미 shim" 은 `furnace-compat-paper` 에 같은 이름 클래스가 있는 수.


## 패키지 묶음별 (2개 이상 플러그인)

| 묶음 | 플러그인 수 | 클래스 수 | 이미 shim | 플러그인 |
|---|---:|---:|---:|---|
| `craftbukkit.entity` | 10 | 134 | 6 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, Shopkeepers, TAB, WorldEdit, dynmap, squaremap |
| `resources` | 9 | 4 | 1 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, TAB, WorldEdit, dynmap, squaremap |
| `nbt` | 8 | 32 | 16 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, dynmap, squaremap |
| `world.entity` | 8 | 23 | 2 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, squaremap |
| `server.level` | 8 | 14 | 2 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, dynmap, squaremap |
| `craftbukkit.(root)` | 8 | 8 | 2 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, WorldEdit, dynmap, squaremap |
| `core.registries` | 8 | 2 | 0 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, WorldEdit, dynmap, squaremap |
| `world.level.block` | 7 | 32 | 0 | Citizens, DecentHolograms, Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `core` | 7 | 24 | 5 | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, dynmap, squaremap |
| `world.level` | 7 | 20 | 2 | Citizens, Denizen, FAWE, TAB, WorldEdit, dynmap, squaremap |
| `com.mojang.authlib` | 7 | 11 | 0 | Citizens, Denizen, FAWE, LibsDisguises, TAB, WorldEdit, dynmap |
| `network.chat` | 7 | 10 | 2 | Citizens, DecentHolograms, Denizen, FAWE, TAB, WorldEdit, squaremap |
| `craftbukkit.inventory` | 7 | 9 | 2 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit |
| `server` | 7 | 4 | 1 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, dynmap |
| `world.entity.player` | 7 | 3 | 1 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, squaremap |
| `network.protocol.game` | 6 | 46 | 6 | Citizens, DecentHolograms, Denizen, FAWE, TAB, WorldEdit |
| `world.level.chunk` | 6 | 19 | 0 | Citizens, Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `util` | 6 | 12 | 0 | Citizens, Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `network` | 6 | 9 | 1 | Citizens, DecentHolograms, Denizen, LibsDisguises, TAB, squaremap |
| `world.item` | 6 | 7 | 2 | Citizens, DecentHolograms, Denizen, FAWE, Shopkeepers, WorldEdit |
| `(root)` | 6 | 6 | 0 | Citizens, DecentHolograms, Denizen, FAWE, TAB, WorldEdit |
| `com.mojang.serialization` | 6 | 5 | 0 | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, squaremap |
| `world.phys` | 6 | 5 | 1 | Citizens, DecentHolograms, Denizen, FAWE, WorldEdit, squaremap |
| `tags` | 5 | 7 | 0 | Citizens, Denizen, FAWE, dynmap, squaremap |
| `world.level.biome` | 5 | 7 | 0 | Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `craftbukkit.util` | 5 | 5 | 0 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers |
| `com.mojang.datafixers` | 5 | 4 | 0 | Citizens, DecentHolograms, Denizen, FAWE, WorldEdit |
| `world.entity.ai` | 4 | 19 | 0 | Citizens, Denizen, Shopkeepers, squaremap |
| `world.level.levelgen` | 4 | 11 | 0 | Denizen, FAWE, WorldEdit, squaremap |
| `world.scores` | 4 | 9 | 0 | Citizens, Denizen, LibsDisguises, TAB |
| `world` | 4 | 8 | 0 | Citizens, Denizen, FAWE, WorldEdit |
| `world.level.storage` | 4 | 8 | 0 | Denizen, FAWE, WorldEdit, squaremap |
| `core.component` | 4 | 5 | 2 | Denizen, FAWE, Shopkeepers, WorldEdit |
| `server.network` | 4 | 5 | 2 | Citizens, DecentHolograms, Denizen, TAB |
| `world.level.material` | 4 | 5 | 0 | Citizens, Denizen, FAWE, squaremap |
| `network.syncher` | 4 | 4 | 0 | Citizens, DecentHolograms, Denizen, LibsDisguises |
| `stats` | 4 | 3 | 0 | Citizens, Denizen, FAWE, WorldEdit |
| `world.damagesource` | 4 | 3 | 0 | Citizens, Denizen, FAWE, WorldEdit |
| `world.level.dimension` | 4 | 3 | 0 | Citizens, FAWE, WorldEdit, squaremap |
| `network.protocol` | 4 | 2 | 1 | Citizens, DecentHolograms, Denizen, TAB |
| `server.dedicated` | 4 | 2 | 1 | Citizens, Denizen, FAWE, WorldEdit |
| `craftbukkit.block.data` | 4 | 1 | 0 | Citizens, Denizen, FAWE, WorldEdit |
| `craftbukkit.block` | 3 | 6 | 0 | Citizens, Denizen, FAWE |
| `world.entity.decoration` | 3 | 6 | 0 | Citizens, DecentHolograms, Denizen |
| `world.entity.item` | 3 | 4 | 0 | Citizens, DecentHolograms, Denizen |
| `util.datafix.fixes` | 3 | 1 | 0 | Denizen, FAWE, WorldEdit |
| `world.entity.animal` | 2 | 43 | 0 | Citizens, Denizen |
| `world.entity.monster` | 2 | 41 | 0 | Citizens, Denizen |
| `world.entity.projectile` | 2 | 22 | 0 | Citizens, Denizen |
| `world.entity.vehicle` | 2 | 12 | 0 | Citizens, Denizen |
| `world.level.pathfinder` | 2 | 11 | 0 | Citizens, Denizen |
| `world.entity.boss` | 2 | 5 | 0 | Citizens, Denizen |
| `world.inventory` | 2 | 5 | 0 | Citizens, Denizen |
| `server.players` | 2 | 4 | 0 | Citizens, Denizen |
| `world.entity.npc` | 2 | 3 | 0 | Citizens, Denizen |
| `advancements` | 2 | 2 | 0 | Citizens, Denizen |
| `commands.arguments` | 2 | 2 | 0 | Denizen, squaremap |
| `network.chat.contents` | 2 | 2 | 0 | Citizens, DecentHolograms |
| `network.chat.numbers` | 2 | 2 | 0 | Denizen, TAB |
| `network.protocol.configuration` | 2 | 2 | 0 | Denizen, LibsDisguises |
| `sounds` | 2 | 2 | 0 | Citizens, FAWE |
| `world.item.context` | 2 | 2 | 0 | FAWE, WorldEdit |
| `world.item.enchantment` | 2 | 2 | 0 | Citizens, Denizen |
| `world.item.trading` | 2 | 2 | 0 | Citizens, Shopkeepers |
| `world.level.lighting` | 2 | 2 | 0 | Denizen, FAWE |
| `world.level.saveddata` | 2 | 2 | 0 | Denizen, squaremap |
| `world.phys.shapes` | 2 | 2 | 0 | Citizens, Denizen |
| `world.scores.criteria` | 2 | 2 | 0 | Denizen, TAB |
| `com.mojang.brigadier` | 2 | 1 | 0 | Denizen, squaremap |
| `craftbukkit.boss` | 2 | 1 | 1 | Citizens, Denizen |
| `server.level.progress` | 2 | 1 | 0 | FAWE, WorldEdit |
| `util.datafix` | 2 | 1 | 0 | FAWE, WorldEdit |
| `util.thread` | 2 | 1 | 0 | FAWE, WorldEdit |
| `world.entity.ambient` | 2 | 1 | 0 | Citizens, Denizen |
| `world.flag` | 2 | 1 | 0 | FAWE, LibsDisguises |
| `world.level.border` | 2 | 1 | 0 | FAWE, squaremap |

## 클래스별 (4개 이상 플러그인)

| 클래스 | 플러그인 수 | shim | 플러그인 |
|---|---:|---|---|
| `org.bukkit.craftbukkit.entity.CraftPlayer` | 9 | 있음 | Citizens, DecentHolograms, Denizen, FAWE, Shopkeepers, TAB, WorldEdit, dynmap, squaremap |
| `net.minecraft.world.entity.Entity` | 8 | 있음 | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, squaremap |
| `com.mojang.authlib.GameProfile` | 7 |  | Citizens, Denizen, FAWE, LibsDisguises, TAB, WorldEdit, dynmap |
| `net.minecraft.core.registries.BuiltInRegistries` | 7 |  | Citizens, DecentHolograms, Denizen, FAWE, LibsDisguises, dynmap, squaremap |
| `net.minecraft.nbt.Tag` | 7 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, squaremap |
| `net.minecraft.server.level.ServerPlayer` | 7 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit, squaremap |
| `net.minecraft.world.level.block.Block` | 7 |  | Citizens, DecentHolograms, Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `org.bukkit.craftbukkit.CraftWorld` | 7 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, dynmap, squaremap |
| `net.minecraft.core.Registry` | 6 |  | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, squaremap |
| `net.minecraft.core.registries.Registries` | 6 |  | Denizen, FAWE, LibsDisguises, WorldEdit, dynmap, squaremap |
| `net.minecraft.network.chat.Component` | 6 | 있음 | Citizens, Denizen, FAWE, TAB, WorldEdit, squaremap |
| `net.minecraft.resources.ResourceLocation` | 6 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, squaremap |
| `net.minecraft.server.MinecraftServer` | 6 | 있음 | Citizens, Denizen, FAWE, Shopkeepers, WorldEdit, dynmap |
| `net.minecraft.server.level.ServerLevel` | 6 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit, squaremap |
| `net.minecraft.world.item.ItemStack` | 6 | 있음 | Citizens, DecentHolograms, Denizen, FAWE, Shopkeepers, WorldEdit |
| `org.bukkit.craftbukkit.entity.CraftEntity` | 6 | 있음 | Citizens, Denizen, FAWE, LibsDisguises, Shopkeepers, WorldEdit |
| `org.bukkit.craftbukkit.inventory.CraftItemStack` | 6 | 있음 | Citizens, DecentHolograms, Denizen, FAWE, Shopkeepers, WorldEdit |
| `net.minecraft.core.BlockPos` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.core.Direction` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.core.Holder` | 5 |  | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.nbt.CompoundTag` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.resources.ResourceKey` | 5 |  | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.server.level.ClientInformation` | 5 |  | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit |
| `net.minecraft.server.level.ServerChunkCache` | 5 |  | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit |
| `net.minecraft.world.entity.EntityType` | 5 |  | Citizens, Denizen, FAWE, LibsDisguises, WorldEdit |
| `net.minecraft.world.entity.player.Player` | 5 | 있음 | Citizens, Denizen, FAWE, Shopkeepers, squaremap |
| `net.minecraft.world.level.Level` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.block.Blocks` | 5 |  | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.block.state.BlockState` | 5 |  | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.chunk.status.ChunkStatus` | 5 |  | Denizen, FAWE, WorldEdit, dynmap, squaremap |
| `net.minecraft.world.phys.Vec3` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, squaremap |
| `org.bukkit.craftbukkit.CraftServer` | 5 | 있음 | Citizens, Denizen, FAWE, WorldEdit, dynmap |
| `com.mojang.authlib.properties.Property` | 4 |  | Citizens, Denizen, TAB, dynmap |
| `net.minecraft.core.HolderSet` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.core.SectionPos` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.nbt.ByteArrayTag` | 4 | 있음 | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.nbt.IntArrayTag` | 4 | 있음 | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.nbt.ListTag` | 4 | 있음 | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.nbt.NbtOps` | 4 |  | Denizen, FAWE, LibsDisguises, WorldEdit |
| `net.minecraft.nbt.StringTag` | 4 | 있음 | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.network.protocol.Packet` | 4 | 있음 | Citizens, DecentHolograms, Denizen, TAB |
| `net.minecraft.server.level.ChunkMap` | 4 |  | Citizens, Denizen, FAWE, LibsDisguises |
| `net.minecraft.world.InteractionHand` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.world.damagesource.DamageSource` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.world.entity.LivingEntity` | 4 | 있음 | Citizens, Denizen, LibsDisguises, Shopkeepers |
| `net.minecraft.world.item.Item` | 4 | 있음 | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.world.level.ChunkPos` | 4 | 있음 | Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.biome.Biome` | 4 |  | Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.chunk.ChunkAccess` | 4 |  | Denizen, FAWE, WorldEdit, squaremap |
| `net.minecraft.world.level.chunk.LevelChunk` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `net.minecraft.world.level.material.FluidState` | 4 |  | Citizens, Denizen, FAWE, squaremap |
| `org.bukkit.craftbukkit.block.data.CraftBlockData` | 4 |  | Citizens, Denizen, FAWE, WorldEdit |
| `org.bukkit.craftbukkit.util.CraftMagicNumbers` | 4 |  | Citizens, Denizen, LibsDisguises, Shopkeepers |
