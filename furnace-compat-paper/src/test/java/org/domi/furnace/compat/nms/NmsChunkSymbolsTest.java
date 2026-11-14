package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * N1·N2a 표면의 서술자가 바닐라 1.21.1(Mojang 이름)과 같은가 — 플러그인 바이트코드는 이름 + 서술자(반환형 포함)로 링크한다.
 * 서술자는 바닐라 서버 jar 를 javap 로 읽어 맞췄다(난독화 이름 → 형: dvi LevelChunk, duy ChunkAccess, dvj LevelChunkSection, dvq PalettedContainer,
 * dvr PalettedContainerRO, dvz ChunkStatus, dyy Heightmap, dcw Level, aqu ServerLevel, aqs ServerChunkCache, dvc ChunkSource).
 * N2b-1(떨어진 섹션 객체)은 있어야 하고, N2b-2 이후(따로 만드는 하이트맵·setRawData)는 아직 없어서 덫에 걸려야 한다 — 그것도 확인한다.
 */
class NmsChunkSymbolsTest {

    static final String POS = "Lnet/minecraft/core/BlockPos;", STATE = "Lnet/minecraft/world/level/block/state/BlockState;",
            FLUID = "Lnet/minecraft/world/level/material/FluidState;", CHUNK = "Lnet/minecraft/world/level/chunk/LevelChunk;",
            ACCESS = "Lnet/minecraft/world/level/chunk/ChunkAccess;", STATUS = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;",
            SECTION = "Lnet/minecraft/world/level/chunk/LevelChunkSection;", TYPES = "Lnet/minecraft/world/level/levelgen/Heightmap$Types;",
            HOLDER = "Lnet/minecraft/core/Holder;", PC = "Lnet/minecraft/world/level/chunk/PalettedContainer;";

