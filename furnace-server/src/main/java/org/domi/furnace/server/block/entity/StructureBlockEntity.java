// 바닐라 StructureBlockEntity: 구조물 블록의 상태(이름·작성자·메타데이터·오프셋·크기·미러·회전·모드·옵션)와 저장/불러오기/크기 감지/배치.
// 저장 키: name, author, metadata, posX/Y/Z, sizeX/Y/Z, rotation, mirror, mode, ignoreEntities, powered, showair, showboundingbox, integrity, seed.
// 클라이언트에는 청크 패킷과 block_entity_data 로 saveCustomOnly 를 보낸다(편집 화면은 클라이언트가 그 데이터로 연다).
// Furnace 차이: 배치·저장·크기 감지는 이 칸의 region 이 가진 청크만 본다(바닐라는 필요한 청크를 그 자리에서 불러온다) — 영역이 다른 region 이나
// 로드 안 된 청크에 걸치면 저장은 실패(save_failure), 불러오기는 아무것도 놓지 않는다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.HangingEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.LiveGrid;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processor;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate.BlockInfo;
import org.domi.furnace.server.world.gen.structure.template.TemplateManager;

public class StructureBlockEntity extends BlockEntity {

    /** StructureMode — 서버 수신 패킷의 열거 번호 순서(SAVE, LOAD, CORNER, DATA). */
    public enum Mode {
        SAVE("save"), LOAD("load"), CORNER("corner"), DATA("data");

        public final String stateName;

        Mode(String stateName) { this.stateName = stateName; }

        static Mode byStateName(String n) {
            for (Mode m : values()) if (m.stateName.equals(n)) return m;
            return DATA;
        }
    }

    /** UpdateType — set_structure_block 패킷의 동작 번호 순서. */
    public enum UpdateType { UPDATE_DATA, SAVE_AREA, LOAD_AREA, SCAN_AREA }

    /** Rotation 의 toString/valueOf 이름 — 번호는 BlockStateTable.ROT_* 와 같다. */
    public static final String[] ROTATION_NAMES = {"NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"};
    /** Mirror 의 이름 — 번호는 BlockStateTable.NONE/LEFT_RIGHT/FRONT_BACK. */
    public static final String[] MIRROR_NAMES = {"NONE", "LEFT_RIGHT", "FRONT_BACK"};

    public static final int MAX_OFFSET = 48, MAX_SIZE = 48, SCAN_RANGE = 80;

    private static final Prop MODE = B.prop("mode");
    private static final int STRUCTURE_BLOCK = B.id("structure_block"), STRUCTURE_VOID = B.id("structure_void");

    private ResourceId name;
    private String author = "";
    private String metaData = "";
    private int[] structurePos = {0, 1, 0};
    private int[] structureSize = {0, 0, 0};
    private int mirror = BlockStateTable.NONE;
    private int rotation = BlockStateTable.ROT_NONE;
    private Mode mode;
    private boolean ignoreEntities = true;
    private boolean powered;
    private boolean showAir;
    private boolean showBoundingBox = true;
    private float integrity = 1.0F;
    private long seed;

    public StructureBlockEntity(Pos pos, int state) {
        super("minecraft:structure_block", pos, state);
        this.mode = MODE.has(state) ? Mode.byStateName(MODE.valueName(state)) : Mode.DATA;
    }

    @Override
    public boolean isValid(int state) { return B.block(state) == STRUCTURE_BLOCK; }

    @Override
    public boolean hasUpdatePacket() { return true; }

    /** getUpdateTag = saveCustomOnly. */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void saveAdditional(NbtCompound tag) {
        tag.putString("name", getStructureName());
        tag.putString("author", author);
        tag.putString("metadata", metaData);
        tag.putInt("posX", structurePos[0]);
        tag.putInt("posY", structurePos[1]);
        tag.putInt("posZ", structurePos[2]);
        tag.putInt("sizeX", structureSize[0]);
        tag.putInt("sizeY", structureSize[1]);
        tag.putInt("sizeZ", structureSize[2]);
        tag.putString("rotation", ROTATION_NAMES[rotation]);
        tag.putString("mirror", MIRROR_NAMES[mirror]);
        tag.putString("mode", mode.name());
        tag.putBoolean("ignoreEntities", ignoreEntities);
        tag.putBoolean("powered", powered);
        tag.putBoolean("showair", showAir);
        tag.putBoolean("showboundingbox", showBoundingBox);
        tag.putFloat("integrity", integrity);
        tag.putLong("seed", seed);
    }

