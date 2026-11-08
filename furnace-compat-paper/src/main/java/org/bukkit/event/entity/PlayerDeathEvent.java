package org.bukkit.event.entity;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 플레이어 사망. 사망 메시지·인벤토리 유지·레벨 유지·떨어뜨릴 아이템·떨어뜨릴 경험치·리스폰 경험치(newExp·newLevel·newTotalExp)는 Furnace 이벤트에 잇는다
 * (바꾸면 반영된다). 취소(setCancelled(true))는 Furnace 가 죽음을 되돌릴 방법이 없어 UnsupportedFeature 다. 처리기는 EntityDeathEvent 와 같은 목록을 쓴다(Bukkit 과 같다).
 * getDrops() 는 Paper 1.21.1 처럼 keepInventory 와 독립이다: 목록에 남은 아이템은 keepInventory 가 true 여도 떨어지고, keepInventory 가 false 면 인벤토리를 전부 비운다.
 */
public class PlayerDeathEvent extends EntityDeathEvent {

    private String deathMessage;
    private boolean keepInventory;
    private boolean keepLevel;
    private int newExp;
    private int newLevel;
    private int newTotalExp;

    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, String deathMessage) {
        super(player, drops, droppedExp);
        this.deathMessage = deathMessage;
    }

    /** Bukkit 생성자: 새 경험치(리스폰 때 줄 양)까지. */
    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, int newExp, String deathMessage) {
        this(player, drops, droppedExp, newExp, 0, 0, deathMessage);
    }

    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, int newExp, int newTotalExp, int newLevel, String deathMessage) {
        super(player, drops, droppedExp);
        this.deathMessage = deathMessage;
        this.newExp = newExp;
        this.newTotalExp = newTotalExp;
        this.newLevel = newLevel;
    }

    /** 호환 계층 전용: Furnace 사망의 값을 그대로 옮긴 이벤트. */
    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, String deathMessage, boolean keepInventory, boolean keepLevel) {
        super(player, drops, droppedExp);
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

    /** 리스폰 때(keepLevel 이 false 일 때) 줄 경험치. 떨어뜨릴 양은 {@link #getDroppedExp()}. 기본 0. */
    public int getNewExp() { return newExp; }

    public void setNewExp(int exp) { this.newExp = exp; }

    public int getNewLevel() { return newLevel; }

    public void setNewLevel(int level) { this.newLevel = level; }

    public int getNewTotalExp() { return newTotalExp; }

    public void setNewTotalExp(int totalExp) { this.newTotalExp = totalExp; }

    /** Paper 는 취소하면 죽지 않지만 Furnace 는 되돌릴 방법이 없어 취소 요청을 알린다(조용히 무시하지 않는다). */
    @Override
    public void setCancelled(boolean cancel) {
        if (cancel) throw unknown("setCancelled");
        super.setCancelled(false);
    }
}
