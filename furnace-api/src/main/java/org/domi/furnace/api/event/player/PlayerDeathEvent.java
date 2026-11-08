// 플레이어 사망 — 사망 메시지(null 이면 알리지 않음)·떨어뜨릴 아이템·떨어뜨릴 경험치·인벤토리/경험치 유지 여부를 바꿀 수 있다.
// 피해를 준 쪽의 스레드(소유 region·명령 스레드)에서 불린다.
//
// 떨어뜨릴 아이템({@link #drops()})은 지금 인벤토리에서 모은 목록(소멸 저주 아이템은 빠진다)이고 바꾸면 실제 드롭에 반영된다.
// keepInventory 와 drops 는 서로 독립이다: 목록에 남은 아이템은 keepInventory 가 true 여도 떨어지고(인벤토리에도 남는다),
// keepInventory 가 false 면 목록과 무관하게 죽을 때 인벤토리를 전부 비운다. 아이템을 지키려면 keepInventory(true) 와 drops().clear() 를 함께 쓴다.
// keepInventory 가 false 일 때 일부만 남기려면 그 아이템을 drops() 에서 빼고 itemsToKeep() 에 넣는다(Paper getItemsToKeep).
//
// 취소하면 죽지 않는다(Paper 와 같다): 체력이 0 이하면 reviveHealth 로 되돌리고, 사망 메시지·드롭·경험치·사망 화면·통계·리스폰이 모두 없다.
package org.domi.furnace.api.event.player;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.text.Text;

public final class PlayerDeathEvent extends PlayerEvent implements Cancellable {

    private Text deathMessage;
    private boolean keepInventory;
    private boolean keepLevel;
    private final List<ItemStack> drops;
    private int droppedExp;
    private int newExp;
    private int newLevel;
    private int newTotalExp;
    private final List<ItemStack> itemsToKeep = new ArrayList<>();
    private boolean shouldDropExperience = true;
    private final float maxHealth;
    private float reviveHealth;
    private boolean cancelled;

    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory) {
        this(player, deathMessage, keepInventory, List.of(), 0);
    }

    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory, List<ItemStack> drops, int droppedExp) {
        this(player, deathMessage, keepInventory, drops, droppedExp, 20.0f);
    }

    /** @param maxHealth 죽는 순간 최대 체력 — 되살릴 체력의 기본값이자 상한. */
    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory, List<ItemStack> drops, int droppedExp, float maxHealth) {
        super(player);
        this.deathMessage = deathMessage;
        this.keepInventory = keepInventory;
        this.keepLevel = keepInventory;
        this.drops = new ArrayList<>(drops);
        this.droppedExp = droppedExp;
        this.maxHealth = maxHealth;
        this.reviveHealth = maxHealth;
    }

    public Text deathMessage() { return deathMessage; }

    public void deathMessage(Text message) { this.deathMessage = message; }

    public boolean keepInventory() { return keepInventory; }

    public void keepInventory(boolean v) { this.keepInventory = v; }

    /** 리스폰 때 경험치를 유지(기본은 keepInventory 게임룰과 같다). */
    public boolean keepLevel() { return keepLevel; }

    public void keepLevel(boolean v) { this.keepLevel = v; }

    /**
     * 죽은 자리에 떨어뜨릴 아이템 — 바꿀 수 있는 목록(넣고 빼고 비울 수 있다). 목록 순서대로 던진다.
     * 빈 스택은 건너뛴다. 이벤트가 끝난 뒤 목록을 바꿔도 소용없다.
     */
    public List<ItemStack> drops() { return drops; }

    /** 죽은 자리에 떨어뜨릴 경험치(구슬로 나뉜다). 기본은 keepInventory 게임룰이 꺼져 있을 때 레벨×7(최대 100), 관전자는 0. */
    public int droppedExp() { return droppedExp; }

    public void droppedExp(int amount) { this.droppedExp = amount; }

    /** 리스폰 때 keepLevel 이 false 일 때 새로 줄 경험치량(레벨·총량을 정한 뒤 더한다). 기본 0. */
    public int newExp() { return newExp; }

    public void newExp(int exp) { this.newExp = exp; }

    /** 리스폰 때 keepLevel 이 false 일 때의 시작 레벨. 기본 0. */
    public int newLevel() { return newLevel; }

    public void newLevel(int level) { this.newLevel = level; }

    /** 리스폰 때 keepLevel 이 false 일 때의 시작 총 경험치(점수 표시용 총량). 기본 0. */
    public int newTotalExp() { return newTotalExp; }

    public void newTotalExp(int total) { this.newTotalExp = total; }

    /**
     * 죽을 때 인벤토리에 남길 아이템(Paper getItemsToKeep) — 바꿀 수 있는 목록, 기본은 비어 있다. keepInventory 가 false 일 때만 본다.
     * 인벤토리를 비울 때 칸마다(주 인벤토리 → 갑옷 → 왼손) 이 목록에 같은 아이템(종류·개수·컴포넌트가 모두 같은 것)이 있으면 그 칸을 그대로 두고
     * 목록에서 하나 지운다. 소멸 저주 아이템은 목록에 있어도 칸에서 지운다. 끝까지 맞는 칸이 없던 아이템은 인벤토리에 새로 넣는다
     * (덜 찬 같은 아이템 칸 → 빈 칸, 주 인벤토리 0..35 순서. 자리가 없으면 버린다). 남긴 아이템을 drops() 에서 빼지 않으면 복제된다.
     */
    public List<ItemStack> itemsToKeep() { return itemsToKeep; }

    /** 죽은 자리에 경험치 구슬을 낼지(Paper shouldDropExperience). 기본 true. false 면 droppedExp 와 무관하게 구슬이 없다. */
    public boolean shouldDropExperience() { return shouldDropExperience; }

    public void shouldDropExperience(boolean v) { this.shouldDropExperience = v; }

    /** 취소했을 때 되살릴 체력. 기본은 죽는 순간 최대 체력. */
    public float reviveHealth() { return reviveHealth; }

    /** @throws IllegalArgumentException 0 이하(최대 체력이 0 이 아닐 때)이거나 최대 체력보다 크면(Paper 와 같다) */
    public void reviveHealth(float health) {
        if ((maxHealth != 0 && health <= 0) || health > maxHealth) {
            throw new IllegalArgumentException("되살릴 체력은 0 초과 " + maxHealth + " 이하여야 한다: " + health);
        }
        this.reviveHealth = health;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    /** 취소하면 죽지 않는다 — 체력이 0 이하면 reviveHealth 로 되돌린다. */
    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
