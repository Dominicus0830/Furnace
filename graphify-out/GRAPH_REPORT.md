# Graph Report - Furnace  (2026-10-23)

## Corpus Check
- Large corpus: 2428 files · ~1,199,992 words. Semantic extraction will be expensive (many Claude tokens). Consider running on a subfolder.

## Summary
- 15667 nodes · 58554 edges · 281 communities (166 shown, 115 thin omitted)
- Extraction: 82% EXTRACTED · 18% INFERRED · 0% AMBIGUOUS · INFERRED: 10373 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Community 0
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Community 10
- Community 11
- Community 12
- Community 13
- Community 14
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40
- Community 41
- Community 42
- Community 43
- Community 44
- Community 45
- Community 46
- Community 47
- Community 48
- Community 49
- Community 50
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Community 57
- Community 58
- Community 59
- Community 60
- Community 61
- Community 62
- Community 63
- Community 64
- Community 65
- Community 66
- Community 67
- Community 68
- Community 69
- Community 70
- Community 71
- Community 72
- Community 73
- Community 74
- Community 75
- Community 76
- Community 77
- Community 78
- Community 79
- Community 80
- Community 81
- Community 82
- Community 83
- Community 84
- Community 85
- Community 86
- Community 87
- Community 88
- Community 89
- Community 90
- Community 91
- Community 92
- Community 93
- Community 94
- Community 95
- Community 96
- Community 97
- Community 98
- Community 99
- Community 100
- Community 101
- Community 102
- Community 103
- Community 104
- Community 105
- Community 106
- Community 107
- Community 108
- Community 109
- Community 110
- Community 111
- Community 113
- Community 114
- Community 115
- Community 116
- Community 117
- Community 118
- Community 119
- Community 120
- Community 121
- Community 122
- Community 123
- Community 124
- Community 125
- Community 126
- Community 127
- Community 128
- Community 129
- Community 130
- Community 131
- Community 132
- Community 133
- Community 134
- Community 135
- Community 136
- Community 137
- Community 138
- Community 139
- Community 140
- Community 141
- Community 142
- Community 143
- Community 144
- Community 145
- Community 146
- Community 147
- Community 148
- Community 149
- Community 150
- Community 151
- Community 152
- Community 153
- Community 154
- Community 155
- Community 156
- Community 157
- Community 158
- Community 159
- Community 160
- Community 161
- Community 162
- Community 163
- Community 164
- Community 165
- Community 166
- Community 167
- Community 168
- Community 169
- Community 170
- Community 171
- Community 172
- Community 173
- Community 174
- Community 175
- Community 176
- Community 177
- Community 178
- Community 179
- Community 180
- Community 181
- Community 182
- Community 183
- Community 185
- Community 186
- Community 187
- Community 188
- Community 189
- Community 190
- Community 191
- Community 192
- Community 193
- Community 194
- Community 195
- Community 196
- Community 197
- Community 198
- Community 199
- Community 200
- Community 201
- Community 202
- Community 203
- Community 204
- Community 205
- Community 206
- Community 207
- Community 208
- Community 209
- Community 210
- Community 211
- Community 212
- Community 213
- Community 214
- Community 215
- Community 216
- Community 217
- Community 218
- Community 220
- Community 221
- Community 222
- Community 223
- Community 224
- Community 225
- Community 226
- Community 227
- Community 228
- Community 229
- Community 230
- Community 231
- Community 232
- Community 233
- Community 234
- Community 235
- Community 236
- Community 237
- Community 238
- Community 239
- Community 240
- Community 241
- Community 242
- Community 243
- Community 244
- Community 245
- Community 246
- Community 248
- Community 249
- Community 250
- Community 251
- Community 252
- Community 254
- Community 255
- Community 256
- Community 257
- Community 258
- Community 259
- Community 260
- Community 261
- Community 262
- Community 263
- Community 264
- Community 265
- Community 266
- Community 267
- Community 268
- Community 269
- Community 270
- Community 271
- Community 272
- Community 273
- Community 274

## God Nodes (most connected - your core abstractions)
1. `Pos` - 1485 edges
2. `BlockLevel` - 1388 edges
3. `WorldgenRandom` - 465 edges
4. `Text` - 395 edges
5. `FeatureWorldView` - 378 edges
6. `BlockActor` - 334 edges
7. `ItemStack` - 334 edges
8. `Player` - 305 edges
9. `RandomSource` - 298 edges
10. `Prop` - 279 edges

## Surprising Connections (you probably didn't know these)
- `Env` --references--> `FurnaceSchedulerImpl`  [EXTRACTED]
  LLM-Wiki/output/furnace-parity-audit/tools/WorldPersistCheck.java → furnace-server/src/main/java/org/domi/furnace/server/sched/FurnaceSchedulerImpl.java
- `Env` --references--> `RegionFileStore`  [EXTRACTED]
  LLM-Wiki/output/furnace-parity-audit/tools/WorldPersistCheck.java → furnace-server/src/main/java/org/domi/furnace/server/world/anvil/RegionFileStore.java
- `Env` --references--> `World`  [EXTRACTED]
  LLM-Wiki/output/furnace-parity-audit/tools/WorldPersistCheck.java → furnace-server/src/main/java/org/domi/furnace/server/world/World.java
- `PluginHost` --implements--> `Furnace`  [EXTRACTED]
  furnace-server/src/main/java/org/domi/furnace/server/plugin/PluginHost.java → furnace-api/src/main/java/org/domi/furnace/api/Furnace.java
