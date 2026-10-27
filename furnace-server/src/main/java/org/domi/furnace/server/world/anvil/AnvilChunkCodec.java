// ChunkColumn ↔ 바닐라 1.21.1 Anvil 청크 NBT(ChunkSerializer 형식).
// - 섹션: block_states/biomes 팰릿(첫 등장 순) + 바닐라 비트 수(블록 max(4, ceillog2), 바이옴 ceillog2)
// - 높이맵 4종, 상자는 바닐라 형식(id·Items{Slot,id,count}), 생성된 미지원 블록 엔티티·엔티티는 NBT 그대로 보존
// - 모르는 최상위 키(structures·틱 목록·다른 서버 확장 등)는 읽은 값을 그대로 되돌려 쓴다
// - 완성되지 않은(Status ≠ full) 청크는 읽지 않고 null — 호출 측이 새로 생성한다
// 조명은 아직 서버가 계산하지 않으므로 isLightOn=0 으로 써서 바닐라가 열면 다시 계산하게 한다.
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChestBlockEntity;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.ItemRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

public final class AnvilChunkCodec {

    public static final int DATA_VERSION = 3955; // 1.21.1
    private static final String LEGACY_CHEST_ID = "furnace:chest"; // 초기 Furnace 자체 형식(읽기 호환)
    /** 바닐라 아이템 NBT 에 실을 수 없는 Furnace 컴포넌트 패치 바이트(네트워크 인코딩) — 바닐라는 모르는 키를 무시한다. */
    private static final String FURNACE_COMPONENTS = "furnace:components";
    /** 서버가 직접 해석·재작성하는 최상위 키. 나머지는 extraNbt 로 보존된다. */
    private static final Set<String> KNOWN_KEYS = Set.of("DataVersion", "xPos", "zPos", "yPos", "Status", "LastUpdate",
            "sections", "Heightmaps", "block_entities", "entities", "isLightOn", "InhabitedTime", "PostProcessing", "structures");

    private final BlockRegistry registry;
    private final ItemRegistry items; // null 이면 상자를 초기 Furnace 형식으로 쓴다(바닐라 문자열 id 를 모름)
    private final List<String> biomeNames; // null 이면 바이옴을 저장·복원하지 않고 defaultBiome 사용
    private final Map<String, Integer> biomeIds = new HashMap<>();
    private final int defaultBiome;
    private final int minY;
    private final int sectionCount;
    private volatile LongSupplier gameTime = () -> 0L;

    public AnvilChunkCodec(BlockRegistry registry, int biomeId) {
        this(registry, biomeId, null);
    }

