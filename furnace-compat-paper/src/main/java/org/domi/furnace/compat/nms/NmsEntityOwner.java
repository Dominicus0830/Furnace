package org.domi.furnace.compat.nms;

/**
 * 엔티티 shim 의 소유 규칙 클래스(T-232, 연결 명세 {@code owner=}). 생성 몸통·손 몸통의 첫 줄에서 불린다 — "검사가 위임보다 먼저".
 * 규칙은 {@link NmsEntities} 와 같다: 엔티티를 가진 스레드면 통과, 틱 스레드가 아닌 스레드(FAWE 작업 스레드)는 통과(몸통이 주인 region 에 맡겨 기다린다),
 * 다른 region·전역 스레드는 교착을 피해 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}. 아직 월드에 없는 엔티티와 플레이어는 통과
 * (앞은 주인이 없고, 뒤는 접속 처리기를 거치는 값이라 몸통이 스스로 가린다).
 */
public final class NmsEntityOwner {

    private NmsEntityOwner() {}

    public static void read(Object self, String what) { check(self, what); }

    public static void write(Object self, String what) { check(self, what); }

    private static void check(Object self, String what) {
        if (!(self instanceof net.minecraft.world.entity.Entity e)) throw new IllegalArgumentException(what + ": not an entity shim: " + self);
        org.domi.furnace.api.entity.Entity h = e.furnaceHandleOrNull();
        if (h == null || h instanceof org.domi.furnace.api.entity.Player || h.isOwnedByCurrentThread()) return;
        if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) {
            throw new org.domi.furnace.api.concurrent.ThreadOwnershipException(what + ": entity " + h.uniqueId() + " belongs to another region - a region or"
                    + " global thread cannot wait for another region (deadlock); call it on its owning region (current thread: " + Thread.currentThread().getName() + ")");
        }
    }
}