- `ConsoleSender` --implements--> `CommandSender`  [EXTRACTED]
  furnace-server/src/main/java/org/domi/furnace/server/plugin/ConsoleSender.java → furnace-api/src/main/java/org/domi/furnace/api/command/CommandSender.java

## Import Cycles
- None detected.

## Communities (281 total, 115 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.01
Nodes (75): SelectAdvancementsTab, broadcastAll(), broadcastToTrackers(), Types, BrandPayload, ConfigDisconnect, FeatureFlags, FinishConfiguration (+67 more)

### Community 1 - "Community 1"
Cohesion: 0.02
Nodes (43): B, BlockBehavior, Blocks, Line, Dir, Fluids, Hit, InteractionResult (+35 more)

### Community 2 - "Community 2"
Cohesion: 0.02
Nodes (28): BlockSetType, AbstractCandleBlock, AbstractSkullBlock, BeehiveBlock, ButtonBlock, CalibratedSculkSensorBlock, CandleBlock, CauldronBlock (+20 more)

### Community 3 - "Community 3"
Cohesion: 0.02
Nodes (20): FluidTicker, BambooStalkBlock, BigDripleafBlock, BigDripleafStemBlock, BubbleColumnBlock, BuddingAmethystBlock, CactusBlock, ChorusFlowerBlock (+12 more)

### Community 4 - "Community 4"
Cohesion: 0.02
Nodes (29): BlockLevel, DaylightDetectorBlockEntity, AbstractFurnaceBlock, BarrelBlock, BaseEntityBlock, BasePressurePlateBlock, BeaconBlock, BlastFurnaceBlock (+21 more)

### Community 5 - "Community 5"
Cohesion: 0.01
Nodes (38): Pos, AzaleaBlock, BambooSaplingBlock, BeetrootBlock, BonemealableBlock, BushBlock, CropBlock, DeadBushBlock (+30 more)

### Community 6 - "Community 6"
Cohesion: 0.02
Nodes (23): BlockActor, BlockBreaking, Host, BlockItems, Entry, BlockPlacement, Holder, ItemUse (+15 more)

### Community 7 - "Community 7"
Cohesion: 0.02
Nodes (32): AmethystClusterBlock, AttachedStemBlock, BaseCoralWallFanBlock, BaseTorchBlock, BedBlock, BellBlock, CarpetBlock, CocoaBlock (+24 more)

### Community 8 - "Community 8"
Cohesion: 0.03
Nodes (3): BlockStateKey, BlockStateTable, TemplatePlacer

### Community 9 - "Community 9"
Cohesion: 0.02
Nodes (39): UpdateAdvancements, Data, read(), Forwarded, VelocityForwarding, NetworkServer, KnownPacksClientbound, RegistryData (+31 more)

### Community 10 - "Community 10"
Cohesion: 0.03
Nodes (38): BlockPos, AlterGroundDecorator, AttachedToLeavesDecorator, BeehiveDecorator, CocoaDecorator, LeaveVineDecorator, Context, TreeDecorator (+30 more)

### Community 11 - "Community 11"
Cohesion: 0.03
Nodes (16): Eula, ConsoleInput, TagRegistries, ItemContext, ItemData, NetworkComponents, NbtIo, RegistrySet (+8 more)

### Community 12 - "Community 12"
Cohesion: 0.02
Nodes (41): WorldgenRandom, Claw, CoralFeature, Mushroom, Shape, CLAW, MUSHROOM, TREE (+33 more)

### Community 13 - "Community 13"
Cohesion: 0.03
Nodes (28): SingleAngle, BlockInput, MthTrig, Vec2, CommandContext, CommandDispatcher, Action, GRANT (+20 more)

### Community 14 - "Community 14"
Cohesion: 0.02
Nodes (25): AbstractChestBlock, BarrierBlock, BaseCoralFanBlock, BaseCoralPlantBlock, BaseCoralPlantTypeBlock, CampfireBlock, ChainBlock, ConduitBlock (+17 more)

### Community 15 - "Community 15"
Cohesion: 0.02
Nodes (57): Dimension, END, NETHER, OVERWORLD, HeightKind, MOTION_BLOCKING, OCEAN_FLOOR, WORLD_SURFACE (+49 more)

### Community 16 - "Community 16"
Cohesion: 0.03
Nodes (15): Columns, Pillar, FeatureWorldView, HugeFungusFeature, HugeMushroomFeature, MonsterRoomFeature, Blocks, NetherVinesFeatures (+7 more)

### Community 17 - "Community 17"
Cohesion: 0.03
Nodes (25): RegistrySetTest, WorldDump, BlockRegistryStateTest, BlockRegistryTest, ChunkMemoryMeasurement, ChunkGenBenchmark, FeaturePipelineParityTest, FullGenProfile (+17 more)

### Community 18 - "Community 18"
Cohesion: 0.04
Nodes (27): PlayerJoinEvent, Text, ValidationException, DecodeException, AngleArgument, BlockPosArgument, EntityArgument, GameProfileArgument (+19 more)

### Community 19 - "Community 19"
Cohesion: 0.04
Nodes (26): GlobalScheduler, ServerPropertiesFile, EntityIdAllocator, ChunkWorkerPool, FurnaceSchedulerImpl, Layer, SuperflatWorldSource, ChunkWorkerPoolTest (+18 more)

