// 바닐라 StructurePlaceSettings — 템플릿 배치 옵션(미러·회전·피벗·쓰기 상자·프로세서 …).
package org.domi.furnace.server.world.gen.structure.template;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class PlaceSettings {
    public int mirror = BlockStateTable.NONE;
    public int rotation = BlockStateTable.ROT_NONE;
    public int pivotX, pivotY, pivotZ;
    public boolean ignoreEntities;
    /** 쓰기 허용 상자 {minX,minY,minZ,maxX,maxY,maxZ}, null이면 제한 없음. */
    public int[] boundingBox;
    /** LiquidSettings.APPLY_WATERLOGGING(기본) 이면 true. */
    public boolean keepLiquids = true;
    public boolean knownShape;
    public boolean finalizeEntities;
    /** 설정되면 모든 getRandom(pos)가 이 난수를 돌려준다. */
    public LegacyRandom random;
    public final List<Processor> processors = new ArrayList<>();

    public PlaceSettings copy() {
        PlaceSettings c = new PlaceSettings();
        c.mirror = mirror;
        c.rotation = rotation;
        c.pivotX = pivotX; c.pivotY = pivotY; c.pivotZ = pivotZ;
        c.ignoreEntities = ignoreEntities;
        c.boundingBox = boundingBox == null ? null : boundingBox.clone();
        c.keepLiquids = keepLiquids;
        c.knownShape = knownShape;
        c.finalizeEntities = finalizeEntities;
        c.random = random;
        c.processors.addAll(processors);
        return c;
    }

    /** 바닐라 getRandom(pos): 고정 난수가 있으면 그것, 없으면 pos 시드의 LegacyRandomSource. */
    public LegacyRandom getRandom(int x, int y, int z) {
        return random != null ? random : new LegacyRandom(getSeed(x, y, z));
    }

    /** 바닐라 Mth.getSeed(x, y, z). */
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public boolean inBox(int x, int y, int z) {
        return boundingBox == null || (x >= boundingBox[0] && x <= boundingBox[3] && y >= boundingBox[1] && y <= boundingBox[4]
            && z >= boundingBox[2] && z <= boundingBox[5]);
    }
}
