// 특정 region에 속하지 않는 전역 작업 스케줄러
package org.domi.furnace.api.sched;

public interface GlobalScheduler extends TaskScheduler {

    /**
     * 전역 스레드에서: region 빌림 구간을 연다(try-with-resources 로 닫는다). 구간 안에서 region 이 가진 상태(블록·엔티티)를
     * 소유 스레드 전용 동기 API(Block#data·Block#set·World#blockNow·spawnEntityNow·nearbyEntitiesNow·Internals#entityNow 등)로
     * 만지면 ThreadOwnershipException 대신, 그 region 이 지금 도는 틱을 마치기를 기다렸다가 구간이 닫힐 때까지 빌려 온다.
     * 빌린 region 은 그 동안 틱을 쉬고 이 스레드가 그 소유 스레드가 된다. 만지지 않은 region 은 그대로 병렬로 돈다.
     * <p>
     * 옛 "주 스레드 하나가 모든 월드를 만진다" 코드(Bukkit 동기 작업·명령)를 위한 것이다 — 새 코드는 region 스케줄러를 쓴다.
     * 겹쳐 열어도 되고 가장 바깥 구간이 닫힐 때 모두 돌려준다. 전역 스레드가 아니면 아무것도 빌리지 않는 빈 구간이다.
     * 빌리러 기다리는 시간이 한도({@code furnace.regionBorrowWaitMillis}, 기본 5000)를 넘으면 예전처럼 ThreadOwnershipException.
     */
    default RegionAccess openRegionAccess() { return RegionAccess.NONE; }

    /** {@link #openRegionAccess()} 의 닫는 손잡이. 연 스레드에서 닫는다. */
    @FunctionalInterface
    interface RegionAccess extends AutoCloseable {
        RegionAccess NONE = () -> {};

        @Override
        void close();
    }
}