### Community 20 - "Community 20"
Cohesion: 0.02
Nodes (20): Audience, Audience, BasicCommand, CommandRegistry, CommandSender, Permissible, PluginManager, FurnaceScheduler (+12 more)

### Community 21 - "Community 21"
Cohesion: 0.03
Nodes (21): ConfigFileTest, MarkupEffectsTest, RandomTickTest, PlayerPendingChunksTest, PlayerPublishTest, PlayerTest, ConnectionHandlerSpawnGateTest, CompressionTest (+13 more)

### Community 22 - "Community 22"
Cohesion: 0.02
Nodes (6): MoveSample, Player, ChunkPos, RegionLevel, Subscription, World

### Community 23 - "Community 23"
Cohesion: 0.05
Nodes (11): BoundingBox, DesertPyramidPiece, Piece, Corridor, St, Piece, PostCtx, Piece (+3 more)

### Community 24 - "Community 24"
Cohesion: 0.04
Nodes (29): Coordinates, LocalCoordinates, World, BlockStateArgument, Dynamic3, Parsed, EntitySelector, Order (+21 more)

### Community 25 - "Community 25"
Cohesion: 0.03
Nodes (16): ArgumentTypeIds, ArgumentBuilder, ArgumentCommandNode, Command, CommandContextBuilder, CommandNode, ParsedArgument, ParsedCommandNode (+8 more)

### Community 26 - "Community 26"
Cohesion: 0.03
Nodes (18): Result, BanListEntry, GameProfile, Entry, GameProfileCache, IpBanList, IpBanListEntry, PlayerLists (+10 more)

### Community 27 - "Community 27"
Cohesion: 0.03
Nodes (13): EntityView, InventoryHost, Delayed, Region, org.domi.furnace.server.block.BreakerActor, Task, Regionizer, RegionWide (+5 more)

### Community 28 - "Community 28"
Cohesion: 0.04
Nodes (39): DensityFunction, Visitor, Ap2, CellBoundsOracle, CellBoundsOracleInto, Clamp, DensityFunctions, Mapped (+31 more)

### Community 29 - "Community 29"
Cohesion: 0.06
Nodes (16): LegacyRandom, FreeRef, FreeSpace, Base, Crossing, Kind, MESA, NORMAL (+8 more)

### Community 30 - "Community 30"
Cohesion: 0.04
Nodes (24): HoverEvent, ShowText, Builder, Style, Builder, TextDecoration, BOLD, ITALIC (+16 more)

### Community 31 - "Community 31"
Cohesion: 0.04
Nodes (54): Direction, EAST, NORTH, SOUTH, WEST, BridgeCrossing, BridgeEndFiller, BridgeStraight (+46 more)

### Community 32 - "Community 32"
Cohesion: 0.04
Nodes (46): PositionalRandom, SurfaceContext, AboveBottom, AbovePreliminarySurface, Absolute, Bandlands, BelowTop, BiomeIs (+38 more)

### Community 33 - "Community 33"
Cohesion: 0.03
Nodes (11): Abilities, DeathListener, PlayerVitals, Surroundings, PlayerAbilities, GameMode, ADVENTURE, CREATIVE (+3 more)

### Community 34 - "Community 34"
Cohesion: 0.03
Nodes (4): Damage, Entity, EntityStore, EntityType

### Community 35 - "Community 35"
Cohesion: 0.03
Nodes (19): BlockAttrs, BambooFeature, BasaltFeatures, BlockStates, BlockSurvival, BlueIceFeature, DeltaFeature, DesertWellFeature (+11 more)

### Community 36 - "Community 36"
Cohesion: 0.04
Nodes (22): Single, PlaceSettings, Ctx, Processor, ProcessorLists, BlackstoneReplace, BlockAge, BlockEntityModifier (+14 more)

### Community 37 - "Community 37"
Cohesion: 0.04
Nodes (21): EndCityStructure, Gens, Piece, SectionGenerator, IglooStructure, Piece, St, OceanRuinStructure (+13 more)

### Community 38 - "Community 38"
Cohesion: 0.04
Nodes (12): BiomeEntry, BiomeSampler, SinglePointContext, NoiseRouter, WorldgenData, BiomeSamplerParityTest, DensityFunctionParserTest, NoopFeatureAuditTest (+4 more)

### Community 39 - "Community 39"
Cohesion: 0.04
Nodes (9): BitStorage, ChestBlockEntity, ChunkColumn, GeneratedBlockEntityVisitor, Generated, ChunkColumnTest, FeatureWorldViewTest, OreFeatureTest (+1 more)

### Community 40 - "Community 40"
Cohesion: 0.04
Nodes (20): ContextProvider, FunctionContext, SimpleFunction, BlendAlpha, INSTANCE, BlendDensity, BlendOffset, INSTANCE (+12 more)

### Community 41 - "Community 41"
Cohesion: 0.05
Nodes (19): Criterion, InventoryEvent, LocationEvent, Matcher, FeatureFlags, Bounds, Enchantment, EntityPredicate (+11 more)

### Community 42 - "Community 42"
Cohesion: 0.05
Nodes (19): Arg, Argument, ArgumentQueue, Impl, ColorEffects, Context, Formatter, Composite (+11 more)

### Community 43 - "Community 43"
Cohesion: 0.03
Nodes (18): Times, Title, Permission, PermissionAttachment, PermissionDefault, FALSE, NOT_OP, OP (+10 more)

### Community 44 - "Community 44"
Cohesion: 0.04
Nodes (31): ResourceId, Resource, ResourceManager, Entry, TagLoader, RandomSequences, RecipeBookUpdate, Ingredient (+23 more)

