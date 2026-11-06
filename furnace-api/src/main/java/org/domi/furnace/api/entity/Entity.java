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

    /** 종류 키(minecraft:zombie, minecraft:player). 불변. */
    String type();

    /** 핸들을 만든 시점의 위치. */
    Location location();

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
     * 엔티티가 가진 칸(상자 배·상자/깔때기 수레, 주민·알레이·피글린의 소지품, 플레이어 인벤토리). 칸이 없는 엔티티는 empty.
     * 돌려준 Inventory 의 읽기·쓰기는 그 엔티티를 가진 region 스레드에서 실행된다(엔티티가 사라졌으면 IllegalStateException 으로 완료).
     */
    CompletableFuture<Optional<Inventory>> container();

    /** 소유 region 에서 최신 상태를 읽은 새 핸들. 엔티티가 그 region 을 떠났거나 사라졌으면 null 로 완료된다. */
    CompletableFuture<? extends Entity> refresh();

    /** 같은 차원 안에서만 옮긴다(소유 region 에서 실행). 옮겼으면 true, 사라졌거나 다른 차원이면 false. */
    CompletableFuture<Boolean> teleport(Location to);

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
}
