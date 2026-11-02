// 서버 게임 코드가 플러그인 이벤트를 부르는 좁은 입구. 기본(NONE)은 아무것도 하지 않고 모두 허용한다.
// 엔티티·Region 코드는 플러그인 구현을 모르고 LevelServices.gameEvents() 로만 접근한다. 호출 지점은 "허가 여부" 한 줄이다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.menu.AbstractContainerMenu;
import org.domi.furnace.server.menu.ClickType;

public interface GameEvents {

    GameEvents NONE = new GameEvents() {};

    /** 피해 직전 — 돌려주는 값이 새 피해량, 음수면 취소. 소유 region 스레드에서. */
    default float entityDamage(LivingEntity e, String cause, float amount) { return amount; }

    /** 플레이어판 entityDamage. */
    default float playerDamage(Player p, String cause, float amount) { return amount; }

    default void entityDeath(LivingEntity e) {}

    /** false 면 넣지 않는다. */
    default boolean entitySpawn(Entity e) { return true; }

    /** false 면 from 으로 되돌린다. */
    default boolean playerMove(Player p, double fx, double fy, double fz, float fyaw, float fpitch,
                               double tx, double ty, double tz, float tyaw, float tpitch) { return true; }

    /** false 면 옮기지 않는다. 호출한 스레드에서. */
    default boolean playerTeleport(Player p, double x, double y, double z) { return true; }

    /** blockPos 가 null 이면 공중 클릭. false 면 취소. */
    default boolean playerInteract(Player p, int hand, int[] blockPos, int blockState, int face, ItemStack inHand) { return true; }

    default boolean playerDrop(Player p, ItemStack stack) { return true; }

    default boolean playerPickup(Player p, ItemStack stack) { return true; }

    default boolean inventoryClick(Player p, AbstractContainerMenu menu, int slot, int button, ClickType type) { return true; }

    default boolean inventoryOpen(Player p, AbstractContainerMenu menu) { return true; }

    default void inventoryClose(Player p, AbstractContainerMenu menu) {}

    default void chunkLoad(String dimension, int cx, int cz) {}

    default void chunkUnload(String dimension, int cx, int cz) {}
}