### Community 46 - "Community 46"
Cohesion: 0.03
Nodes (17): GameMode, ADVENTURE, CREATIVE, SPECTATOR, SURVIVAL, Player, BlockBreakEvent, BlockEvent (+9 more)

### Community 47 - "Community 47"
Cohesion: 0.06
Nodes (4): PlainText, TextTemplateTest, TextToolsTest, TranslationTest

### Community 48 - "Community 48"
Cohesion: 0.04
Nodes (16): TimeArgument, WorldCommands, Env, Difficulty, EASY, HARD, NORMAL, PEACEFUL (+8 more)

### Community 49 - "Community 49"
Cohesion: 0.04
Nodes (21): DataPackManager, org.domi.furnace.server.recipe.RecipeContext, Loaded, Compatibility, COMPATIBLE, TOO_NEW, TOO_OLD, Pack (+13 more)

### Community 50 - "Community 50"
Cohesion: 0.07
Nodes (12): Loader, ConfiguredFeature, ForestRockFeature, PlacedFeature, RandomBooleanSelectorFeature, RandomPatchFeature, Entry, RandomSelectorFeature (+4 more)

### Community 51 - "Community 51"
Cohesion: 0.05
Nodes (8): ConnectionHandler, ConnectionState, CONFIGURATION, HANDSHAKE, LOGIN, PLAY, STATUS, TokenBucket

### Community 52 - "Community 52"
Cohesion: 0.03
Nodes (39): RandomTrace, Session, AllOfPred, AnyOfPred, BiasedToBottomHeight, BiomeFilter, BlockPred, CarvingMaskPlacement (+31 more)

### Community 53 - "Community 53"
Cohesion: 0.05
Nodes (15): Advancement, Rewards, AdvancementManager, AdvancementNode, AdvancementProgress, CriterionProgress, Listener, PlayerAdvancements (+7 more)

### Community 54 - "Community 54"
Cohesion: 0.05
Nodes (21): Aquifer, CanyonWorldCarver, Canyon, CarverConfig, Cave, CarvingMask, CaveWorldCarver, ClampedNormal (+13 more)

### Community 55 - "Community 55"
Cohesion: 0.05
Nodes (13): Frame, MarkupParser, Builder, TagResolver, TagScanner, Builder, LinkedHashMap<>, TextMarkup (+5 more)

### Community 56 - "Community 56"
Cohesion: 0.08
Nodes (12): RegionPos, RegionFileStoreTest, AutosaveTest, BlockPlaceTest, ChestInteractionTest, ChunkEvictionTest, DeltaMoveOverflowTest, EmptyRegionEvictionTest (+4 more)

### Community 57 - "Community 57"
Cohesion: 0.05
Nodes (10): GameModeArgument, Vec2Argument, IntegerSuggestion, LiteralCommandNode, StringRange, Suggestion, Suggestions, SuggestionsBuilder (+2 more)

### Community 58 - "Community 58"
Cohesion: 0.04
Nodes (13): EventBus, PluginDisableEvent, PluginEnableEvent, Furnace, Holder, FurnacePlugin, PluginContext, Attachment (+5 more)

### Community 59 - "Community 59"
Cohesion: 0.04
Nodes (11): Mth, OldBlendedNoise, MathHelper, BlendedNoise, ImprovedNoise, LegacyPositionalRandom, NoiseRandom, PerlinNoise (+3 more)

### Community 60 - "Community 60"
Cohesion: 0.04
Nodes (11): CaveVinesBlock, CaveVinesPlantBlock, GrowingPlantBlock, GrowingPlantBody, GrowingPlantHead, Kind, KelpBlock, KelpPlantBlock (+3 more)

### Community 61 - "Community 61"
Cohesion: 0.05
Nodes (19): IntRange, LootCondition, LootContext, LootData, Candidate, Composite, Creator, LootEntry (+11 more)

### Community 62 - "Community 62"
Cohesion: 0.05
Nodes (9): Palette, Run, Attr, MarkupWriter, Builder, LegacyTextSerializer, TextColor, LegacySerializerTest (+1 more)

### Community 64 - "Community 64"
Cohesion: 0.06
Nodes (24): Building, RoomFit, RoomFit, RoomFit, RoomFit, RoomFit, RoomFit, RoomFit (+16 more)

### Community 65 - "Community 65"
Cohesion: 0.03
Nodes (4): BlockInfo, Grid, Shape, BlockHardness

### Community 66 - "Community 66"
Cohesion: 0.05
Nodes (7): MarkerOrMarked, Cache2D, CacheAllInCell, CacheOnce, FlatCache, NoiseChunk, NoiseInterpolator

### Community 67 - "Community 67"
Cohesion: 0.05
Nodes (21): BuriedTreasureStructure, GenContext, JungleTempleStructure, Props, RuinedPortalStructure, Setup, VP, IN_MOUNTAIN (+13 more)

### Community 68 - "Community 68"
Cohesion: 0.06
Nodes (6): MarkupParseException, Placeholder, Cache, MarkupParseTest, MarkupSerializeTest, PlaceholderTest

### Community 69 - "Community 69"
Cohesion: 0.10
Nodes (3): toNbt(), NbtCompound, NbtList

### Community 70 - "Community 70"
Cohesion: 0.09
Nodes (3): ConfigSectionTest, TomlTest, YamlParserTest

