// 바닐라 VaultServerData: 서버만 아는 금고 상태 — 보상을 받은 플레이어(최대 128명, 오래된 순으로 밀려남), 상태 갱신 재개 시각, 내보낼 아이템, 총 배출 수.
// 저장 키(server_data): rewarded_players, state_updating_resumes_at, items_to_eject, total_ejections_needed.
// 바닐라 load 는 total_ejections_needed 를 반영하지 않는다(copyFrom 이 빠뜨림) — 다시 읽은 뒤의 배출 진행도는 이 값이 0 이라는 전제로 계산한다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.bossbar.CustomBossEvents;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.ItemStackNbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class VaultServerData {

    static final int MAX_REWARD_PLAYERS = 128;

    final Set<UUID> rewardedPlayers = new LinkedHashSet<>();
    long stateUpdatingResumesAt;
    final List<ItemStack> itemsToEject = new ArrayList<>();
    long lastInsertFailTimestamp;
    int totalEjectionsNeeded;
    boolean isDirty;

    public Set<UUID> rewardedPlayers() { return rewardedPlayers; }

    public long stateUpdatingResumesAt() { return stateUpdatingResumesAt; }

    public List<ItemStack> itemsToEject() { return itemsToEject; }

    public int totalEjectionsNeeded() { return totalEjectionsNeeded; }

    public boolean isDirty() { return isDirty; }

    void setLastInsertFailTimestamp(long t) { lastInsertFailTimestamp = t; }

    long lastInsertFailTimestamp() { return lastInsertFailTimestamp; }

    boolean hasRewardedPlayer(UUID player) { return player != null && rewardedPlayers.contains(player); }

    /** addToRewardedPlayers: 128명을 넘으면 가장 오래된 한 명을 뺀다. */
    void addToRewardedPlayers(UUID player) {
        rewardedPlayers.add(player);
        if (rewardedPlayers.size() > MAX_REWARD_PLAYERS) {
            Iterator<UUID> it = rewardedPlayers.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        markChanged();
    }

    void pauseStateUpdatingUntil(long time) {
        stateUpdatingResumesAt = time;
        markChanged();
    }

    /** markEjectionFinished: 총 배출 수를 0 으로. */
    void markEjectionFinished() {
        totalEjectionsNeeded = 0;
        markChanged();
    }

    /** setItemsToEject: 배출 목록을 바꾸고 총 배출 수를 그 크기로. */
    void setItemsToEject(List<ItemStack> items) {
        itemsToEject.clear();
        itemsToEject.addAll(items);
        totalEjectionsNeeded = itemsToEject.size();
        markChanged();
    }

    /** getNextItemToEject: 목록 끝(없으면 빈 스택). */
    ItemStack getNextItemToEject() { return itemsToEject.isEmpty() ? ItemStack.EMPTY : itemsToEject.get(itemsToEject.size() - 1); }

    /** popNextItemToEject: 끝에서 하나 꺼낸다(없으면 빈 스택). */
    ItemStack popNextItemToEject() {
        if (itemsToEject.isEmpty()) return ItemStack.EMPTY;
        markChanged();
        return itemsToEject.remove(itemsToEject.size() - 1);
    }

    /** ejectionProgress: 총 1 이면 1.0, 아니면 1 − inverseLerp(남은 수, 1, 총수) = 1 − (남은 − 1) / (총 − 1). 소리 높이에 쓴다. */
    float ejectionProgress() {
        if (totalEjectionsNeeded == 1) return 1.0F;
        float size = (float) itemsToEject.size();
        return 1.0F - (size - 1.0F) / ((float) totalEjectionsNeeded - 1.0F);
    }

    void markChanged() { isDirty = true; }

    // ------------------------------------------------------------------ NBT

    /** 코덱 해석 결과를 반영한다(copyFrom: 총 배출 수는 옮기지 않는다). */
    public void load(NbtCompound t) {
        Set<UUID> rewarded = new LinkedHashSet<>();
        if (t.tags().get("rewarded_players") instanceof NbtList l) {
            for (Object e : l.items()) if (e instanceof int[] a && a.length == 4) rewarded.add(CustomBossEvents.uuidFromInts(a));
        }
        stateUpdatingResumesAt = t.tags().get("state_updating_resumes_at") instanceof Number n ? n.longValue() : 0L;
        List<ItemStack> items = new ArrayList<>();
        if (t.tags().get("items_to_eject") instanceof NbtList l && (l.size() == 0 || l.elementType() == NbtIo.TAG_COMPOUND)) {
            for (Object e : l.items()) {
                ItemStack s = e instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
                if (s.isEmpty()) {   // 항목 하나라도 틀리면 목록 전체가 기본값(빈 목록)
                    items.clear();
                    break;
                }
                items.add(s);
            }
        }
        itemsToEject.clear();
        itemsToEject.addAll(items);
        rewardedPlayers.clear();
        rewardedPlayers.addAll(rewarded);
    }

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        if (!rewardedPlayers.isEmpty()) {
            NbtList l = new NbtList(NbtIo.TAG_INT_ARRAY);
            for (UUID u : rewardedPlayers) l.add(CustomBossEvents.uuidToInts(u));
            t.putList("rewarded_players", l);
        }
        if (stateUpdatingResumesAt != 0L) t.putLong("state_updating_resumes_at", stateUpdatingResumesAt);
        if (!itemsToEject.isEmpty()) {
            NbtList l = new NbtList(NbtIo.TAG_COMPOUND);
            for (ItemStack s : itemsToEject) l.add(ItemStackNbt.write(s));
            t.putList("items_to_eject", l);
        }
        if (totalEjectionsNeeded != 0) t.putInt("total_ejections_needed", totalEjectionsNeeded);
        return t;
    }
}
