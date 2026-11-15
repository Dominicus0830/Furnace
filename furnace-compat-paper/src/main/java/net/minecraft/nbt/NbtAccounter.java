package net.minecraft.nbt;

/**
 * NBT 읽기 한도(Mojang 이름, 바닐라 1.21.1 모양 — 쓰는 멤버만). ProtocolLib StructureCache 가 빈 자료용 직렬화기(0 버퍼)를 만들 때
 * {@code getNBTReadLimiterClass()} 로 이 클래스를 이름으로 찾는다 — 없으면 그 초기화가 조용히 실패해 {@code LightData.fromValues}·
 * {@code createPacket} 기본값 채우기가 NullPointerException 이 된다(PR #399 검토 R1). Furnace 의 패킷 NBT 읽기는 compat NbtWire 가 하고
 * 깊이 512 를 지킨다 — 이 객체의 바이트 한도는 세지 않는다(가정: 플러그인이 직접 accountBytes 를 부르는 일은 드물다).
 */
public class NbtAccounter {

    private static final int MAX_STACK_DEPTH = 512;

    private final long quota;      // 바닐라 필드 모양(읽는 곳 없음)
    private final int maxDepth;

    public NbtAccounter(long quota, int maxDepth) {
        this.quota = quota;
        this.maxDepth = maxDepth;
    }

    /** 바닐라: 바이트 한도 quota, 깊이 512. */
    public static NbtAccounter create(long quota) { return new NbtAccounter(quota, MAX_STACK_DEPTH); }

    /** 바닐라: 바이트 한도 없음, 깊이 512. */
    public static NbtAccounter unlimitedHeap() { return new NbtAccounter(Long.MAX_VALUE, MAX_STACK_DEPTH); }
}
