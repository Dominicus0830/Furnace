// ChunkColumn ↔ 표준 Anvil 청크 NBT. 팰릿 속성 무시(Name→default state id 근사).
package org.domi.furnace.server.world.anvil;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnvilChunkCodec {

    private static final int DATA_VERSION = 3955; // 1.21.1
    private final BlockRegistry registry;
    private final int biomeId; // 단일 바이옴(superflat/근사). 바닐라 read 시 바이옴은 무시하고 이 값 사용.

    public AnvilChunkCodec(BlockRegistry registry, int biomeId) {
        this.registry = registry;
        this.biomeId = biomeId;
    }

    public NbtCompound toNbt(ChunkColumn col, int chunkX, int chunkZ) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", chunkX);
        root.putInt("zPos", chunkZ);
        root.putInt("yPos", -4);
        root.putString("Status", "minecraft:full");

        NbtList sections = new NbtList(NbtIo.TAG_COMPOUND);
        ChunkSection[] secs = col.sections();
        for (int i = 0; i < secs.length; i++) {
            NbtCompound s = new NbtCompound();
            s.putByte("Y", (byte) (i - 4));
            s.putCompound("block_states", blockStatesFromArray(secs[i].blocks()));
            sections.add(s);
        }
        root.putList("sections", sections);

        NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", col.motionBlockingHeightmap());
        root.putCompound("Heightmaps", heightmaps);
        return root;
    }

    public NbtCompound toNbt(org.domi.furnace.server.world.ChunkSnapshot snap) {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", DATA_VERSION);
        root.putInt("xPos", snap.chunkX());
        root.putInt("zPos", snap.chunkZ());
        root.putInt("yPos", -4);
        root.putString("Status", "minecraft:full");
        NbtList sections = new NbtList(NbtIo.TAG_COMPOUND);
        org.domi.furnace.server.world.ChunkSnapshot.Section[] secs = snap.sections();
        for (int i = 0; i < secs.length; i++) {
            NbtCompound s = new NbtCompound();
            s.putByte("Y", (byte) (i - 4));
            s.putCompound("block_states", blockStatesFromArray(secs[i].blocks()));
            sections.add(s);
        }
        root.putList("sections", sections);
        NbtCompound heightmaps = new NbtCompound();
        heightmaps.putLongArray("MOTION_BLOCKING", snap.heightmap());
        root.putCompound("Heightmaps", heightmaps);
        return root;
    }

    private NbtCompound blockStatesFromArray(int[] blocks) {
        Map<Integer, Integer> indexOf = new HashMap<>();
        List<Integer> palette = new ArrayList<>();
        for (int v : blocks) {
            if (!indexOf.containsKey(v)) { indexOf.put(v, palette.size()); palette.add(v); }
        }
        NbtList paletteList = new NbtList(NbtIo.TAG_COMPOUND);
        for (int stateId : palette) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Name", registry.nameOf(stateId));
            paletteList.add(entry);
        }
        NbtCompound bs = new NbtCompound();
        bs.putList("palette", paletteList);
        if (palette.size() > 1) {
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            BitStorage store = new BitStorage(bits, 4096);
            for (int i = 0; i < 4096; i++) store.set(i, indexOf.get(blocks[i]));
            bs.putLongArray("data", store.data());
        }
        return bs;
    }

    public ChunkColumn fromNbt(NbtCompound root) {
        ChunkColumn col = new ChunkColumn(biomeId, registry.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS);
        NbtList sections = root.getList("sections");
        for (Object o : sections.items()) {
            NbtCompound s = (NbtCompound) o;
            int y = s.getByte("Y");
            int sectionIdx = y + 4;
            if (sectionIdx < 0 || sectionIdx >= ChunkColumn.SECTION_COUNT) continue;
            if (!s.contains("block_states")) continue;
            applyBlockStates(col, sectionIdx, s.getCompound("block_states"));
        }
        recomputeHeightmap(col);
        return col;
    }

    // NBT엔 Heightmaps가 있으나 신뢰 않고 블록서 재계산(바닐라 월드도 정확). 최상단 비공기 → surface.
    private void recomputeHeightmap(ChunkColumn col) {
        int top = ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16 - 1;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = 0;
                for (int wy = top; wy >= ChunkColumn.MIN_Y; wy--) {
                    if (col.getBlock(x, wy, z) != ChunkSection.AIR) {
                        h = (wy - ChunkColumn.MIN_Y) + 1;
                        break;
                    }
                }
                col.setSurface(x, z, h);
            }
        }
    }

    private void applyBlockStates(ChunkColumn col, int sectionIdx, NbtCompound bs) {
        NbtList paletteList = bs.getList("palette");
        int[] paletteIds = new int[paletteList.size()];
        for (int i = 0; i < paletteIds.length; i++) {
            NbtCompound entry = (NbtCompound) paletteList.get(i);
            paletteIds[i] = registry.stateIdOrAir(entry.getString("Name"));
        }
        int baseY = ChunkColumn.MIN_Y + sectionIdx * 16;
        if (!bs.contains("data")) {
            if (paletteIds.length == 0 || paletteIds[0] == ChunkSection.AIR) return; // 전부 air
            for (int idx = 0; idx < 4096; idx++) setFromIndex(col, baseY, idx, paletteIds[0]);
            return;
        }
        long[] data = bs.getLongArray("data");
        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteIds.length - 1));
        BitStorage store = BitStorage.wrap(bits, 4096, data);
        for (int idx = 0; idx < 4096; idx++) {
            int stateId = paletteIds[store.get(idx)];
            if (stateId != ChunkSection.AIR) setFromIndex(col, baseY, idx, stateId);
        }
    }

    // 섹션 로컬 index(y*256+z*16+x) → ChunkColumn.setBlock(x, worldY, z)
    private void setFromIndex(ChunkColumn col, int baseY, int idx, int stateId) {
        int x = idx & 15;
        int z = (idx >> 4) & 15;
        int yLocal = idx >> 8;
        col.setBlock(x, baseY + yLocal, z, stateId);
    }
}
