package org.domi.furnace.compat.nms;

/**
 * 서버 제공 라이브러리 묶음 datafixerupper(DFU)가 호환 계층에서 보이는가(N2b-6). 호환 계층은 DFU 를 jar 에 싣지 않고 그 묶음을 쓰는 플러그인이 있을 때만
 * 서버가 내려받아 붙이므로, DFU 타입을 만드는 shim 은 먼저 이것으로 확인한다 — 안 보이는 서버에서 클래스 로드가 {@link NoClassDefFoundError} 로 번지지 않게.
 *
 * <p>이 클래스는 DFU 타입을 쓰지 않는다. 보이면 그 뒤로는 바로 참을 준다. 안 보이면 1초에 한 번만 다시 찾는다(묶음은 플러그인을 훑은 뒤 붙으므로 처음에는 없을 수 있다).
 */
public final class NmsDfu {

    private static final String PROBE = "com/mojang/serialization/MapCodec.class";

    private static volatile boolean visible;
    private static volatile boolean broken;
    private static volatile long nextProbe;

    private NmsDfu() {}

    /** DFU 타입을 쓰려다 링크 오류가 났다(예: DFU 의 의존 라이브러리가 안 붙었다) — 이 서버에서는 더 시도하지 않는다. */
    public static void markBroken(LinkageError cause) {
        if (!broken) {
            broken = true;
            visible = false;
            org.slf4j.LoggerFactory.getLogger("Furnace").debug("DataFixerUpper types are not usable from the compat layer: {}", cause.toString());
        }
    }

    public static boolean visible() {
        if (broken) return false;
        if (visible) return true;
        long now = System.nanoTime();
        if (now - nextProbe < 0) return false;
        nextProbe = now + 1_000_000_000L;
        visible = NmsDfu.class.getClassLoader().getResource(PROBE) != null;
        return visible;
    }
}