    public AnvilChunkCodec(BlockRegistry registry, int biomeId, ItemRegistry items) {
        this(registry, items, null, biomeId, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    public AnvilChunkCodec(BlockRegistry registry, ItemRegistry items, List<String> biomeNames, int defaultBiome,
                           int minY, int sectionCount) {
        this.registry = registry;
        this.items = items;
        this.biomeNames = biomeNames;
        this.defaultBiome = defaultBiome;
        this.minY = minY;
        this.sectionCount = sectionCount;
        if (biomeNames != null) for (int i = 0; i < biomeNames.size(); i++) biomeIds.put(biomeNames.get(i), i);
    }

    /** LastUpdate 에 쓸 월드 게임 시간 공급자. */
    public void gameTime(LongSupplier s) { this.gameTime = s; }

    // ------------------------------------------------------------------ write

    public NbtCompound toNbt(ChunkColumn col, int chunkX, int chunkZ) {
        return toNbt(ChunkSnapshot.forSave(chunkX, chunkZ, col));
    }

    public NbtCompound toNbt(ChunkSnapshot snap) {
        ChunkSnapshot.SaveData save = snap.save();
        int colMinY = save != null ? save.minY() : minY;
        NbtCompound root = save != null && save.extraNbt() != null ? save.extraNbt().copy() : new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", snap.chunkX());
        root.putInt("zPos", snap.chunkZ());
        root.putInt("yPos", colMinY >> 4);
        root.putString("Status", "minecraft:full");
        root.putLong("LastUpdate", gameTime.getAsLong());
        root.putLong("InhabitedTime", save != null ? save.inhabitedTime() : 0L);
        root.put("isLightOn", (byte) (snap.light() != null ? 1 : 0));

        ChunkSnapshot.Section[] secs = snap.sections();
        NbtList sections = new NbtList(NbtIo.TAG_COMPOUND);
        int minSection = colMinY >> 4;
        // 빛 배열은 ChunkLight.MIN_SECTION 부터 — 이 차원의 맨 아래 빛 섹션(minSection - 1)의 자리
        int lightBase = minSection - 1 - org.domi.furnace.server.light.ChunkLight.MIN_SECTION;
        for (int i = 0; i < secs.length; i++) {
            NbtCompound s = new NbtCompound();
            s.putCompound("block_states", blockStatesFromArray(secs[i].blocks()));
            s.putCompound("biomes", biomesFromArray(secs[i].biomes()));
            s.putByte("Y", (byte) (minSection + i));
            putLight(s, snap.light(), lightBase + i + 1);
            sections.add(s);
        }
        if (snap.light() != null) {   // 바닐라처럼 위·아래 빛 전용 섹션도 쓴다(비어 있으면 생략)
            for (int li : new int[]{0, secs.length + 1}) {
                NbtCompound s = new NbtCompound();
                s.putByte("Y", (byte) (minSection - 1 + li));
                putLight(s, snap.light(), lightBase + li);
                if (s.contains("SkyLight") || s.contains("BlockLight")) sections.add(s);
            }
        }
        root.putList("sections", sections);

        NbtCompound heightmaps = new NbtCompound();
        if (save != null) {
            heightmaps.putLongArray("OCEAN_FLOOR", save.oceanFloorHeightmap());
            heightmaps.putLongArray("MOTION_BLOCKING_NO_LEAVES", save.motionBlockingNoLeavesHeightmap());
        }
        heightmaps.putLongArray("MOTION_BLOCKING", snap.heightmap());
        heightmaps.putLongArray("WORLD_SURFACE", snap.worldSurfaceHeightmap());
        root.putCompound("Heightmaps", heightmaps);

        NbtList blockEntities = new NbtList(NbtIo.TAG_COMPOUND);
        for (ChunkSnapshot.BlockEntitySnapshot be : snap.blockEntities()) {
            int x = snap.chunkX() * 16 + be.localX();
            int z = snap.chunkZ() * 16 + be.localZ();
            String blockName = blockNameAt(snap, colMinY, be.localX(), be.y(), be.localZ());
            blockEntities.add(chestNbt(x, be.y(), z, blockName, be.tag()));
        }
        if (save != null) {
            for (ChunkSnapshot.BlockEntitySnapshot be : save.generatedBlockEntities()) {
                NbtCompound e = be.tag().copy();
                if (!(e.tags().get("id") instanceof String)) continue; // 바닐라도 id 없는 블록 엔티티는 버린다
                e.putInt("x", snap.chunkX() * 16 + be.localX());
                e.putInt("y", be.y());
                e.putInt("z", snap.chunkZ() * 16 + be.localZ());
                blockEntities.add(e);
            }
        }
        root.putList("block_entities", blockEntities);

        if (save != null && !save.entities().isEmpty()) {
            NbtList entities = new NbtList(NbtIo.TAG_COMPOUND);
            save.entities().forEach(entities::add);
            root.putList("entities", entities);
        }
        if (!root.contains("structures")) {
            NbtCompound structures = new NbtCompound();
            structures.putCompound("References", new NbtCompound());
            structures.putCompound("starts", new NbtCompound());
            root.putCompound("structures", structures);
        }
        if (!root.contains("block_ticks")) root.putList("block_ticks", new NbtList(NbtIo.TAG_END));
        if (!root.contains("fluid_ticks")) root.putList("fluid_ticks", new NbtList(NbtIo.TAG_END));
        NbtList postProcessing = new NbtList(NbtIo.TAG_LIST);
        for (int i = 0; i < secs.length; i++) postProcessing.add(new NbtList(NbtIo.TAG_END));
        root.putList("PostProcessing", postProcessing);
        return root;
    }

    private String blockNameAt(ChunkSnapshot snap, int colMinY, int lx, int y, int lz) {
        int sec = (y - colMinY) >> 4;
        ChunkSnapshot.Section[] secs = snap.sections();
        if (sec < 0 || sec >= secs.length) return null;
        int state = secs[sec].blocks()[((y - colMinY) & 15) * 256 + lz * 16 + lx];
        return registry.nameOf(state);
    }

    /** 상자 블록 엔티티. 아이템 레지스트리가 있으면 바닐라 형식, 없으면 초기 Furnace 형식. */
    private NbtCompound chestNbt(int x, int y, int z, String blockName, NbtCompound furnaceTag) {
        NbtCompound e = new NbtCompound();
        NbtList src = furnaceTag != null && furnaceTag.contains("Items") ? furnaceTag.getList("Items") : new NbtList(NbtIo.TAG_COMPOUND);
        if (items == null) {
            e.putString("id", LEGACY_CHEST_ID);
            e.putList("Items", src);
        } else {
            e.putString("id", "minecraft:trapped_chest".equals(blockName) ? "minecraft:trapped_chest" : "minecraft:chest");
            NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
            for (Object o : src.items()) {
                NbtCompound in = (NbtCompound) o;
                String name = items.nameOf(in.getInt("Id"));
                if (name == null || name.equals("minecraft:air")) continue;
                NbtCompound it = new NbtCompound();
                it.putByte("Slot", in.getByte("Slot"));
                it.putString("id", name);
                it.putInt("count", in.getInt("Count"));
                if (in.contains(org.domi.furnace.server.item.ItemNbt.KEY)) it.put(org.domi.furnace.server.item.ItemNbt.KEY, in.tags().get(org.domi.furnace.server.item.ItemNbt.KEY));
                out.add(it);
            }
            e.putList("Items", out);
        }
        if (furnaceTag != null && furnaceTag.tags().get("LootTable") instanceof String table) {
            e.putString("LootTable", table);
            if (furnaceTag.tags().get("LootTableSeed") instanceof Long seed) e.putLong("LootTableSeed", seed);
        }
        e.putInt("x", x);
        e.putInt("y", y);
        e.putInt("z", z);
        return e;
    }

    /** PalettedContainer.pack 의 블록 상태 형식. */
    private NbtCompound blockStatesFromArray(int[] blocks) {
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : blocks) {
            if (!indexOf.containsKey(v)) { indexOf.put(v, palette.size()); palette.add(v); }
        }
        NbtList paletteList = new NbtList(NbtIo.TAG_COMPOUND);
        for (int stateId : palette) paletteList.add(paletteEntryOf(stateId));
        NbtCompound bs = new NbtCompound();
        bs.putList("palette", paletteList);
        if (palette.size() > 1) {
            int bits = Math.max(4, ceillog2(palette.size()));
            BitStorage store = new BitStorage(bits, 4096);
            for (int i = 0; i < 4096; i++) store.set(i, indexOf.get(blocks[i]));
            bs.putLongArray("data", store.data());
        }
        return bs;
    }

    /** PalettedContainer.pack 의 바이옴 형식(1~3비트 선형, 그 이상은 ceillog2). */
    private NbtCompound biomesFromArray(int[] biomes) {
        NbtCompound out = new NbtCompound();
        NbtList paletteList = new NbtList(NbtIo.TAG_STRING);
        if (biomeNames == null) {
            paletteList.add("minecraft:plains");
            out.putList("palette", paletteList);
            return out;
        }
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : biomes) {
            if (!indexOf.containsKey(v)) { indexOf.put(v, palette.size()); palette.add(v); }
        }
        for (int id : palette) paletteList.add(id >= 0 && id < biomeNames.size() ? biomeNames.get(id) : "minecraft:plains");
        out.putList("palette", paletteList);
        if (palette.size() > 1) {
            BitStorage store = new BitStorage(ceillog2(palette.size()), 64);
            for (int i = 0; i < 64; i++) store.set(i, indexOf.get(biomes[i]));
            out.putLongArray("data", store.data());
        }
        return out;
    }