### Community 71 - "Community 71"
Cohesion: 0.06
Nodes (22): AnsiTextSerializer, Builder, ColorMode, ANSI_16, ANSI_256, NONE, TRUE_COLOR, Builder (+14 more)

### Community 72 - "Community 72"
Cohesion: 0.06
Nodes (8): Actor, BlockPlacementTest, TestLevel, TestLevel, TestLevel, BlockTypesTest, FluidTest, PistonTest

### Community 73 - "Community 73"
Cohesion: 0.04
Nodes (13): JigsawPiece, Junction, Element, Feature, Jig, JigsawPools, ListElement, Pool (+5 more)

### Community 75 - "Community 75"
Cohesion: 0.06
Nodes (5): BaseRailBlock, DetectorRailBlock, PoweredRailBlock, RailBlock, RedStoneWireBlock

### Community 76 - "Community 76"
Cohesion: 0.05
Nodes (8): WallHangingSignPlacement, CeilingHangingSignBlock, ChestBlock, FenceGateBlock, SignBlock, StandingSignBlock, TrappedChestBlock, WallHangingSignBlock

### Community 77 - "Community 77"
Cohesion: 0.08
Nodes (10): Snapshot, AnvilChunkCodec, FossilFeature, EntityInfo, MissingTemplateException, StructureTemplate, PlayerDataVanillaTest, NbtCompoundTest (+2 more)

### Community 78 - "Community 78"
Cohesion: 0.07
Nodes (18): Block, Content, Entity, Keybind, Literal, Nbt, NbtSource, Score (+10 more)

### Community 79 - "Community 79"
Cohesion: 0.07
Nodes (13): GlowLichenBlock, MultifaceBlock, DefaultSpreaderConfig, MultifaceSpreader, SpreadConfig, SpreadPos, SpreadPredicate, SpreadType (+5 more)

### Community 80 - "Community 80"
Cohesion: 0.10
Nodes (5): BlockShapes, ShapeLevel, Shape, ShapeUpdater, ShapeDiff

### Community 81 - "Community 81"
Cohesion: 0.05
Nodes (18): BlockPileFeature, RotatedBlockProvider, DualNoiseProvider, Kind, BROWN, RED, NoiseProvider, NoiseThresholdProvider (+10 more)

### Community 82 - "Community 82"
Cohesion: 0.05
Nodes (14): Field, PCodec, PCodec, PCodec, PCodec, PCodec, PCodec, PCodec (+6 more)

### Community 83 - "Community 83"
Cohesion: 0.05
Nodes (8): AnvilBlock, BrushableBlock, DragonEggBlock, FallingBlock, FrostedIceBlock, IceBlock, SnowLayerBlock, TntBlock

### Community 84 - "Community 84"
Cohesion: 0.06
Nodes (10): FilterBlock, FormatRange, Overlay, PackMetadata, Composite, IoSupplier, PackResources, PathPack (+2 more)

### Community 85 - "Community 85"
Cohesion: 0.07
Nodes (11): Mth, Cluster, DripstoneFeatures, Large, LargeDripstone, Pointed, Support, Wind (+3 more)

### Community 86 - "Community 86"
Cohesion: 0.04
Nodes (10): CopperBulbBlock, SlabBlock, WeatheringCopper, WeatheringCopperBulbBlock, WeatheringCopperDoorBlock, WeatheringCopperFullBlock, WeatheringCopperGrateBlock, WeatheringCopperSlabBlock (+2 more)

### Community 87 - "Community 87"
Cohesion: 0.05
Nodes (27): BlockColumnFeature, Layer, AllOf, AnyOf, BlockPredicate, HasSturdyFace, InsideWorldBounds, MatchingFluids (+19 more)

### Community 88 - "Community 88"
Cohesion: 0.07
Nodes (10): ClickEvent, NamedTextColor, Builder, Condition, Result, REPLACE, SKIP, STOP (+2 more)

### Community 89 - "Community 89"
Cohesion: 0.07
Nodes (8): CommandInfo, PluginDescription, Candidate, Context, Loaded, PluginClassLoader, PluginLogger, PluginManagerImpl

### Community 90 - "Community 90"
Cohesion: 0.05
Nodes (9): Components, LootBlockState, Key, LootParams, org.domi.furnace.server.loot.LootEntity, VanillaBlockLootTest, VanillaChestLootTest, VanillaEntityLootTest (+1 more)

### Community 91 - "Community 91"
Cohesion: 0.05
Nodes (17): F, Kind, ALWAYS, DEFAULTED, OPTIONAL, NCodec, NCodec, NCodec (+9 more)

### Community 92 - "Community 92"
Cohesion: 0.08
Nodes (7): RandomSupport, Seed128bit, Xoroshiro128PlusPlus, XoroshiroPositionalRandomFactory, XoroshiroRandomSource, XoroshiroRandomSourceTest, XoroshiroRandomTest

### Community 93 - "Community 93"
Cohesion: 0.07
Nodes (12): BiomeFeatureData, FeaturePlacer, FeatureSorter, StepFeatureData, BiomeFeatureDataTest, FeatureOrderParityTest, Fixture, CountingFeature (+4 more)

### Community 94 - "Community 94"
Cohesion: 0.08
Nodes (4): BreakerActor, PlayerActor, BlockBreakingTest, Host

### Community 95 - "Community 95"
Cohesion: 0.08
Nodes (4): DoublePlantBlock, PitcherCropBlock, SmallDripleafBlock, TallSeagrassBlock

