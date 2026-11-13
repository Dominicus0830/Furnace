// 월드의 엔티티 핸들. 이벤트가 만든 핸들의 location()/customName()/isValid() 는 "만든 시점" 값이다(낡을 수 있다).
// 최신 값이 필요하면 refresh(), 상태를 바꾸는 호출은 모두 소유 region 스레드에서 실행되고 Future 로 끝을 알린다.
package org.domi.furnace.api.entity;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.api.world.World;

public interface Entity {

    UUID uniqueId();

    /**
     * 네트워크 엔티티 id(패킷이 엔티티를 가리키는 번호, Bukkit/Paper 의 getEntityId). 핸들을 만든 뒤에도 바뀌지 않으며
     * 서버가 살아 있는 동안 서버 전체(모든 차원)에서 다른 엔티티와 겹치지 않는다. 플레이어도 같다.
     */
    int entityId();

    /** 종류 키(minecraft:zombie, minecraft:player). 불변. */
    String type();

    /** 핸들을 만든 시점의 위치. yaw 는 몸 방향이다(살아 있는 몹은 머리와 다를 수 있다 — {@link #headYaw()}). */
    Location location();

    /**
     * 핸들을 만든 시점의 머리 방향(yaw, 도). 살아 있는 몹은 시선을 따로 돌려 몸 방향({@code location().yaw()})과 다를 수 있고,
     * 그 밖의 엔티티·플레이어는 몸 방향과 같다. Bukkit/Paper 의 {@code getLocation().getYaw()} 가 살아 있는 엔티티에서 이 값이다.
     */
    default float headYaw() { return location().yaw(); }

    /** 핸들을 만든 시점의 사용자 지정 이름(없으면 null). */
    Text customName();

    /** 핸들을 만든 시점에 살아 있었는가. */
    boolean isValid();

    default World world() { return Furnace.server().world(location().world()); }

    /** 핸들 시점 위치를 가진 region 의 스케줄러. 엔티티가 그 region 을 벗어났으면 작업은 그 자리의 region 에서 돈다. 엔티티를 따라가려면 entityScheduler(). */
    TaskScheduler scheduler();

    /** 엔티티를 따라가는 스케줄러 — 작업은 늘 그 엔티티를 가진 region 스레드에서 돌고, 엔티티가 사라지면 retired 가 불린다. */
    EntityScheduler entityScheduler();

    /** 영속 데이터(엔티티 NBT 의 BukkitValues)의 핸들 시점 사본. 플레이어는 최근 값. */
    PersistentDataView persistentData();