    private static int ceillog2(int n) { return 32 - Integer.numberOfLeadingZeros(n - 1); }

    // 팰릿 엔트리: canonical key(name 또는 name;k=v,...)를 Name + Properties(있으면)로 분해해 왕복 시 state 보존.
    private NbtCompound paletteEntryOf(int stateId) {
        NbtCompound entry = new NbtCompound();
        String key = registry.canonicalKeyOf(stateId);
        if (key == null) {
            entry.putString("Name", registry.nameOf(stateId));
            return entry;
        }
        int semi = key.indexOf(';');
        if (semi < 0) {
            entry.putString("Name", key);
            return entry;
        }
        entry.putString("Name", key.substring(0, semi));
        NbtCompound props = new NbtCompound();
        for (String kv : key.substring(semi + 1).split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            props.putString(kv.substring(0, eq), kv.substring(eq + 1));
        }
        entry.putCompound("Properties", props);
        return entry;
    }

    private static void putLight(NbtCompound s, ChunkSnapshot.Light l, int idx) {
        if (l == null) return;
        if (nonZero(l.sky()[idx])) s.putByteArray("SkyLight", l.sky()[idx]);
        if (nonZero(l.block()[idx])) s.putByteArray("BlockLight", l.block()[idx]);
    }

    private static boolean nonZero(byte[] a) {
        for (byte b : a) if (b != 0) return true;
        return false;
    }