### Community 96 - "Community 96"
Cohesion: 0.07
Nodes (12): BlockEntitySnapshot, ChunkSnapshot, SaveData, Section, ChunkSerializerTest, AnvilChunkCodecTest, ChunkBodyCacheRaceTest, Env (+4 more)

### Community 97 - "Community 97"
Cohesion: 0.06
Nodes (8): Owned, ThreadOwner, OwnedTest, GlobalThread, Delayed, RegionThread, TickTimer, TickTimerTest

### Community 98 - "Community 98"
Cohesion: 0.09
Nodes (6): ChunkPipeline, Proto, PristineChunk, ProtoColumn, ChunkPipelineConcurrencyTest, Replay

### Community 99 - "Community 99"
Cohesion: 0.07
Nodes (8): Builder, Constant, CoordinateVisitor, CubicSpline, Multipoint, DensityFunctionParser, SplineCoordinate, ToFloatFunction

### Community 101 - "Community 101"
Cohesion: 0.14
Nodes (8): Piece, MansionGrid, P, PiecePlacer, PlacementData, RoomKit, SimpleGrid, WoodlandMansionStructure

### Community 102 - "Community 102"
Cohesion: 0.05
Nodes (12): Batch10, Batch1, Batch2, Batch3, Batch4, Batch5, Batch6, Batch7 (+4 more)

### Community 103 - "Community 103"
Cohesion: 0.10
Nodes (4): Enchantments, Instance, LootStack, NetworkComponentsTest

### Community 104 - "Community 104"
Cohesion: 0.06
Nodes (18): Event, AsyncPlayerPreLoginEvent, Result, ALLOWED, KICK_BANNED, KICK_FULL, KICK_OTHER, KICK_WHITELIST (+10 more)

### Community 105 - "Community 105"
Cohesion: 0.07
Nodes (8): Compression, RegionFile, RegionFileStore, AnvilWorldSource, WorldSource, OracleGroundtruthAuditTest, Env, WorldPersistCheck

### Community 107 - "Community 107"
Cohesion: 0.07
Nodes (8): GlobalRenderer, Format, MESSAGE_FORMAT, VANILLA, TranslationStore, Translator, java.util.ListResourceBundle, Language

### Community 108 - "Community 108"
Cohesion: 0.08
Nodes (4): BaseFireBlock, NetherPortalBlock, PortalShape, SoulFireBlock

### Community 109 - "Community 109"
Cohesion: 0.07
Nodes (5): Crypt, AuthenticationUnavailableException, SessionService, ServerLoginContext, Property

### Community 113 - "Community 113"
Cohesion: 0.09
Nodes (4): MultifaceGrowthFeature, Env, MultifaceSpreader, Blocks

### Community 117 - "Community 117"
Cohesion: 0.08
Nodes (15): Bound, ChatType, CHAT, EMOTE_COMMAND, MSG_COMMAND_INCOMING, MSG_COMMAND_OUTGOING, SAY_COMMAND, TEAM_MSG_COMMAND_INCOMING (+7 more)

### Community 118 - "Community 118"
Cohesion: 0.09
Nodes (5): ResourcePackInfo, ServerProperties, MutableValue, VanillaSettings, ServerPropertiesTest

### Community 119 - "Community 119"
Cohesion: 0.13
Nodes (3): ComponentPatch, ItemNbt, DataComponentsVanillaTest

### Community 120 - "Community 120"
Cohesion: 0.13
Nodes (3): Blocks, PlayerProbe, TreeFeatureParityDiagnosticTest

### Community 121 - "Community 121"
Cohesion: 0.11
Nodes (6): PluginTasks, ScheduledTask, Once, Repeating, TaskScheduler, RegionRouter

### Community 122 - "Community 122"
Cohesion: 0.12
Nodes (4): BarrierCache, FluidStatus, MutableContext, NoiseBasedAquifer

### Community 123 - "Community 123"
Cohesion: 0.11
Nodes (8): OreVeinifier, OreVeinifierSkipFuzzTest, DensityFunction, DensityFunction, XoroshiroRandomSource.XoroshiroPositionalRandomFactory, RngConsumedException, OreVeinifierTest, DensityFunction

### Community 125 - "Community 125"
Cohesion: 0.11
Nodes (8): LastSeenMessages, Update, Entry, LastSeenMessagesValidator, MessageSignature, Packed, MessageSignatureCache, SignedMessageBody

### Community 126 - "Community 126"
Cohesion: 0.09
Nodes (10): Direction, DOWN, EAST, NORTH, SOUTH, UP, WEST, Bounds (+2 more)

### Community 127 - "Community 127"
Cohesion: 0.08
Nodes (7): CipherDecoder, CipherEncoder, CompressionEncoder, FrameDecoder, FrameEncoder, ChannelInitializer<SocketChannel>, FramingTest

### Community 130 - "Community 130"
Cohesion: 0.10
Nodes (3): EntityTracker, Viewers, MoveEntity

### Community 132 - "Community 132"
Cohesion: 0.11
Nodes (6): Config, ConfigFormat, JSON, TOML, YAML, CrossFormatTest

### Community 134 - "Community 134"
Cohesion: 0.10
Nodes (12): CombatTracker, Entry, DamageType, DROWN, FALL, GENERIC, GENERIC_KILL, IN_WALL (+4 more)

### Community 135 - "Community 135"
Cohesion: 0.11
Nodes (3): net.minecraft.world.level.chunk.CarvingMask, OracleRngTrace, TraceWorldgenRandom

