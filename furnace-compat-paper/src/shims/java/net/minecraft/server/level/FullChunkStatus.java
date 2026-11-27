package net.minecraft.server.level;

/**
 * 바닐라 FullChunkStatus(청크 홀더 단계, Mojang 이름 shim — T-204). 순서가 바닐라와 같다: 티켓 레벨 33 FULL, 32 BLOCK_TICKING, 31 이하 ENTITY_TICKING, 그 밖 INACCESSIBLE.
 */
enum FullChunkStatus {
    /** 바닐라: 이 단계가 status 와 같거나 더 높은가. */
    public boolean isOrAfter(FullChunkStatus status) { return ordinal() >= status.ordinal(); }

    /** 호환 모듈 전용 — 다리의 번호(0~3, 로드돼 있지 않으면 -1 = INACCESSIBLE). */
    public static FullChunkStatus furnaceOf(int id) { return id <= 0 ? INACCESSIBLE : values()[Math.min(id, 3)]; }
}