    /**
     * 소유 스레드에서 영속 데이터를 고친다(바뀐 값은 엔티티와 함께 저장된다). 엔티티가 사라졌거나 다른 region 으로 넘어갔으면 false.
     * 콜백이 받은 컨테이너를 밖으로 들고 나가 나중에 쓰지 않는다.
     */
    CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit);

    /**
     * editPersistentData 의 동기판 — 그 엔티티를 가진 region 스레드에서만(아니면 ThreadOwnershipException) 콜백을 곧바로 돌리고,
     * 엔티티가 사라졌으면 콜백 없이 false. 플레이어는 어느 스레드에서나 된다(서버가 영속 데이터를 잠가 둔다).
     * 읽을 때도 이 메서드의 콜백 안에서 읽으면 핸들 시점 사본이 아니라 지금 값이다.
     */
    default boolean editPersistentDataNow(Consumer<PersistentDataContainer> edit) { throw new UnsupportedOperationException("editPersistentDataNow"); }

    /**
     * 엔티티가 가진 칸(상자 배·상자/깔때기 수레, 주민·알레이·피글린의 소지품, 플레이어 인벤토리). 칸이 없는 엔티티는 empty.
     * 돌려준 Inventory 의 읽기·쓰기는 그 엔티티를 가진 region 스레드에서 실행된다(엔티티가 사라졌으면 IllegalStateException 으로 완료).
     */
    CompletableFuture<Optional<Inventory>> container();

    /** 소유 region 에서 최신 상태를 읽은 새 핸들. 엔티티가 그 region 을 떠났거나 사라졌으면 null 로 완료된다. */
    CompletableFuture<? extends Entity> refresh();

    /**
     * 같은 차원 안에서만 옮긴다(소유 region 에서 실행). 옮겼으면 true. 사라졌거나, 승객이 타고 있거나, 다른 차원·다른 region 으로의
     * 이동이거나, 플레이어의 PlayerTeleportEvent 가 취소됐으면 false. 탈것을 타고 있으면 먼저 내린다(Paper 의 Entity#teleport 와 같다).
     */
    CompletableFuture<Boolean> teleport(Location to);

    /**
     * {@link #teleport} 의 동기판 — 엔티티를 가진 region 스레드에서 곧바로 옮기고 진짜 결과를 돌려준다(Bukkit/Paper 의 Entity#teleport 가
     * 주 스레드에서 즉시 결과를 주는 것과 같다). 소유 스레드가 아니면 ThreadOwnershipException. 플레이어는 연결이 보정 텔레포트를 보내는
     * 스레드 안전 경로라 어느 스레드에서나 부를 수 있고, 탈것에서 내리는 일만 소유 스레드로 넘어간다.
     */
    default boolean teleportNow(Location to) { throw new UnsupportedOperationException("teleportNow"); }

    /** 엔티티를 지운다(플레이어는 UnsupportedOperationException 으로 완료). 이미 없으면 false. */
    CompletableFuture<Boolean> remove();

    // ------------------------------------------------------------------ NBT (/data entity 와 같은 의미 — 내부 객체 없이 대부분의 상태에 닿는다)

    /**
     * 엔티티의 저장 NBT 사본(/data get entity 와 같은 내용 — 플레이어도 읽힌다). 엔티티를 가진 region 스레드에서 읽는다.
     * 엔티티가 사라졌으면 IllegalStateException 으로 완료. 키 이름·구조는 마인크래프트 데이터 버전을 따른다.
     */
    CompletableFuture<NbtCompound> readNbt();

    /**
     * 엔티티를 가진 region 스레드에서 NBT 를 읽어 edit 로 고치고, 바뀌었으면 엔티티에 다시 싣는다(/data modify·merge entity 와 같은 경로:
     * 바닐라 Entity.load, UUID 는 바뀌지 않는다). 바뀌었으면 true, 그대로면 false(싣지 않는다).
     * 플레이어는 바닐라처럼 쓸 수 없다(UnsupportedOperationException 으로 완료). 엔티티가 사라졌으면 IllegalStateException.
     * edit 는 그 region 스레드에서 돈다 — 받은 묶음을 밖으로 들고 나가지 않는다.
     */
    CompletableFuture<Boolean> editNbt(Consumer<NbtCompound> edit);

    /** patch 를 합친다(/data merge entity, 묶음은 안쪽으로 합치고 리스트는 통째로 바뀐다). 바뀌었으면 true. */
    default CompletableFuture<Boolean> mergeNbt(NbtCompound patch) {
        NbtCompound p = patch.copy();
        return editNbt(n -> n.merge(p));
    }

    // ------------------------------------------------------------------ 자세·물리

    /**
     * 핸들을 만든 시점의 자세. 플레이어는 최근 값. 자세를 스스로 바꾸는 엔티티(워든·개구리·브리즈·플레이어)는 곧 다른 값이 될 수 있다.
     */
    default Pose pose() { return Pose.STANDING; }

    /**
     * 자세를 바꾼다(소유 region 스레드에서 — 데이터 동기화 값이 바뀌어 보는 플레이어 화면에 반영된다).
     * 몹은 AI 가 자기 자세를 다시 정하면 덮인다(바닐라와 같다). 플레이어는 pose 로 자세를 고정해 다른 플레이어가 보는 모습만 바뀐다
     * (판정 상자·자기 화면은 그대로) — null 로 고정을 푼다. 몹에 null 이면 서 있는 자세로 돌린다. 엔티티가 사라졌으면 false.
     */
    default CompletableFuture<Boolean> setPose(Pose pose) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("setPose"));
    }

    /**
     * 핸들을 만든 시점의 충돌 상자(바닐라 Entity.getBoundingBox — 월드 좌표). 플레이어는 지금 값(위치·자세로 계산한다).
     * 지금 값을 소유 스레드에서 읽으려면 {@link EntityState#boundingBox()}.
     */
    default org.domi.furnace.api.world.Aabb boundingBox() { throw new UnsupportedOperationException("boundingBox"); }

    /** 핸들을 만든 시점의 noPhysics(충돌·블록 막힘 없이 움직이는가). */
    default boolean noPhysics() { return false; }

    /**
     * noPhysics 를 바꾼다(바닐라 Entity.noPhysics — 블록·엔티티와 부딪히지 않고 속도대로 움직인다. 중력은 그대로라 바닥을 뚫고 떨어진다).
     * 저장되지 않는다. 아이템처럼 틱마다 스스로 정하는 엔티티는 다음 틱에 덮인다. 플레이어는 UnsupportedOperationException 으로 완료.
     * 엔티티가 사라졌으면 false.
     */
    default CompletableFuture<Boolean> setNoPhysics(boolean noPhysics) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("setNoPhysics"));
    }

    // ------------------------------------------------------------------ 지금 상태(이름·플래그·탑승·효과)

    /**
     * 지금 이 스레드가 그 엔티티를 가진 region 스레드면(그 엔티티 이벤트 처리기 안·entityScheduler 작업 안·옛 동기 작업의 빌림 구간) 곧바로
     * 상태 손잡이. 아니면 ThreadOwnershipException. 엔티티가 사라졌으면 빈 값. 살아 있는 엔티티는 {@link LivingEntityState},
     * 갑옷 거치대는 {@link ArmorStandState} 다. 플레이어는 아직 없다(UnsupportedOperationException).
     */
    default Optional<EntityState> stateNow() { throw new UnsupportedOperationException("stateNow"); }

    /**
     * 지금 이 스레드가 그 엔티티를 가진 region 스레드(빌림 구간 포함)면 true — {@link #stateNow()} 가 예외 없이 돌려줄 수 있는가.
     * 사라진 엔티티는 어느 스레드에서나 true(stateNow 가 빈 값). 상태 손잡이가 없는 엔티티(플레이어)는 false. 예외를 만들지 않아 자주 불러도 싸다.
     */
    default boolean isOwnedByCurrentThread() { return false; }

    /**
     * 그 엔티티를 가진 region 스레드에서 다음 틱에 action 을 돈다(엔티티가 region 을 옮겨 가도 따라간다). action 의 결과로 완료하고,
     * 엔티티가 이미 사라졌거나 그 전에 사라지면 IllegalStateException 으로 완료. 손잡이를 밖으로 들고 나가 다른 스레드에서 쓰지 않는다.
     */
    default <T> CompletableFuture<T> editState(java.util.function.Function<? super EntityState, ? extends T> action) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("editState"));
    }

    // ------------------------------------------------------------------ 원인 사슬

    /**
     * 이 엔티티를 만들었거나 켠 원인(핸들을 만든 순간 값) — 점화된 TNT 면 켠 플레이어·레드스톤 칸·앞선 폭발, 투사체면 쏜 쪽.
     * 엔티티 자신은 들어 있지 않다. 모르면 {@link org.domi.furnace.api.event.cause.CauseChain#NONE}. 저장되지 않는다(다시 읽은 엔티티는 바닐라 주인만).
     */
    default org.domi.furnace.api.event.cause.CauseChain origin() { return org.domi.furnace.api.event.cause.CauseChain.NONE; }

    /** 떨어진 아이템 엔티티(minecraft:item)가 든 아이템(만든 순간 값). 아이템 엔티티가 아니면 null. */
    default org.domi.furnace.api.item.ItemStack droppedItem() { return null; }

    /** 떨어진 아이템 엔티티의 줍기 지연(틱, 만든 순간 값). 아이템 엔티티가 아니면 0. */
    default int pickupDelay() { return 0; }

    /**
     * 핸들(스냅샷)을 만든 순번 — 같은 엔티티의 두 핸들 중 값이 큰 쪽이 나중에 만든 것이다. 서버 전체에서 늘어나기만 한다.
     * 0 이면 모른다(살아 있는 핸들이거나 서버가 순번을 매기지 않는 구현). 호환 계층이 늦게 온 옛 스냅샷으로 새 값을 덮지 않게 쓴다.
     */
    default long snapshotStamp() { return 0L; }
}
