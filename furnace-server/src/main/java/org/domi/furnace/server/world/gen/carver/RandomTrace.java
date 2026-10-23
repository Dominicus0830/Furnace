package org.domi.furnace.server.world.gen.carver;

public final class RandomTrace {
    private static final boolean ENABLED = Boolean.getBoolean("furnace.rngTrace");
    private static volatile int targetCx = Integer.getInteger("furnace.rngTraceCx", Integer.MIN_VALUE);
    private static volatile int targetCz = Integer.getInteger("furnace.rngTraceCz", Integer.MIN_VALUE);
    private static final int TARGET_STEP = Integer.getInteger("furnace.rngTraceStep", Integer.MIN_VALUE);
    private static final int TARGET_IDX = Integer.getInteger("furnace.rngTraceIdx", Integer.MIN_VALUE);
    private static final String TARGET_ID = System.getProperty("furnace.rngTraceId");

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private RandomTrace() {
    }

    /** 측정 도구용: 추적할 decoration origin 청크를 프로그램에서 바꾼다(여러 청크를 한 JVM에서 차례로 덤프). */
    public static void setTarget(int cx, int cz) {
        targetCx = cx;
        targetCz = cz;
    }

    private static volatile int[] targetBox; // {minCx, maxCx, minCz, maxCz} — 설정되면 단일 target 대신 사각형 안의 모든 origin을 추적

    /** 측정 도구용: 사각형 범위의 모든 decoration origin을 추적한다(null이면 해제). */
    public static void setTargetBox(int minCx, int maxCx, int minCz, int maxCz) {
        targetBox = new int[]{minCx, maxCx, minCz, maxCz};
    }

    public static boolean shouldTrace(int cx, int cz, int step, int idx, String id) {
        if (!ENABLED) return false;
        int[] box = targetBox;
        if (box != null) {
            if (cx < box[0] || cx > box[1] || cz < box[2] || cz > box[3]) return false;
            return TARGET_STEP == Integer.MIN_VALUE || TARGET_STEP == step;
        }
        if (targetCx != Integer.MIN_VALUE && targetCx != cx) return false;
        if (targetCz != Integer.MIN_VALUE && targetCz != cz) return false;
        if (TARGET_STEP != Integer.MIN_VALUE && TARGET_STEP != step) return false;
        if (TARGET_IDX != Integer.MIN_VALUE && TARGET_IDX != idx) return false;
        return TARGET_ID == null || TARGET_ID.equals(id);
    }

    public static void begin(long seed, int cx, int cz, int step, int idx, String id, long featureSeed) {
        Session session = new Session();
        CURRENT.set(session);
        System.out.printf(
            "FURNACE_RNG_BEGIN seed=%d cx=%d cz=%d step=%d idx=%d id=%s featureSeed=%d%n",
            seed, cx, cz, step, idx, id, featureSeed);
    }

    public static void end() {
        Session session = CURRENT.get();
        if (session != null) {
            System.out.printf("FURNACE_RNG_END calls=%d%n", session.calls);
        }
        CURRENT.remove();
    }

    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    public static int beginTreePlacement(int x, int y, int z) {
        Session session = CURRENT.get();
        if (session == null) return -1;
        int ordinal = ++session.treePlacements;
        session.currentTreeOrdinal = ordinal;
        System.out.printf(
            "FURNACE_TREE_PLACE origin=(%d,%d,%d) tree=%d%n",
            x, y, z, ordinal);
        return ordinal;
    }

    public static void endTreePlacement() {
        Session session = CURRENT.get();
        if (session != null) session.currentTreeOrdinal = -1;
    }

    public static int currentTreeOrdinal() {
        Session session = CURRENT.get();
        return session == null ? -1 : session.currentTreeOrdinal;
    }

    public static void log(String message) {
        if (!isActive()) return;
        System.out.printf("FURNACE_RNG_CTX %s%n", message);
    }

    public static void nextInt(int value) {
        Session session = CURRENT.get();
        if (session == null) return;
        System.out.printf("FURNACE_RNG call=%d method=nextInt value=%d%n", ++session.calls, value);
    }

    public static void nextInt(int bound, int value) {
        Session session = CURRENT.get();
        if (session == null) return;
        System.out.printf("FURNACE_RNG call=%d method=nextInt bound=%d value=%d%n", ++session.calls, bound, value);
    }

    public static void nextFloat(float value) {
        Session session = CURRENT.get();
        if (session == null) return;
        System.out.printf("FURNACE_RNG call=%d method=nextFloat value=%s%n", ++session.calls, Float.toString(value));
    }

    public static void nextDouble(double value) {
        Session session = CURRENT.get();
        if (session == null) return;
        System.out.printf("FURNACE_RNG call=%d method=nextDouble value=%s%n", ++session.calls, Double.toString(value));
    }

    public static void nextBoolean(boolean value) {
        Session session = CURRENT.get();
        if (session == null) return;
        System.out.printf("FURNACE_RNG call=%d method=nextBoolean value=%s%n", ++session.calls, Boolean.toString(value));
    }

    private static final class Session {
        private int calls;
        private int treePlacements;
        private int currentTreeOrdinal = -1;
    }
}