### Community 136 - "Community 136"
Cohesion: 0.14
Nodes (3): Listener, EventBusImpl, Handler

### Community 137 - "Community 137"
Cohesion: 0.11
Nodes (4): RootCommandNode, CommandTreePacket, Node, VanillaCommandTreeTest

### Community 139 - "Community 139"
Cohesion: 0.11
Nodes (8): EndSpikeFeature, Spike, DistributionPoint, GeodeConfig, GeodeFeature, IntRange, StateResolver, EndFeaturesTest

### Community 140 - "Community 140"
Cohesion: 0.10
Nodes (7): Extent, MovingExtent, StaticExtent, Status, GROWING, SHRINKING, STATIONARY

### Community 141 - "Community 141"
Cohesion: 0.12
Nodes (4): AdventurePredicate, normalize(), PCodec, Val

### Community 142 - "Community 142"
Cohesion: 0.07
Nodes (24): Instrument, BANJO, BASEDRUM, BASS, BELL, BIT, CHIME, COW_BELL (+16 more)

### Community 145 - "Community 145"
Cohesion: 0.08
Nodes (4): DamageSourcePredicate, LootDamageSource, LootEntity, LootDamageSource

### Community 146 - "Community 146"
Cohesion: 0.12
Nodes (4): DirectoryLock, LockException, LevelStorage, LevelStorageTest

### Community 148 - "Community 148"
Cohesion: 0.15
Nodes (4): PCodec, ComponentType, NCodec, DataComponents

### Community 149 - "Community 149"
Cohesion: 0.12
Nodes (5): ContainerClose, ContainerSetContent, ContainerSetSlot, OpenScreen, ContainerPacketsTest

### Community 151 - "Community 151"
Cohesion: 0.13
Nodes (3): Entities, LevelData, LevelDataBorderTest

### Community 152 - "Community 152"
Cohesion: 0.17
Nodes (3): Listener, WorldBorder, Recorder

### Community 153 - "Community 153"
Cohesion: 0.17
Nodes (5): BlockTicks, Key, Scheduled, Ticker, ChunkFilter

### Community 154 - "Community 154"
Cohesion: 0.11
Nodes (4): Codecs, PCodec, PCodec, TextNbtJson

### Community 157 - "Community 157"
Cohesion: 0.16
Nodes (4): Data, ProfilePublicKey, SignatureValidator, Updater

### Community 160 - "Community 160"
Cohesion: 0.10
Nodes (15): Kind, CHEST_CORRIDOR, FIVE_CROSSING, LEFT_TURN, LIBRARY, PORTAL_ROOM, PRISON_HALL, RIGHT_TURN (+7 more)

### Community 162 - "Community 162"
Cohesion: 0.17
Nodes (7): Clip, FluidMode, ANY, NONE, SOURCE_ONLY, WATER, Result

### Community 163 - "Community 163"
Cohesion: 0.14
Nodes (3): PlayerChatState, RemoteChatSession, Decoder

### Community 166 - "Community 166"
Cohesion: 0.19
Nodes (3): ItemTools, Rule, Tool

### Community 167 - "Community 167"
Cohesion: 0.22
Nodes (4): ClimateRTree, Leaf, Node, SubTree

### Community 171 - "Community 171"
Cohesion: 0.21
Nodes (4): Builder, CauldronInteraction, Prop_, Table

### Community 173 - "Community 173"
Cohesion: 0.18
Nodes (3): UpdateTags, UpdateTagsPlay, TagSetTest

### Community 174 - "Community 174"
Cohesion: 0.11
Nodes (18): Kind, ALWAYS, AZALEA, BUSH, CACTUS, DEAD_BUSH, FIRE, FUNGUS (+10 more)

### Community 176 - "Community 176"
Cohesion: 0.13
Nodes (9): EventPriority, HIGH, HIGHEST, LOW, LOWEST, MONITOR, NORMAL, EventSubscription (+1 more)

### Community 177 - "Community 177"
Cohesion: 0.20
Nodes (3): BlockPredicateArgument, Predicate, BlockArgumentsTest

### Community 181 - "Community 181"
Cohesion: 0.15
Nodes (5): DisplayInfo, Frame, CHALLENGE, GOAL, TASK

### Community 182 - "Community 182"
Cohesion: 0.26
Nodes (6): Entry, FullNeighborUpdate, MultiNeighborUpdate, NeighborUpdater, ShapeUpdate, SimpleNeighborUpdate

### Community 185 - "Community 185"
Cohesion: 0.21
Nodes (3): Beardifier, Joint, Rigid

### Community 187 - "Community 187"
Cohesion: 0.14
Nodes (14): Kind, BANNER, BED, BLOCK, DOUBLE_HIGH, GAME_MASTER, HANGING_SIGN, ITEM_NAME (+6 more)

### Community 188 - "Community 188"
Cohesion: 0.22
Nodes (4): FurnaceMain, Options, Repeating, TpsHeartbeat

### Community 190 - "Community 190"
Cohesion: 0.14
Nodes (3): FeatureSize, ThreeLayersFeatureSize, TwoLayersFeatureSize

### Community 191 - "Community 191"
Cohesion: 0.14
Nodes (7): Direction, DOWN, EAST, NORTH, SOUTH, UP, WEST

### Community 192 - "Community 192"
Cohesion: 0.26
Nodes (4): StructurePlacementCatalog, StructureSetPlacement, ChunkFixture, StructurePlacementParityTest