    // ------------------------------------------------------------------ read

    /** 완성되지 않은 청크(Status ≠ full)는 null — 호출 측이 새로 생성한다. */
    public ChunkColumn fromNbt(NbtCompound root) {
        if (root.tags().get("Status") instanceof String status && !status.equals("minecraft:full") && !status.equals("full")) {
            return null;
        }
        ChunkColumn col = new ChunkColumn(defaultBiome, registry.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, minY, sectionCount);
        int minSection = minY >> 4;
        NbtList sections = root.contains("sections") ? root.getList("sections") : new NbtList(NbtIo.TAG_COMPOUND);
        for (Object o : sections.items()) {
            NbtCompound s = (NbtCompound) o;
            int sectionIdx = s.getByte("Y") - minSection;
            if (sectionIdx < 0 || sectionIdx >= sectionCount) continue; // 조명 전용 섹션(위·아래 한 칸)
            if (s.contains("block_states")) applyBlockStates(col, sectionIdx, s.getCompound("block_states"));
            if (biomeNames != null && s.contains("biomes")) applyBiomes(col, sectionIdx, s.getCompound("biomes"));
        }
        col.rebuildHeightmapsAfterSurface();
        if (root.contains("block_entities")) {
            for (Object o : root.getList("block_entities").items()) {
                // 블록 엔티티 하나가 이상해도 청크 전체 로드가 실패하면 안 된다(실패한 청크는 영구 로드 불가가 됐었다).
                try {
                    NbtCompound be = (NbtCompound) o;
                    String id = be.tags().get("id") instanceof String s ? s : null;
                    if (LEGACY_CHEST_ID.equals(id)) {
                        col.putChest(legacyChestFromNbt(be));
                    } else if (items != null && ("minecraft:chest".equals(id) || "minecraft:trapped_chest".equals(id))) {
                        col.putChest(vanillaChestFromNbt(be));
                    } else if (id != null && org.domi.furnace.server.block.entity.BlockEntities.known(id)) {
                        int x = be.getInt("x"), y = be.getInt("y"), z = be.getInt("z");
                        var pos = new org.domi.furnace.server.block.Pos(x, y, z);
                        var typed = org.domi.furnace.server.block.entity.BlockEntities.loadStatic(pos, col.getBlock(x & 15, y, z & 15), be);
                        if (typed != null && typed.isValid(col.getBlock(x & 15, y, z & 15))) {
                            col.putBlockEntity(typed);
                        } else {
                            // 바닐라 promotePendingBlockEntity 는 블록과 맞지 않으면 경고하고 버린다 — 여기서는 NBT 그대로 보존(데이터를 잃지 않게)
                            org.slf4j.LoggerFactory.getLogger(AnvilChunkCodec.class).warn("블록 엔티티 {} 가 ({},{},{}) 블록과 맞지 않음, NBT 보존", id, x, y, z);
                            col.putGeneratedBlockEntity(x & 15, y, z & 15, be);
                        }
                    } else if (id != null) {
                        // 아직 서버가 동작을 모르는 블록 엔티티(화로·표지판·스포너 등)는 NBT 그대로 보존해 다시 쓴다.
                        int x = be.getInt("x"), y = be.getInt("y"), z = be.getInt("z");
                        col.putGeneratedBlockEntity(x & 15, y, z & 15, be);
                    }
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(AnvilChunkCodec.class).warn("블록 엔티티 읽기 실패, 건너뜀", e);
                }
            }
        }
        if (root.contains("entities")) {
            for (Object o : root.getList("entities").items()) if (o instanceof NbtCompound e) col.addGeneratedEntity(e);
        }
        if (root.tags().get("InhabitedTime") instanceof Long t) col.inhabitedTime(t);
        NbtCompound extraStructures = null;
        if (root.tags().get("structures") instanceof NbtCompound st) {
            try {
                col.structures(org.domi.furnace.server.world.gen.structure.ChunkStructures.fromNbt(st));
            } catch (RuntimeException e) {
                // 해석에 실패해도 원본은 그대로 보존한다
                org.slf4j.LoggerFactory.getLogger(AnvilChunkCodec.class).warn("구조물 정보 읽기 실패, 원본만 보존", e);
                extraStructures = st;
            }
        }
        NbtCompound extra = new NbtCompound();
        for (var e : root.tags().entrySet()) if (!KNOWN_KEYS.contains(e.getKey())) extra.put(e.getKey(), e.getValue());
        if (extraStructures != null) extra.put("structures", extraStructures);
        if (!extra.tags().isEmpty()) col.extraNbt(extra);
        return col;
    }

