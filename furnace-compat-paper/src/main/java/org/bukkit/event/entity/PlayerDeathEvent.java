package org.bukkit.event.entity;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어 사망. 사망 메시지·인벤토리 유지·레벨 유지는 Furnace 이벤트에 잇는다(바꾸면 반영된다).
 * 드롭 목록·경험치·새 레벨·취소는 Furnace 가 알리지 않아 UnsupportedFeature 다. 처리기는 EntityDeathEvent 와 같은 목록을 쓴다(Bukkit 과 같다).
 */
public class PlayerDeathEvent extends EntityDeathEvent {

    private String deathMessage;
    private boolean keepInventory;
    private boolean keepLevel;

    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, String deathMessage) {
        super(player, drops, droppedExp);
        this.deathMessage = deathMessage;
    }

    /** 호환 계층 전용: 드롭·경험치를 모르는 Furnace 사망. */
    public PlayerDeathEvent(Player player, String deathMessage, boolean keepInventory, boolean keepLevel) {
        super(player, null, 0);
        this.deathMessage = deathMessage;
        this.keepInventory = keepInventory;
        this.keepLevel = keepLevel;
    }

    @Override
    public Player getEntity() { return (Player) entity; }

    /** Paper. */
    public Player getPlayer() { return getEntity(); }

    /** § 서식 문자열, null 이면 알리지 않는다. */
    public String getDeathMessage() { return deathMessage; }

    public void setDeathMessage(String deathMessage) { this.deathMessage = deathMessage; }

    public boolean getKeepInventory() { return keepInventory; }

    public void setKeepInventory(boolean keepInventory) { this.keepInventory = keepInventory; }

    public boolean getKeepLevel() { return keepLevel; }

    public void setKeepLevel(boolean keepLevel) { this.keepLevel = keepLevel; }

    public int getNewExp() { throw unknown("getNewExp"); }

    public void setNewExp(int exp) { throw unknown("setNewExp"); }

    public int getNewLevel() { throw unknown("getNewLevel"); }

    public void setNewLevel(int level) { throw unknown("setNewLevel"); }

    public int getNewTotalExp() { throw unknown("getNewTotalExp"); }

    public void setNewTotalExp(int totalExp) { throw unknown("setNewTotalExp"); }
}