### Community 198 - "Community 198"
Cohesion: 0.30
Nodes (3): ListTag, TextNbt, Wrapper

### Community 207 - "Community 207"
Cohesion: 0.25
Nodes (4): RandomSpreadStructurePlacement, RandomSpreadType, LINEAR, TRIANGULAR

### Community 210 - "Community 210"
Cohesion: 0.20
Nodes (7): Action, CHANGE_PAGE, COPY_TO_CLIPBOARD, OPEN_FILE, OPEN_URL, RUN_COMMAND, SUGGEST_COMMAND

### Community 211 - "Community 211"
Cohesion: 0.31
Nodes (3): SignedMessageChain, SignedMessageLink, ClientSigner

### Community 214 - "Community 214"
Cohesion: 0.20
Nodes (6): RemovalReason, CHANGED_DIMENSION, DISCARDED, KILLED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER

### Community 216 - "Community 216"
Cohesion: 0.24
Nodes (3): BlockPosIter, Visitor, BlockPosIterTest

### Community 217 - "Community 217"
Cohesion: 0.20
Nodes (9): GenStatus, BIOMES, CARVERS, EMPTY, FEATURES, FULL, NOISE, STRUCTURE_STARTS (+1 more)

### Community 225 - "Community 225"
Cohesion: 0.22
Nodes (3): StainedGlassBlock, TintedGlassBlock, TransparentBlock

### Community 227 - "Community 227"
Cohesion: 0.25
Nodes (4): BlockPlacementTest.TestLevel, LevelServices.Delegating, BlockPlacementTest.TestLevel, BlockPlacementTest.TestLevel

### Community 232 - "Community 232"
Cohesion: 0.29
Nodes (6): Mode, EVERYTHING, FROM, ONLY, THROUGH, UNTIL

### Community 234 - "Community 234"
Cohesion: 0.29
Nodes (7): LoginPhase, ACCEPTED, AUTHENTICATING, HELLO, KEY, PROTOCOL_SWITCHING, VERIFYING

### Community 238 - "Community 238"
Cohesion: 0.52
Nodes (6): bot(), dump(), prepare(), run_server(), run.sh script, stop_server()

### Community 245 - "Community 245"
Cohesion: 0.33
Nodes (6): Kind, OPTIONAL_DEFAULT, OPTIONAL_EMPTY, OPTIONAL_LENIENT, OR_ELSE, REQUIRED

## Knowledge Gaps
- **334 isolated node(s):** `YAML`, `TOML`, `JSON`, `SURVIVAL`, `CREATIVE` (+329 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 1902 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **115 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Pos` connect `Community 5` to `Community 0`, `Community 1`, `Community 2`, `Community 3`, `Community 4`, `Community 128`, `Community 6`, `Community 7`, `Community 8`, `Community 11`, `Community 141`, `Community 14`, `Community 13`, `Community 18`, `Community 21`, `Community 22`, `Community 151`, `Community 153`, `Community 27`, `Community 159`, `Community 162`, `Community 34`, `Community 39`, `Community 169`, `Community 170`, `Community 45`, `Community 177`, `Community 182`, `Community 56`, `Community 60`, `Community 63`, `Community 72`, `Community 75`, `Community 76`, `Community 77`, `Community 79`, `Community 83`, `Community 86`, `Community 94`, `Community 95`, `Community 224`, `Community 229`, `Community 230`, `Community 108`, `Community 243`, `Community 244`?**
  _High betweenness centrality (0.179) - this node is a cross-community bridge._
- **Are the 33 inferred relationships involving `Pos` (e.g. with `.fromNbt()` and `.ChestBlockEntity()`) actually correct?**
  _`Pos` has 33 INFERRED edges - model-reasoned connections that need verification._
- **What connects `YAML`, `TOML`, `JSON` to the rest of the system?**
  _334 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.012034584826296931 - nodes in this community are weakly interconnected._
- **Why does `Text` connect `Community 18` to `Community 0`, `Community 129`, `Community 1`, `Community 6`, `Community 134`, `Community 8`, `Community 136`, `Community 137`, `Community 11`, `Community 13`, `Community 19`, `Community 20`, `Community 22`, `Community 24`, `Community 25`, `Community 26`, `Community 157`, `Community 30`, `Community 163`, `Community 164`, `Community 42`, `Community 43`, `Community 45`, `Community 46`, `Community 47`, `Community 48`, `Community 49`, `Community 51`, `Community 55`, `Community 57`, `Community 58`, `Community 62`, `Community 68`, `Community 196`, `Community 198`, `Community 71`, `Community 204`, `Community 78`, `Community 208`, `Community 84`, `Community 88`, `Community 220`, `Community 104`, `Community 107`, `Community 116`, `Community 117`?**
  _High betweenness centrality (0.077) - this node is a cross-community bridge._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.023381471657333727 - nodes in this community are weakly interconnected._
- **Why does `BlockLevel` connect `Community 4` to `Community 1`, `Community 2`, `Community 3`, `Community 5`, `Community 6`, `Community 7`, `Community 11`, `Community 141`, `Community 14`, `Community 13`, `Community 18`, `Community 159`, `Community 162`, `Community 170`, `Community 45`, `Community 177`, `Community 182`, `Community 60`, `Community 75`, `Community 76`, `Community 79`, `Community 83`, `Community 86`, `Community 95`, `Community 224`, `Community 230`, `Community 108`, `Community 243`, `Community 244`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._