    /** 바닐라 1.21 상자: Items[{Slot:byte, id:"minecraft:..", count:int, components?}]. components 는 바닐라 코덱으로 읽는다. */
    private ChestBlockEntity vanillaChestFromNbt(NbtCompound e) {
        int x = e.getInt("x");
        int y = e.getInt("y");
        int z = e.getInt("z");
        ItemStack[] stacks = new ItemStack[ChestBlockEntity.SLOTS];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        if (e.contains("Items")) {
            for (Object io : e.getList("Items").items()) {
                try {
                    NbtCompound ie = (NbtCompound) io;
                    int slot = ie.getByte("Slot") & 0xFF;
                    if (slot >= stacks.length) continue;
                    String name = ie.getString("id");
                    int itemId = items.idOf(name);
                    int count = ie.contains("count") ? ie.getInt("count") : 1;
                    if (itemId <= 0 || count <= 0) continue;
                    var patch = org.domi.furnace.server.item.ItemNbt.read(ie, name, FURNACE_COMPONENTS);
                    if (patch == null) continue;
                    stacks[slot] = new ItemStack(itemId, count, patch);
                } catch (RuntimeException ex) {
                    // 이상한 항목은 그 슬롯만 건너뛴다
                }
            }
        }
        ChestBlockEntity chest = new ChestBlockEntity("minecraft:trapped_chest".equals(e.tags().get("id")) ? "minecraft:trapped_chest" : "minecraft:chest", x, y, z, stacks);
        if (e.tags().get("LootTable") instanceof String table) {
            chest.lootTable = table;
            chest.lootSeed = e.tags().get("LootTableSeed") instanceof Long seed ? seed : 0L;
        }
        return chest;
    }

