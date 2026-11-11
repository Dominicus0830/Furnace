// 바닐라 TemplateStructurePiece — 템플릿 한 장을 배치하는 조각(structure_block DATA 마커·jigsaw final_state 처리).
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate.BlockInfo;
import org.domi.furnace.server.world.gen.structure.template.TemplatePlacer;

public abstract class TemplatePiece extends StructurePiece {

    protected final String templateName;
    protected final StructureTemplate template;
    protected PlaceSettings settings;
    /** templatePosition */
    protected int tx, ty, tz;

    protected TemplatePiece(String templateName, StructureTemplate template, PlaceSettings settings, int x, int y, int z) {
        super(boxOf(template, settings, x, y, z), Direction.NORTH);
        this.templateName = templateName;
        this.template = template;
        this.settings = settings;
        this.tx = x;
        this.ty = y;
        this.tz = z;
    }

    private static BoundingBox boxOf(StructureTemplate t, PlaceSettings st, int x, int y, int z) {
        int[] b = t.boundingBox(x, y, z, st.rotation, st.pivotX, st.pivotZ, st.mirror);
        return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    public String templateName() { return templateName; }

    /** 바닐라 TemplateStructurePiece: TPX·TPY·TPZ(템플릿 위치), Template(템플릿 id). */
    @Override
    protected void saveAdditional(NbtCompound t) {
        t.putInt("TPX", tx);
        t.putInt("TPY", ty);
        t.putInt("TPZ", tz);
        t.putString("Template", templateName.indexOf(':') < 0 ? "minecraft:" + templateName : templateName);
    }

    /** 바닐라 Rotation 이름(Enum.name — 저장 키 Rot/rotation 이 valueOf 로 읽는다). */
    static String rotationName(int r) {
        return switch (r) {
            case BlockStateTable.ROT_CW90 -> "CLOCKWISE_90";
            case BlockStateTable.ROT_CW180 -> "CLOCKWISE_180";
            case BlockStateTable.ROT_CCW90 -> "COUNTERCLOCKWISE_90";
            default -> "NONE";
        };
    }

    /** 바닐라 Mirror 이름(Enum.name). */
    static String mirrorName(int m) {
        return switch (m) {
            case BlockStateTable.LEFT_RIGHT -> "LEFT_RIGHT";
            case BlockStateTable.FRONT_BACK -> "FRONT_BACK";
            default -> "NONE";
        };
    }

    public StructureTemplate template() { return template; }

    public PlaceSettings settings() { return settings; }

    public int tx() { return tx; }

    public int ty() { return ty; }

    public int tz() { return tz; }

    @Override
    public void move(int dx, int dy, int dz) {
        super.move(dx, dy, dz);
        tx += dx;
        ty += dy;
        tz += dz;
    }

    /** 바닐라 StructureTemplate.calculateRelativePosition(settings, pos) — 원점 가산 전 변환 좌표. */
    public static int[] relative(PlaceSettings st, int x, int y, int z) {
        return StructureTemplate.transform(x, y, z, st.mirror, st.rotation, st.pivotX, st.pivotZ);
    }

    @Override
    public void postProcess(PostCtx c) {
        settings.boundingBox = new int[]{c.writableBox.minX(), c.writableBox.minY(), c.writableBox.minZ(),
            c.writableBox.maxX(), c.writableBox.maxY(), c.writableBox.maxZ()};
        BoundingBox nb = boxOf(template, settings, tx, ty, tz);
        boundingBox = nb;
        if (TemplatePlacer.placeInWorld(template, c.view, c.shapes(), c.table(), c.processorCtx(),
                tx, ty, tz, c.pivotX, c.pivotY, c.pivotZ, settings, c.random, 2)) {
            BlockStateTable table = c.table();
            for (BlockInfo info : TemplatePlacer.filterBlocks(template, table, settings, tx, ty, tz, "minecraft:structure_block")) {
                if (info.nbt() != null && "DATA".equals(info.nbt().getString("mode"))) {
                    handleDataMarker(info.nbt().getString("metadata"), info.x(), info.y(), info.z(), c);
                }
            }
            for (BlockInfo info : TemplatePlacer.filterBlocks(template, table, settings, tx, ty, tz, "minecraft:jigsaw")) {
                if (info.nbt() == null) continue;
                String finalState = info.nbt().getString("final_state");
                int state = c.processorCtx().parseBlockState(finalState);
                if (state < 0) state = 0;
                c.view.setBlock(info.x(), info.y(), info.z(), state);
            }
        }
    }

    protected abstract void handleDataMarker(String marker, int x, int y, int z, PostCtx c);

    /**
     * 바닐라: 마커 아래 칸의 상자 BlockEntity에 setLootTable(table, random.nextLong()) — 내용물은 생략하고 시드/난수 소비만 재현한다.
     * (아래 칸이 상자일 때만 난수를 소비한다: be instanceof ChestBlockEntity)
     */
    protected static void setChestLoot(PostCtx c, int x, int y, int z, String lootTable) {
        int state = c.view.getBlock(x, y, z);
        String name = c.table().name(state);
        if (!name.equals("minecraft:chest") && !name.equals("minecraft:trapped_chest")) return;
        long seed = c.random.nextLong();
        NbtCompound nbt = c.view.generatedBlockEntity(x, y, z);
        if (nbt == null) {
            nbt = new NbtCompound();
            nbt.putString("id", name);
            nbt.putInt("x", x); nbt.putInt("y", y); nbt.putInt("z", z);
        }
        nbt.putString("LootTable", lootTable);
        nbt.putLong("LootTableSeed", seed);
        c.view.putGeneratedBlockEntity(x, y, z, nbt);
    }
}