    static final String[][] PRESENT = {
            {"net/minecraft/world/level/chunk/LevelChunk", "getBlockState", "(" + POS + ")" + STATE},
            {"net/minecraft/world/level/chunk/LevelChunk", "setBlockState", "(" + POS + STATE + "Z)" + STATE},
            {"net/minecraft/world/level/chunk/LevelChunk", "getFluidState", "(" + POS + ")" + FLUID},
            {"net/minecraft/world/level/chunk/LevelChunk", "getFluidState", "(III)" + FLUID},
            {"net/minecraft/world/level/chunk/LevelChunk", "getLevel", "()Lnet/minecraft/world/level/Level;"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getSections", "()[" + SECTION},
            {"net/minecraft/world/level/chunk/LevelChunk", "getSection", "(I)" + SECTION},
            {"net/minecraft/world/level/chunk/LevelChunk", "getPos", "()Lnet/minecraft/world/level/ChunkPos;"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getHeight", "(" + TYPES + "II)I"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getOrCreateHeightmapUnprimed", "(" + TYPES + ")Lnet/minecraft/world/level/levelgen/Heightmap;"},
            {"net/minecraft/world/level/chunk/LevelChunk", "setUnsaved", "(Z)V"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getPersistedStatus", "()" + STATUS},
            {"net/minecraft/world/level/chunk/LevelChunk", "getNoiseBiome", "(III)" + HOLDER},
            {"net/minecraft/world/level/chunk/LevelChunk", "getMinBuildHeight", "()I"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getSectionIndex", "(I)I"},
            {"net/minecraft/world/level/chunk/LevelChunk", "getSectionsCount", "()I"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getBlockState", "(III)" + STATE},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getFluidState", "(III)" + FLUID},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "hasOnlyAir", "()Z"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getStates", "()" + PC},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getBiomes", "()Lnet/minecraft/world/level/chunk/PalettedContainerRO;"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "acquire", "()V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "release", "()V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "isRandomlyTicking", "()Z"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "recalcBlockCounts", "()V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "maybeHas", "(Ljava/util/function/Predicate;)Z"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getNoiseBiome", "(III)" + HOLDER},
            {"net/minecraft/world/level/chunk/PalettedContainer", "get", "(III)Ljava/lang/Object;"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "getAll", "(Ljava/util/function/Consumer;)V"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "copy", "()" + PC},
            {"net/minecraft/world/level/chunk/PalettedContainer", "count", "(Lnet/minecraft/world/level/chunk/PalettedContainer$CountConsumer;)V"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "maybeHas", "(Ljava/util/function/Predicate;)Z"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "getAndSet", "(IIILjava/lang/Object;)Ljava/lang/Object;"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "getAndSetUnchecked", "(IIILjava/lang/Object;)Ljava/lang/Object;"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "set", "(IIILjava/lang/Object;)V"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "acquire", "()V"},
            {"net/minecraft/world/level/chunk/PalettedContainerRO", "get", "(III)Ljava/lang/Object;"},
            {"net/minecraft/server/level/ServerLevel", "getChunkSource", "()Lnet/minecraft/server/level/ServerChunkCache;"},
            {"net/minecraft/server/level/ServerLevel", "getChunk", "(II)" + CHUNK},
            {"net/minecraft/server/level/ServerLevel", "getChunk", "(II)" + ACCESS},
            {"net/minecraft/server/level/ServerLevel", "getChunk", "(II" + STATUS + "Z)" + ACCESS},
            {"net/minecraft/server/level/ServerLevel", "getChunkAt", "(" + POS + ")" + CHUNK},
            {"net/minecraft/server/level/ServerLevel", "getBlockState", "(" + POS + ")" + STATE},
            {"net/minecraft/server/level/ServerLevel", "getFluidState", "(" + POS + ")" + FLUID},
            {"net/minecraft/server/level/ServerLevel", "setBlock", "(" + POS + STATE + "I)Z"},
            {"net/minecraft/server/level/ServerLevel", "setBlock", "(" + POS + STATE + "II)Z"},
            {"net/minecraft/server/level/ServerLevel", "setBlockAndUpdate", "(" + POS + STATE + ")Z"},
            {"net/minecraft/server/level/ServerLevel", "removeBlock", "(" + POS + "Z)Z"},
            {"net/minecraft/server/level/ServerLevel", "getHeight", "(" + TYPES + "II)I"},
            {"net/minecraft/server/level/ServerLevel", "getHeight", "()I"},
            {"net/minecraft/server/level/ServerLevel", "hasChunk", "(II)Z"},
            {"net/minecraft/server/level/ServerLevel", "hasChunkAt", "(" + POS + ")Z"},
            {"net/minecraft/server/level/ServerLevel", "isLoaded", "(" + POS + ")Z"},
            {"net/minecraft/server/level/ServerLevel", "isOutsideBuildHeight", "(I)Z"},
            {"net/minecraft/server/level/ServerLevel", "dimension", "()Lnet/minecraft/resources/ResourceKey;"},
            {"net/minecraft/server/level/ServerLevel", "getEntity", "(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;"},
            {"net/minecraft/server/level/ServerLevel", "getLevel", "()Lnet/minecraft/server/level/ServerLevel;"},
            {"net/minecraft/server/level/ServerLevel", "getMinecraftWorld", "()Lnet/minecraft/server/level/ServerLevel;"},
            {"net/minecraft/server/level/ServerLevel", "getBlockStateIfLoaded", "(" + POS + ")" + STATE},
            {"net/minecraft/server/level/ServerLevel", "getChunkIfLoaded", "(II)" + CHUNK},
            {"net/minecraft/world/level/Level", "getChunkSource", "()Lnet/minecraft/world/level/chunk/ChunkSource;"},
            {"net/minecraft/server/level/ServerChunkCache", "getChunk", "(IIZ)" + CHUNK},
            {"net/minecraft/server/level/ServerChunkCache", "getChunk", "(II" + STATUS + "Z)" + ACCESS},
            {"net/minecraft/server/level/ServerChunkCache", "getChunkNow", "(II)" + CHUNK},
            {"net/minecraft/server/level/ServerChunkCache", "hasChunk", "(II)Z"},
            {"net/minecraft/server/level/ServerChunkCache", "getLoadedChunksCount", "()I"},
            {"net/minecraft/server/level/ServerChunkCache", "getChunkAtIfLoadedImmediately", "(II)" + CHUNK},
            {"net/minecraft/world/entity/Entity", "blockPosition", "()" + POS},
            {"net/minecraft/world/entity/Entity", "position", "()Lnet/minecraft/world/phys/Vec3;"},
            {"net/minecraft/world/entity/Entity", "chunkPosition", "()Lnet/minecraft/world/level/ChunkPos;"},
            {"net/minecraft/world/entity/Entity", "getType", "()Lnet/minecraft/world/entity/EntityType;"},
            {"net/minecraft/server/MinecraftServer", "getLevel", "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"},
            {"net/minecraft/server/MinecraftServer", "overworld", "()Lnet/minecraft/server/level/ServerLevel;"},
            {"net/minecraft/server/MinecraftServer", "levelKeys", "()Ljava/util/Set;"},
            {"net/minecraft/world/level/chunk/status/ChunkStatus", "isOrAfter", "(" + STATUS + ")Z"},
            {"net/minecraft/world/level/chunk/status/ChunkStatus", "byName", "(Ljava/lang/String;)" + STATUS},
            {"net/minecraft/world/level/chunk/status/ChunkStatus", "getStatusList", "()Ljava/util/List;"},
            {"net/minecraft/world/level/levelgen/Heightmap", "getFirstAvailable", "(II)I"},
            {"net/minecraft/world/level/levelgen/Heightmap", "getHighestTaken", "(II)I"},
            {"net/minecraft/world/level/levelgen/Heightmap", "getRawData", "()[J"},
            {"net/minecraft/world/level/levelgen/Heightmap$Types", "getSerializationKey", "()Ljava/lang/String;"},
            {"net/minecraft/world/level/levelgen/Heightmap$Types", "keepAfterWorldgen", "()Z"},
    };

    /** N2b-1: 떨어진 섹션 객체 표면(바닐라 1.21.1 서술자 — javap 로 확인한 dvj·dvq·dvq$d·azd·azu·dfy.q 의 Mojang 이름). */
    static final String[][] N2B1 = {
            {"net/minecraft/world/level/chunk/LevelChunkSection", "setBlockState", "(III" + STATE + ")" + STATE},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "setBlockState", "(III" + STATE + "Z)" + STATE},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "<init>", "(" + PC + "Lnet/minecraft/world/level/chunk/PalettedContainerRO;)V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "<init>", "(Lnet/minecraft/core/Registry;)V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "isRandomlyTickingBlocks", "()Z"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "isRandomlyTickingFluids", "()Z"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "write", "(Lnet/minecraft/network/FriendlyByteBuf;)V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "read", "(Lnet/minecraft/network/FriendlyByteBuf;)V"},
            {"net/minecraft/world/level/chunk/LevelChunkSection", "getSerializedSize", "()I"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "<init>", "(Lnet/minecraft/core/IdMap;Ljava/lang/Object;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;)V"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "<init>", "(Lnet/minecraft/core/IdMap;Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;"
                    + "Lnet/minecraft/world/level/chunk/PalettedContainer$Configuration;Lnet/minecraft/util/BitStorage;Ljava/util/List;)V"},
            {"net/minecraft/world/level/chunk/PalettedContainer", "recreate", "()" + PC},
            {"net/minecraft/world/level/chunk/PalettedContainer", "onResize", "(ILjava/lang/Object;)I"},
            {"net/minecraft/world/level/chunk/PalettedContainer$Strategy", "getConfiguration",
                    "(Lnet/minecraft/core/IdMap;I)Lnet/minecraft/world/level/chunk/PalettedContainer$Configuration;"},
            {"net/minecraft/world/level/chunk/PalettedContainer$Strategy", "getIndex", "(III)I"},
            {"net/minecraft/util/SimpleBitStorage", "<init>", "(II[J)V"},
            {"net/minecraft/util/SimpleBitStorage", "<init>", "(II[I)V"},
            {"net/minecraft/util/SimpleBitStorage", "getRaw", "()[J"},
            {"net/minecraft/util/ZeroBitStorage", "<init>", "(I)V"},
            {"net/minecraft/world/level/chunk/HashMapPalette", "<init>", "(Lnet/minecraft/core/IdMap;ILnet/minecraft/world/level/chunk/PaletteResize;Ljava/util/List;)V"},
            {"net/minecraft/world/level/chunk/GlobalPalette", "<init>", "(Lnet/minecraft/core/IdMap;)V"},
            {"net/minecraft/core/IdMapper", "getId", "(Ljava/lang/Object;)I"},
            {"net/minecraft/world/level/block/state/BlockState", "isRandomlyTicking", "()Z"},
    };

    /** N2b-2 이후 — 아직 없어야 하는 것(플러그인 호출 위치에서 덫이 걸린다). */
    static final String[][] ABSENT = {
            {"net/minecraft/world/level/levelgen/Heightmap", "<init>", "(" + ACCESS + TYPES + ")V"},
            {"net/minecraft/world/level/levelgen/Heightmap", "setRawData", "(" + ACCESS + TYPES + "[J)V"},
    };

    @Test
    void vanillaDescriptorsArePresent() {
        List<String> missing = new ArrayList<>();
        for (String[] m : PRESENT) if (!NmsSymbols.RUNTIME.hasMethod(m[0], m[1], m[2])) missing.add(m[0] + "#" + m[1] + m[2]);
        assertEquals(List.of(), missing);
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/chunk/LevelChunkSection", "SECTION_SIZE", "I"));
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/chunk/status/ChunkStatus", "FULL", STATUS));
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/Level", "OVERWORLD", "Lnet/minecraft/resources/ResourceKey;"));
    }

    @Test
    void n2b1DetachedSurfaceIsPresent() {
        List<String> missing = new ArrayList<>();
        for (String[] m : N2B1) if (!NmsSymbols.RUNTIME.hasMethod(m[0], m[1], m[2])) missing.add(m[0] + "#" + m[1] + m[2]);
        assertEquals(List.of(), missing);
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/block/Block", "BLOCK_STATE_REGISTRY", "Lnet/minecraft/core/IdMapper;"));
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/chunk/PalettedContainer$Strategy", "SECTION_STATES",
                "Lnet/minecraft/world/level/chunk/PalettedContainer$Strategy;"));
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/chunk/ChunkAccess", "heightmaps", "Ljava/util/Map;"));
    }

    @Test
    void n2bSurfaceStaysTrapped() {
        for (String[] m : ABSENT) assertFalse(NmsSymbols.RUNTIME.hasMethod(m[0], m[1], m[2]), m[0] + "#" + m[1] + m[2]);
    }
}
