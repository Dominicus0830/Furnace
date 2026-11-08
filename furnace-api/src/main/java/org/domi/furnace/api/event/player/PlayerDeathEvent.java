// 플레이어 사망 — 사망 메시지(null 이면 알리지 않음)·떨어뜨릴 아이템·떨어뜨릴 경험치·인벤토리/경험치 유지 여부를 바꿀 수 있다.
// 피해를 준 쪽의 스레드(소유 region·명령 스레드)에서 불린다.
//
// 떨어뜨릴 아이템({@link #drops()})은 지금 인벤토리에서 모은 목록(소멸 저주 아이템은 빠진다)이고 바꾸면 실제 드롭에 반영된다.
// keepInventory 와 drops 는 서로 독립이다: 목록에 남은 아이템은 keepInventory 가 true 여도 떨어지고(인벤토리에도 남는다),
// keepInventory 가 false 면 목록과 무관하게 죽을 때 인벤토리를 전부 비운다. 아이템을 지키려면 keepInventory(true) 와 drops().clear() 를 함께 쓴다.
package org.domi.furnace.api.event.player;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.text.Text;

public final class PlayerDeathEvent extends PlayerEvent {

    private Text deathMessage;
    private boolean keepInventory;
    private boolean keepLevel;
    private final List<ItemStack> drops;
    private int droppedExp;
    private int newExp;
    private int newLevel;
    private int newTotalExp;

    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory) {
        this(player, deathMessage, keepInventory, List.of(), 0);
    }

    public PlayerDeathEvent(Player player, Text deathMessage, boolean keepInventory, List<ItemStack> drops, int droppedExp) {
        super(player);
        this.deathMessage = deathMessage;
        this.keepInventory = keepInventory;
        this.keepLevel = keepInventory;
        this.drops = new ArrayList<>(drops);
        this.droppedExp = droppedExp;
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
}
