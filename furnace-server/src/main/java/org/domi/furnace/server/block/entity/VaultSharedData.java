// 바닐라 VaultSharedData: 클라이언트와 나누는 금고 상태(청크 패킷·갱신 태그에 실린다) — 표시 아이템, 연결된 플레이어(입자), 입자 범위.
// 저장 키(shared_data): display_item(비면 생략), connected_players(비면 생략), connected_particles_range(4.5 이면 생략).
package org.domi.furnace.server.block.entity;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.bossbar.CustomBossEvents;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.ItemStackNbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class VaultSharedData {

    private ItemStack displayItem = ItemStack.EMPTY;
    private Set<UUID> connectedPlayers = new LinkedHashSet<>();
    private double connectedParticlesRange = VaultConfig.DEFAULT_DEACTIVATION_RANGE;
    boolean isDirty;

    public ItemStack displayItem() { return displayItem; }

    public boolean hasDisplayItem() { return !displayItem.isEmpty(); }

    public Set<UUID> connectedPlayers() { return connectedPlayers; }

    public double connectedParticlesRange() { return connectedParticlesRange; }

    boolean hasConnectedPlayers() { return !connectedPlayers.isEmpty(); }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return a.count() == b.count() && a.sameItemSameComponents(b);
    }

    /** setDisplayItem: 달라졌으면 복사해 두고 갱신 표시. */
    void setDisplayItem(ItemStack stack) {
        if (!same(displayItem, stack)) {
            displayItem = stack;
            markDirty();
        }
    }

    /**
     * updateConnectedPlayersWithinRange: 범위 안(시선 없이) 플레이어 중 이미 보상을 받은 사람을 뺀 집합이 달라졌으면 바꾼다.
     * 감지기는 창작 모드 플레이어를 포함한다(INCLUDING_CREATIVE_PLAYERS).
     */
    void updateConnectedPlayersWithinRange(ChunkLevel level, Pos pos, VaultServerData serverData, VaultConfig config, double range) {
        Set<UUID> detected = new HashSet<>();
        List<UUID> found = TrialSpawner.PlayerDetector.INCLUDING_CREATIVE_PLAYERS.detect(level, pos, range, false);
        for (UUID u : found) if (!serverData.rewardedPlayers.contains(u)) detected.add(u);
        if (!connectedPlayers.equals(detected)) {
            connectedPlayers = detected;
            markDirty();
        }
    }

    void markDirty() { isDirty = true; }

    // ------------------------------------------------------------------ NBT

    /** 코덱 해석 결과를 반영한다(copyFrom: 세 필드 모두). */
    public void load(NbtCompound t) {
        displayItem = t.tags().get("display_item") instanceof NbtCompound c ? ItemStackNbt.read(c) : ItemStack.EMPTY;
        Set<UUID> players = new LinkedHashSet<>();
        if (t.tags().get("connected_players") instanceof NbtList l) {
            for (Object e : l.items()) if (e instanceof int[] a && a.length == 4) players.add(CustomBossEvents.uuidFromInts(a));
        }
        connectedPlayers = players;
        connectedParticlesRange = t.tags().get("connected_particles_range") instanceof Number n ? n.doubleValue() : VaultConfig.DEFAULT_DEACTIVATION_RANGE;
    }

    public NbtCompound save() {
        NbtCompound t = new NbtCompound();
        if (!displayItem.isEmpty()) t.putCompound("display_item", ItemStackNbt.write(displayItem));
        if (!connectedPlayers.isEmpty()) {
            NbtList l = new NbtList(NbtIo.TAG_INT_ARRAY);
            for (UUID u : connectedPlayers) l.add(CustomBossEvents.uuidToInts(u));
            t.putList("connected_players", l);
        }
        if (Double.compare(connectedParticlesRange, VaultConfig.DEFAULT_DEACTIVATION_RANGE) != 0) t.putDouble("connected_particles_range", connectedParticlesRange);
        return t;
    }
}
