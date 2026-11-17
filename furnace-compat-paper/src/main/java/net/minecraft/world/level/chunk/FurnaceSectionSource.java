package net.minecraft.world.level.chunk;

/**
 * 붙은 섹션 컨테이너의 서버 번호 공급원(N2b-4, 호환 모듈 내부) — 청크 shim(그 거울)의 칸 읽기·쓰기로 간다: 주인 스레드는 살아 있는 값·서버에 바로,
 * 그 밖은 공개 스냅샷 + 자기 쓰기 덧칠·거울 칸 일지.
 */
final class FurnaceSectionSource implements PalettedContainer.FurnaceSource {

    final ChunkAccess chunk;
    final int index;
    /** 바이옴 컨테이너(4×4×4)면 true, 블록 컨테이너(16³)면 false. */
    final boolean biome;

    FurnaceSectionSource(ChunkAccess chunk, int index, boolean biome) {
        this.chunk = chunk;
        this.index = index;
        this.biome = biome;
    }

    @Override
    public int idAt(int cell) { return biome ? chunk.furnaceSectionBiomes(index)[cell] : chunk.furnaceSectionCell(index, cell); }

    @Override
    public int[] ids() { return biome ? chunk.furnaceSectionBiomes(index) : chunk.furnaceSectionStates(index); }

    @Override
    public int setId(PalettedContainer<?> self, int cell, int id) {
        return biome ? chunk.furnaceSetSectionBiome(self, this, index, cell, id) : chunk.furnaceContainerSet(self, this, index, cell, id);
    }

    @Override
    public FurnaceChunkMirror mirror() { return chunk.furnaceMirror; }

    @Override
    public Object contentStamped(long[] stampOut, long known) { return chunk.furnaceSectionContentStamped(index, biome, stampOut, known); }

    @Override
    public Object lock() { return chunk.furnaceMirror.lock; }

    @Override
    public void fieldWritten() { chunk.furnaceMirror.noteWrite(); }
}