    @Override
    public void load(NbtCompound tag) {
        setStructureName(tag.getString("name"));
        author = tag.getString("author");
        metaData = tag.getString("metadata");
        structurePos = new int[] {clamp(tag.getInt("posX"), -MAX_OFFSET, MAX_OFFSET), clamp(tag.getInt("posY"), -MAX_OFFSET, MAX_OFFSET),
                clamp(tag.getInt("posZ"), -MAX_OFFSET, MAX_OFFSET)};
        structureSize = new int[] {clamp(tag.getInt("sizeX"), 0, MAX_SIZE), clamp(tag.getInt("sizeY"), 0, MAX_SIZE), clamp(tag.getInt("sizeZ"), 0, MAX_SIZE)};
        rotation = indexOf(ROTATION_NAMES, tag.getString("rotation"), BlockStateTable.ROT_NONE);
        mirror = indexOf(MIRROR_NAMES, tag.getString("mirror"), BlockStateTable.NONE);
        mode = Mode.DATA;
        for (Mode m : Mode.values()) if (m.name().equals(tag.getString("mode"))) mode = m;
        ignoreEntities = tag.getBoolean("ignoreEntities");
        powered = tag.getBoolean("powered");
        showAir = tag.getBoolean("showair");
        showBoundingBox = tag.getBoolean("showboundingbox");
        integrity = tag.contains("integrity") ? tag.getFloat("integrity") : 1.0F;
        seed = tag.getLong("seed");
        // updateBlockState 는 레벨이 있을 때만 — afterLoad
    }

