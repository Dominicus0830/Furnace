// 청크 한 열의 POI 섹션들(바닐라 SectionStorage 의 청크 단위 묶음). poi/ 지역 파일 형식 {DataVersion, Sections: {"<섹션 y>": PoiSection}}.
// 청크와 함께 region 사이를 옮겨 다니므로 region 합치기·나누기 때 따로 옮길 것이 없다.
package org.domi.furnace.server.world.poi;

import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class PoiChunk {
    private final int minSection;
    private final PoiSection[] sections;
    private boolean dirty;
    /** null 이 아닌 섹션 수(섹션은 한 번 생기면 지우지 않는다) — 0 이면 마을 거리 훑기가 이 열을 건너뛴다. */
    private int present;
    private final Runnable setDirty = () -> dirty = true;

    public PoiChunk(int minSection, int sectionCount) {
        this.minSection = minSection;
        this.sections = new PoiSection[sectionCount];
    }

    public int minSection() { return minSection; }

    public int maxSection() { return minSection + sections.length - 1; }

    /** getOrLoad: 없으면 null. */
    public PoiSection section(int sectionY) {
        int i = sectionY - minSection;
        return i >= 0 && i < sections.length ? sections[i] : null;
    }

    /** getOrCreate. 범위 밖이면 null. */
    PoiSection getOrCreate(int sectionY) {
        int i = sectionY - minSection;
        if (i < 0 || i >= sections.length) return null;
        PoiSection s = sections[i];
        if (s == null) {
            sections[i] = s = new PoiSection(setDirty);
            present++;
            dirty = true;
        }
        return s;
    }

    public boolean dirty() { return dirty; }

    /** 섹션이 하나라도 있는가. */
    public boolean hasSections() { return present > 0; }

    public void clearDirty() { dirty = false; }

    /** 저장 NBT(바닐라 SectionStorage.writeColumn). 섹션이 하나도 없으면 null. */
    public NbtCompound save(int dataVersion) {
        NbtCompound secs = new NbtCompound();
        boolean any = false;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) continue;
            secs.putCompound(Integer.toString(minSection + i), sections[i].save());
            any = true;
        }
        if (!any) return null;
        NbtCompound root = new NbtCompound();
        root.putCompound("Sections", secs);
        root.putInt("DataVersion", dataVersion);
        return root;
    }

    /** 저장 NBT 를 읽는다(readColumn). 범위 밖 섹션·깨진 키는 버린다. */
    public static PoiChunk load(NbtCompound root, int minSection, int sectionCount) {
        PoiChunk c = new PoiChunk(minSection, sectionCount);
        if (root != null && root.tags().get("Sections") instanceof NbtCompound secs) {
            for (var e : secs.tags().entrySet()) {
                int y;
                try {
                    y = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                int i = y - minSection;
                if (i < 0 || i >= sectionCount || !(e.getValue() instanceof NbtCompound sc)) continue;
                if (c.sections[i] == null) c.present++;
                c.sections[i] = PoiSection.load(sc, c.setDirty);
            }
        }
        return c;
    }
}
