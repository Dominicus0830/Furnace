// 가상 엔티티 — 서버 월드에는 없고, 가까이 있는 플레이어 화면에만 패킷으로 나타나는 엔티티(홀로그램·표지판 대용·NPC 바탕).
// 서버 상태(충돌·AI·저장·엔티티 수 제한)에 아무 영향이 없다. 번호는 서버 엔티티와 겹치지 않게 예약된다.
//
// 스레드: 모든 메서드는 어느 스레드에서 불러도 된다(바뀐 값은 보고 있는 플레이어에게 바로 패킷으로 간다).
// 소유: 만든 플러그인이 꺼지면 이 서비스가 자동으로 remove() 한다.
package org.domi.furnace.api.virtual;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;

public interface VirtualEntity {

    /** 클라이언트가 아는 엔티티 번호(interact 패킷에도 이 번호가 온다). */
    int entityId();

    UUID uuid();

    /** "minecraft:villager" 같은 이름. 가짜 플레이어는 "minecraft:player". */
    String entityType();

    FurnacePlugin owner();

    boolean isRemoved();

    Location location();

    /** 옮긴다. 다른 월드로 옮기면 보던 플레이어에게서 사라지고 그 월드의 가까운 플레이어에게 나타난다. */
    void teleport(Location to);

    void setRotation(float yaw, float pitch);

    void setHeadYaw(float yaw);

    // ------------------------------------------------------------------ 보이는 범위

    /** 이 거리(블록) 안의 같은 월드 플레이어에게 보인다. 기본 48. */
    double trackingRange();

    void setTrackingRange(double blocks);

    /** 기본 true. false 면 {@link #show} 한 플레이어에게만 보인다. */
    boolean isVisibleByDefault();

    void setVisibleByDefault(boolean visible);

    /** 이 플레이어에게 (범위 안이면) 보이게 한다 — 기본값과 상관없이. */
    void show(Player player);

    /** 이 플레이어에게는 보이지 않게 한다 — 기본값과 상관없이. */
    void hide(Player player);

    /** show/hide 지정을 풀고 기본값을 따르게 한다. */
    void resetVisibility(Player player);

    /** 지금 이 엔티티를 화면에 가진 플레이어. */
    Collection<Player> viewers();

    // ------------------------------------------------------------------ 겉모습

    /**
     * 이름 있는 메타데이터 칸을 정한다(EntityDataKeys). 이미 보는 플레이어에게 바로 보낸다.
     * 이 엔티티 종류에 없는 칸이면 IllegalArgumentException(예: 좀비에 TextDisplay.TEXT) — 클라이언트가 끊기는 일을 미리 막는다.
     * 예) textDisplay.set(EntityDataKeys.TextDisplay.TEXT, Text.text("상점")); itemDisplay.set(EntityDataKeys.Display.SCALE, new Vector3f(2, 2, 2));
     */
    <T> void set(org.domi.furnace.api.entity.data.EntityDataKey<T> key, T value);

    /** 칸 번호를 직접 정한다(바닐라 엔티티 메타데이터 칸 — 번호는 마인크래프트 버전마다 다르다, set(EntityDataKey, …) 를 먼저 쓴다). 이미 보는 플레이어에게 바로 보낸다. */
    void setMetadata(int index, ClientboundSetEntityDataPacket.Value value);

    /** 커스텀 이름(null 이면 지움). 이름표를 보이려면 {@link #setCustomNameVisible}. */
    void setCustomName(Text name);

    void setCustomNameVisible(boolean visible);

    void setInvisible(boolean invisible);

    void setGlowing(boolean glowing);

    void setSneaking(boolean sneaking);

    void setOnFire(boolean onFire);

    void setSilent(boolean silent);

    void setNoGravity(boolean noGravity);

    /** 장비 칸을 정한다(빈 아이템이면 비움). 몹·가짜 플레이어에만 보인다. */
    void setEquipment(EquipmentSlot slot, ItemStack item);

    /** 팔 휘두르기 같은 일회성 동작을 보고 있는 플레이어에게 보낸다. */
    void swingMainHand();

    // ------------------------------------------------------------------ 클릭

    /**
     * 플레이어가 이 엔티티를 우클릭·좌클릭하면 불린다(서버에 없는 번호여도 클라이언트는 패킷을 보낸다).
     * 우클릭은 주 손만 한 번, 공격은 한 번 온다. 처리기는 그 플레이어의 스케줄러 스레드에서 불린다.
     * 하나만 둘 수 있다(다시 부르면 바뀐다, null 이면 뗀다).
     */
    void onInteract(Consumer<VirtualInteraction> handler);

    /** 지운다. 보던 플레이어 화면에서도 사라진다. 두 번 불러도 된다. */
    void remove();
}