    private ChestBlockEntity legacyChestFromNbt(NbtCompound e) {
        int x = e.getInt("x");
        int y = e.getInt("y");
        int z = e.getInt("z");
        ItemStack[] stacks = new ItemStack[ChestBlockEntity.SLOTS];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        if (e.contains("Items")) {
            for (Object io : e.getList("Items").items()) {
                NbtCompound ie = (NbtCompound) io;
                int slot = ie.getByte("Slot") & 0xFF;
                if (slot >= stacks.length) continue;
                int itemId = ie.getInt("Id");
                var patch = org.domi.furnace.server.item.ItemNbt.read(ie, items != null ? items.nameOf(itemId) : null, "Comp");
                if (patch == null) continue;
                stacks[slot] = new ItemStack(itemId, ie.getInt("Count"), patch);
            }
        }
        return new ChestBlockEntity(x, y, z, stacks);
    }

    private void applyBiomes(ChunkColumn col, int sectionIdx, NbtCompound bs) {
        NbtList paletteList = bs.getList("palette");
        int[] ids = new int[paletteList.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = biomeIds.getOrDefault((String) paletteList.get(i), defaultBiome);
        if (ids.length == 0) return;
        ChunkSection section = col.sections()[sectionIdx];
        if (!bs.contains("data")) {
            section.fillBiome(ids[0]);
            return;
        }
        BitStorage store = BitStorage.wrap(ceillog2(ids.length), 64, bs.getLongArray("data"));
        for (int i = 0; i < 64; i++) section.setBiome(i & 3, i >> 4, (i >> 2) & 3, ids[store.get(i)]);
    }

    private void applyBlockStates(ChunkColumn col, int sectionIdx, NbtCompound bs) {
        NbtList paletteList = bs.getList("palette");
        int[] paletteIds = new int[paletteList.size()];
        for (int i = 0; i < paletteIds.length; i++) {
            NbtCompound entry = (NbtCompound) paletteList.get(i);
            String name = entry.getString("Name");
            if (entry.contains("Properties")) {
                Map<String, String> props = new HashMap<>();
                for (var e : entry.getCompound("Properties").tags().entrySet()) {
                    props.put(e.getKey(), (String) e.getValue());
                }
                paletteIds[i] = registry.stateIdOrAir(name, props);
            } else {
                paletteIds[i] = registry.stateIdOrAir(name);
            }
        }
        int baseY = minY + sectionIdx * 16;
        if (!bs.contains("data")) {
            if (paletteIds.length == 0 || paletteIds[0] == ChunkSection.AIR) return; // 전부 air
            for (int idx = 0; idx < 4096; idx++) setFromIndex(col, baseY, idx, paletteIds[0]);
            return;
        }
        long[] data = bs.getLongArray("data");
        int bits = Math.max(4, ceillog2(paletteIds.length));
        BitStorage store = BitStorage.wrap(bits, 4096, data);
        for (int idx = 0; idx < 4096; idx++) {
            int stateId = paletteIds[store.get(idx)];
            if (stateId != ChunkSection.AIR) setFromIndex(col, baseY, idx, stateId);
        }
    }

    // 섹션 로컬 index(y*256+z*16+x) → ChunkColumn.setBlock(x, worldY, z)
    private void setFromIndex(ChunkColumn col, int baseY, int idx, int stateId) {
        col.setBlock(idx & 15, baseY + (idx >> 8), (idx >> 4) & 15, stateId);
    }
}