    /** 레벨에 놓인 뒤 읽었으면(아이템의 block_entity_data 등) 블록 상태의 mode 를 맞춘다. */
    @Override
    public void afterLoad(BlockLevel level) { updateBlockState(level); }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int indexOf(String[] names, String s, int def) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(s)) return i;
        return def;
    }

    /** 칸의 블록이 구조물 블록이면 mode 속성을 이 BE 의 모드로(플래그 2). */
    private void updateBlockState(BlockLevel level) {
        if (level == null) return;
        int cur = level.getBlockState(pos);
        if (B.block(cur) == STRUCTURE_BLOCK) level.setBlock(pos, MODE.with(cur, mode.stateName), 2);
    }

    // ------------------------------------------------------------------ 접근자(바닐라 getter/setter)

    /** getStructureName: 없으면 빈 문자열, 있으면 "네임스페이스:경로". */
    public String getStructureName() { return name == null ? "" : name.toString(); }

    public boolean hasStructureName() { return name != null; }

    public ResourceId structureId() { return name; }

    /** setStructureName(String): 비었거나 올바르지 않으면 이름 없음. */
    public void setStructureName(String s) {
        name = s == null || s.isBlank() ? null : ResourceId.tryParse(s);
    }

    public String author() { return author; }

    public void setAuthor(String a) { this.author = a; }

    public String metaData() { return metaData; }

    public void setMetaData(String m) { this.metaData = m; }

    public int[] structurePos() { return structurePos.clone(); }

    public void setStructurePos(int x, int y, int z) { this.structurePos = new int[] {x, y, z}; }

    public int[] structureSize() { return structureSize.clone(); }

    public void setStructureSize(int x, int y, int z) { this.structureSize = new int[] {x, y, z}; }

    public int mirror() { return mirror; }

    public void setMirror(int m) { this.mirror = m; }

    public int rotation() { return rotation; }

    public void setRotation(int r) { this.rotation = r; }

    public Mode mode() { return mode; }

    /** setMode: 블록 상태도 따라 바꾼다(레벨이 있을 때). */
    public void setMode(Mode m, BlockLevel level) {
        this.mode = m;
        updateBlockState(level);
    }

    public boolean ignoreEntities() { return ignoreEntities; }

    public void setIgnoreEntities(boolean b) { this.ignoreEntities = b; }

    public float integrity() { return integrity; }

    public void setIntegrity(float f) { this.integrity = f; }

    public long seed() { return seed; }

    public void setSeed(long s) { this.seed = s; }

    public boolean isPowered() { return powered; }

    public void setPowered(boolean b) { this.powered = b; }

    public boolean showAir() { return showAir; }

    public void setShowAir(boolean b) { this.showAir = b; }

    public boolean showBoundingBox() { return showBoundingBox; }

    public void setShowBoundingBox(boolean b) { this.showBoundingBox = b; }

    /** createdBy: 놓은 이의 이름을 작성자로(서버 쪽 setPlacedBy). */
    public void createdBy(String placerName) { this.author = placerName; }

    // ------------------------------------------------------------------ 동작

    private TemplateManager manager(RegionLevel level) {
        var w = level.world();
        return TemplateManager.forWorld(w.worldDir(), w.blockRegistry());
    }

    /** createRandom: 0 이면 지금 시각, 아니면 그 시드(레거시 난수). */
    public static LegacyRandom createRandom(long seed) { return new LegacyRandom(seed == 0L ? System.currentTimeMillis() : seed); }

    /** saveStructure(): 모드가 SAVE 일 때만. 템플릿을 채우고 .nbt 로 쓴다. */
    public boolean saveStructure(BlockLevel level) { return saveStructure(level, true); }

    /** saveStructure(boolean): toDisk 가 거짓이면 메모리 템플릿만 갱신한다(레드스톤 신호). */
    public boolean saveStructure(BlockLevel lv, boolean toDisk) {
        if (mode != Mode.SAVE || name == null || !(lv instanceof RegionLevel level)) return false;
        TemplateManager mgr = manager(level);
        StructureTemplate base = mgr.getOrCreate(name);
        StructureTemplate t = base;
        int sx = structureSize[0], sy = structureSize[1], sz = structureSize[2];
        if (sx >= 1 && sy >= 1 && sz >= 1) {
            t = fillFromWorld(level, mgr.table(), pos.x() + structurePos[0], pos.y() + structurePos[1], pos.z() + structurePos[2], sx, sy, sz, !ignoreEntities);
            if (t == null) return false;   // 영역이 이 region 의 로드된 청크 밖
            mgr.put(name, t);
        }
        t.author(author);
        if (toDisk) {
            try {
                return mgr.save(name);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * StructureTemplate.fillFromWorld: 영역의 블록(구조물 빈 칸 제외)·블록 엔티티 NBT·엔티티(플레이어 제외)를 담은 새 템플릿.
     * 영역이 이 레벨에 로드돼 있지 않으면 null.
     */
    static StructureTemplate fillFromWorld(RegionLevel level, BlockStateTable table, int ox, int oy, int oz, int sx, int sy, int sz, boolean includeEntities) {
        int x1 = ox + sx - 1, y1 = oy + sy - 1, z1 = oz + sz - 1;
        for (int cx = ox >> 4; cx <= x1 >> 4; cx++)
            for (int cz = oz >> 4; cz <= z1 >> 4; cz++)
                if (level.listenerChunk(cx, cz) == null) return null;
        List<BlockInfo> infos = new ArrayList<>();
        for (int y = oy; y <= y1; y++) {
            for (int z = oz; z <= z1; z++) {
                for (int x = ox; x <= x1; x++) {
                    Pos p = new Pos(x, y, z);
                    int state = level.getBlockState(p);
                    if (B.block(state) == STRUCTURE_VOID) continue;
                    infos.add(new BlockInfo(x - ox, y - oy, z - oz, state, blockEntityNbt(level, p)));
                }
            }
        }
        List<StructureTemplate.EntityInfo> entities = new ArrayList<>();
        if (includeEntities) {
            AABB box = new AABB(ox, oy, oz, x1 + 1, y1 + 1, z1 + 1);
            // Furnace 의 Player 는 Entity 가 아니라 따로 관리돼 목록에 들어오지 않는다(바닐라: 플레이어 제외)
            for (Entity e : level.getEntitiesOfClass(Entity.class, box, en -> true)) {
                double ex = e.x() - ox, ey = e.y() - oy, ez = e.z() - oz;
                int bx, by, bz;
                if (e instanceof HangingEntity h && h.pos() != null) {
                    bx = h.pos().x() - ox;
                    by = h.pos().y() - oy;
                    bz = h.pos().z() - oz;
                } else {
                    bx = (int) Math.floor(ex);
                    by = (int) Math.floor(ey);
                    bz = (int) Math.floor(ez);
                }
                NbtCompound nbt = e.save();   // 탈것에 탄 것은 탈것이 저장한다 → 바닐라처럼 빈 태그
                entities.add(new StructureTemplate.EntityInfo(ex, ey, ez, bx, by, bz, nbt == null ? new NbtCompound() : nbt.copy()));
            }
        }
        return StructureTemplate.ofWorld(sx, sy, sz, StructureTemplate.orderForSave(infos), entities);
    }

    /** saveWithId: 칸의 블록 엔티티 NBT(id 포함, x·y·z 없음). 아직 이식하지 않은 종류는 청크가 들고 있는 생성 NBT. 없으면 null. */
    static NbtCompound blockEntityNbt(RegionLevel level, Pos p) {
        if (level.getBlockEntity(p) instanceof BlockEntity be) {
            NbtCompound tag = be.saveWithoutMetadata();
            tag.putString("id", be.type());
            return tag;
        }
        ChunkColumn col = level.listenerChunk(p.x() >> 4, p.z() >> 4);
        NbtCompound gen = col == null ? null : col.generatedBlockEntity(p.x() & 15, p.y(), p.z() & 15);
        if (gen == null) return null;
        NbtCompound tag = gen.copy();
        tag.tags().remove("x");
        tag.tags().remove("y");
        tag.tags().remove("z");
        return tag;
    }

    /** isStructureLoadable: 모드가 LOAD 이고 이름이 있고 그 템플릿이 있다. */
    public boolean isStructureLoadable(BlockLevel level) {
        if (mode != Mode.LOAD || name == null || !(level instanceof RegionLevel rl)) return false;
        return manager(rl).get(name).isPresent();
    }

    /** 바닐라 getStructureTemplate. */
    private Optional<StructureTemplate> template(RegionLevel level) { return name == null ? Optional.empty() : manager(level).get(name); }

    /**
     * placeStructureIfSameSize: LOAD 모드이고 템플릿이 있다. 크기가 같으면 놓고 true. 다르면 작성자·크기만 템플릿 것으로 바꾸고 false
     * (클라이언트가 크기를 맞춘 뒤 다시 누르게 한다).
     */
    public boolean placeStructureIfSameSize(BlockLevel level) {
        if (mode != Mode.LOAD || name == null || !(level instanceof RegionLevel rl)) return false;
        StructureTemplate t = template(rl).orElse(null);
        if (t == null) return false;
        if (t.sizeX == structureSize[0] && t.sizeY == structureSize[1] && t.sizeZ == structureSize[2]) {
            placeStructure(rl, t);
            return true;
        }
        loadStructureInfo(level, t);
        return false;
    }

    /** loadStructure(level) — 블록의 신호(불러오기 모드)가 부른다: 템플릿이 있으면 크기 검사 없이 놓는다. */
    public void loadStructure(BlockLevel level) {
        if (!(level instanceof RegionLevel rl)) return;
        StructureTemplate t = template(rl).orElse(null);
        if (t != null) placeStructure(rl, t);
    }

    /** loadStructureInfo: 템플릿의 작성자·크기를 이 블록으로. */
    private void loadStructureInfo(BlockLevel level, StructureTemplate t) {
        author = t.author() != null && !t.author().isEmpty() ? t.author() : "";
        structureSize = new int[] {t.sizeX, t.sizeY, t.sizeZ};
        setChanged(level);
    }

    /** unloadStructure: 저장소에서 그 이름의 템플릿을 뺀다(다음 불러오기는 파일에서 다시 읽는다). */
    public void unloadStructure(BlockLevel level) {
        if (name == null || !(level instanceof RegionLevel rl)) return;
        manager(rl).remove(name);
    }

    /**
     * placeStructure(level, template): 미러·회전·엔티티 무시·완전성(integrity &lt; 1 이면 BlockRot 프로세서와 시드 난수)로 칸 + 오프셋에 놓는다.
     * 영역이 이 region 의 로드된 청크 안에 다 있지 않으면 아무것도 놓지 않고 false.
     */
    public boolean placeStructure(RegionLevel level, StructureTemplate t) {
        TemplateManager mgr = manager(level);
        PlaceSettings st = new PlaceSettings();
        st.mirror = mirror;
        st.rotation = rotation;
        st.ignoreEntities = ignoreEntities;
        if (integrity < 1.0F) {
            st.processors.clear();
            st.processors.add(new Processors.BlockRot(null, Math.max(0.0F, Math.min(1.0F, integrity))));
            st.random = createRandom(seed);
        }
        int ox = pos.x() + structurePos[0], oy = pos.y() + structurePos[1], oz = pos.z() + structurePos[2];
        if (t.sizeX < 1 || t.sizeY < 1 || t.sizeZ < 1) return false;
        int[] bb = t.boundingBox(ox, oy, oz, rotation, 0, 0, mirror);
        int minCx = Math.min(ox >> 4, bb[0] >> 4), minCz = Math.min(oz >> 4, bb[2] >> 4);
        int maxCx = Math.max(ox >> 4, bb[3] >> 4), maxCz = Math.max(oz >> 4, bb[5] >> 4);
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++)
                if (level.listenerChunk(cx, cz) == null) return false;
        try {
            LiveGrid grid = LiveGrid.covering(level, minCx, minCz, maxCx, maxCz, 0L);
            BlockShapes shapes = BlockShapes.of(mgr.registry());
            BlockStateTable table = mgr.table();
            boolean ok = org.domi.furnace.server.world.gen.structure.template.TemplatePlacer.placeInWorld(t, grid.view, shapes, table,
                    new Processor.Ctx(grid.view, table), ox, oy, oz, ox, oy, oz, st, createRandom(seed), 2);
            if (!ok) return false;
            grid.checkComplete();
            grid.commit(2);
            return true;
        } catch (LiveGrid.GridException e) {
            return false;
        }
    }

    /**
     * detectSize(): 모드가 SAVE 일 때, 주변 ±80 칸 안의 같은 이름 모서리 블록들과 이 블록을 감싸는 상자가 각 축 2 칸을 넘으면
     * 그 안쪽을 구조물 영역(오프셋·크기)으로 삼는다. 이 region 이 가진 청크의 블록 엔티티만 본다.
     */
    public boolean detectSize(BlockLevel level) {
        if (mode != Mode.SAVE || !(level instanceof RegionLevel rl)) return false;
        int minX = pos.x(), minY = pos.y(), minZ = pos.z(), maxX = minX, maxY = minY, maxZ = minZ;
        boolean any = false;
        int x0 = pos.x() - SCAN_RANGE, x1 = pos.x() + SCAN_RANGE, z0 = pos.z() - SCAN_RANGE, z1 = pos.z() + SCAN_RANGE;
        int y0 = rl.minBuildHeight(), y1 = rl.maxBuildHeight() - 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                ChunkColumn col = rl.listenerChunk(cx, cz);
                if (col == null) continue;
                for (BlockEntity be : new ArrayList<>(col.blockEntities().values())) {
                    Pos p = be.pos();
                    if (p.x() < x0 || p.x() > x1 || p.z() < z0 || p.z() > z1 || p.y() < y0 || p.y() > y1) continue;
                    if (!(be instanceof StructureBlockEntity other) || other == this || B.block(rl.getBlockState(p)) != STRUCTURE_BLOCK) continue;
                    if (!other.isRelatedTo(this)) continue;
                    any = true;
                    minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
                    maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
                }
            }
        }
        if (!any) return false;
        int dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        if (dx > 1 && dy > 1 && dz > 1) {
            structurePos = new int[] {minX - pos.x() + 1, minY - pos.y() + 1, minZ - pos.z() + 1};
            structureSize = new int[] {dx - 1, dy - 1, dz - 1};
            setChanged(level);
            level.sendBlockUpdated(pos);
            return true;
        }
        return false;
    }

    /** isRelatedTo(other): 이 블록이 CORNER 모드이고 저쪽과 이름이 같다. */
    private boolean isRelatedTo(StructureBlockEntity other) { return mode == Mode.CORNER && java.util.Objects.equals(name, other.name); }

    /** 서버 수신 set_structure_block 의 필드 적용(바닐라 handleSetStructureBlock 의 앞부분). */
    public void applyEdit(Mode m, String structName, int[] offset, int[] size, int mirror, int rotation, String metadata, boolean ignoreEntities,
                          boolean showAir, boolean showBoundingBox, float integrity, long seed, BlockLevel level) {
        setMode(m, level);
        setStructureName(structName);
        this.structurePos = offset.clone();
        this.structureSize = size.clone();
        this.mirror = mirror;
        this.rotation = rotation;
        this.metaData = metadata;
        this.ignoreEntities = ignoreEntities;
        this.showAir = showAir;
        this.showBoundingBox = showBoundingBox;
        this.integrity = integrity;
        this.seed = seed;
    }
}
