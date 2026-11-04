// 금고 시험: 설정/서버/공유 데이터 NBT(기본값 생략·검증 거부), 범위에 따른 inactive↔active, 표시 아이템 20틱 순환(레벨 난수),
// 열쇠 넣기(맞지 않음·이미 받음·창작·빈 보상), unlocking→ejecting 배출 주기와 소리 높이, 128명 제한.
// 기대값은 바닐라 1.21.1 디컴파일(VaultBlockEntity.Server·VaultState·VaultServerData·VaultSharedData)을 손으로 계산한 것이다.
package org.domi.furnace.server.block.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.Test;

class VaultBlockEntityTest {

    static final Prop STATE = B.prop("vault_state"), OMINOUS = B.prop("ominous");
    static final Pos POS = new Pos(1, -60, 1);
    static final UUID P1 = new UUID(1, 2), P2 = new UUID(3, 4);
    static final String REWARD = "minecraft:chests/trial_chambers/reward";

    static { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static int item(String name) { return ItemContext.id("minecraft:item", "minecraft:" + name); }

    static ItemStack stack(String name, int count) { return new ItemStack(item(name), count); }

    static int vaultState(String state, boolean ominous) { return OMINOUS.with(STATE.with(B.defaultState("minecraft:vault"), state), ominous); }

    static VaultBlockEntity place(TrialSpawnerBlockEntityTest.Lvl level, String state, boolean ominous, NbtCompound tag) {
        level.setBlock(POS, vaultState(state, ominous), 3);
        var be = (VaultBlockEntity) level.getBlockEntity(POS);
        assertNotNull(be);
        be.load(tag);
        return be;
    }

    static NbtCompound config(String key, int count, String table) {
        NbtCompound ki = new NbtCompound();
        ki.putString("id", key);
        ki.putInt("count", count);
        NbtCompound c = new NbtCompound();
        c.putCompound("key_item", ki);
        if (table != null) c.putString("loot_table", table);
        NbtCompound t = new NbtCompound();
        t.putCompound("config", c);
        return t;
    }

    static NbtList uuids(UUID... us) {
        NbtList l = new NbtList(NbtIo.TAG_INT_ARRAY);
        for (UUID u : us) l.add(org.domi.furnace.server.bossbar.CustomBossEvents.uuidToInts(u));
        return l;
    }

    static Player player(TrialSpawnerBlockEntityTest.Lvl level, UUID id, double x, double y, double z) {
        Player p = new Player(level.players.size() + 1, id, "P" + (level.players.size() + 1), new ConcurrentEmbeddedChannel());
        p.applyMove(x, y, z, 0, 0, true);
        level.players.add(p);
        return p;
    }

    /** 시험용 행위자: 손 하나, 통계·행운 기록. */
    static class Actor implements BlockActor {
        ItemStack hand;
        boolean creative;
        float luck;
        final UUID id;
        final List<String> stats = new ArrayList<>();

        Actor(UUID id, ItemStack hand) {
            this.id = id;
            this.hand = hand;
        }

        public float yRot() { return 0; }
        public float xRot() { return 0; }
        public double x() { return 1.5; }
        public double y() { return -60; }
        public double z() { return 3.5; }
        public double eyeHeight() { return 1.62; }
        public boolean isSecondaryUseActive() { return false; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return creative; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return creative; }
        public int permissionLevel() { return 0; }
        public ItemStack itemInHand(int h) { return hand; }
        public void setItemInHand(int h, ItemStack s) { hand = s; }
        public Object handle() { return this; }
        @Override public UUID uuid() { return id; }
        @Override public float luck() { return luck; }
        @Override public void awardStat(String stat) { stats.add(stat); }
    }

    static final ItemStack DIAMOND2 = stack("diamond", 2), EMERALD = stack("emerald", 1);

    // ------------------------------------------------------------------ NBT

    @Test
    void templateVaultConfigIsReadAsIs() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(1);
        // 바닐라 trial_chambers/reward/vault.nbt · ominous_vault.nbt 가 놓는 NBT
        var normal = place(level, "inactive", false, config("minecraft:trial_key", 1, REWARD));
        assertEquals(REWARD, normal.config().lootTable());
        assertEquals(item("trial_key"), normal.config().keyItem().itemId());
        assertEquals(1, normal.config().keyItem().count());
        assertEquals(4.0, normal.config().activationRange());
        assertEquals(4.5, normal.config().deactivationRange());
        var ominous = place(new TrialSpawnerBlockEntityTest.Lvl(1), "inactive", true, config("minecraft:ominous_trial_key", 1, "minecraft:chests/trial_chambers/reward_ominous"));
        assertEquals("minecraft:chests/trial_chambers/reward_ominous", ominous.config().lootTable());
        assertEquals(item("ominous_trial_key"), ominous.config().keyItem().itemId());
        // 저장: config 는 늘 쓰고 기본값과 같은 loot_table 은 생략, key_item 은 비어 있지 않으면 늘 쓴다
        NbtCompound out = normal.saveWithoutMetadata();
        assertFalse(out.getCompound("config").contains("loot_table"));
        assertFalse(out.getCompound("config").contains("activation_range"));
        assertEquals("minecraft:trial_key", out.getCompound("config").getCompound("key_item").getString("id"));
        assertTrue(out.contains("shared_data") && out.contains("server_data"));
        assertTrue(out.getCompound("shared_data").tags().isEmpty());
        assertTrue(out.getCompound("server_data").tags().isEmpty());
        assertEquals("minecraft:chests/trial_chambers/reward_ominous", ominous.saveWithoutMetadata().getCompound("config").getString("loot_table"));
    }

    @Test
    void defaultConfigAndInvalidRangesKeepPreviousConfig() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(1);
        var be = place(level, "inactive", false, new NbtCompound());
        assertEquals(REWARD, be.config().lootTable());
        assertEquals(item("trial_key"), be.config().keyItem().itemId(), "기본 열쇠는 시련 열쇠");
        NbtCompound c = new NbtCompound();
        c.putDouble("activation_range", 6.0);
        c.putDouble("deactivation_range", 5.0);   // 활성 > 비활성 → 거부
        c.putString("loot_table", "minecraft:chests/trial_chambers/reward_rare");
        NbtCompound t = new NbtCompound();
        t.putCompound("config", c);
        be.load(t);
        assertEquals(REWARD, be.config().lootTable(), "검증 실패면 이전 설정을 그대로 둔다");
        c.putDouble("deactivation_range", 7.0);
        be.load(t);
        assertEquals("minecraft:chests/trial_chambers/reward_rare", be.config().lootTable());
        assertEquals(6.0, be.config().activationRange());
        // key_item 이 없으면 빈 스택(기본 열쇠가 아니다)
        assertTrue(be.config().keyItem().isEmpty());
        assertEquals(6.0, be.saveWithoutMetadata().getCompound("config").getDouble("activation_range"));
    }

    @Test
    void serverAndSharedDataRoundTrip() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(1);
        NbtCompound server = new NbtCompound();
        server.putList("rewarded_players", uuids(P1, P2));
        server.putLong("state_updating_resumes_at", 500L);
        NbtList items = new NbtList(NbtIo.TAG_COMPOUND);
        items.add(org.domi.furnace.server.entity.ItemStackNbt.write(DIAMOND2));
        items.add(org.domi.furnace.server.entity.ItemStackNbt.write(EMERALD));
        server.putList("items_to_eject", items);
        server.putInt("total_ejections_needed", 5);
        NbtCompound shared = new NbtCompound();
        shared.putCompound("display_item", org.domi.furnace.server.entity.ItemStackNbt.write(EMERALD));
        shared.putList("connected_players", uuids(P1));
        shared.putDouble("connected_particles_range", 6.5);
        NbtCompound t = new NbtCompound();
        t.putCompound("server_data", server);
        t.putCompound("shared_data", shared);
        var be = place(level, "ejecting", false, t);
        assertEquals(List.of(P1, P2), new ArrayList<>(be.serverData().rewardedPlayers()), "넣은 순서를 지킨다");
        assertEquals(500L, be.serverData().stateUpdatingResumesAt());
        assertEquals(2, be.serverData().itemsToEject().size());
        assertEquals(0, be.serverData().totalEjectionsNeeded(), "바닐라 copyFrom 은 total_ejections_needed 를 옮기지 않는다");
        assertEquals(item("emerald"), be.sharedData().displayItem().itemId());
        assertEquals(java.util.Set.of(P1), be.sharedData().connectedPlayers());
        assertEquals(6.5, be.sharedData().connectedParticlesRange());
        NbtCompound out = be.saveWithoutMetadata();
        assertEquals(2, out.getCompound("server_data").getList("rewarded_players").size());
        assertEquals(500L, out.getCompound("server_data").getLong("state_updating_resumes_at"));
        assertEquals(2, out.getCompound("server_data").getList("items_to_eject").size());
        assertFalse(out.getCompound("server_data").contains("total_ejections_needed"), "0 은 쓰지 않는다");
        assertEquals(6.5, out.getCompound("shared_data").getDouble("connected_particles_range"));
        // 갱신 태그는 shared_data 만
        assertEquals(java.util.Set.of("shared_data"), be.updateTag().tags().keySet());
        assertTrue(be.hasUpdatePacket());
        // 되돌아온 총 배출 수가 0 이라 진행도는 남은 수가 된다: 1 − (3−1)/(0−1) = 3
        be.serverData().itemsToEject.add(stack("gold_ingot", 1));
        assertEquals(3.0F, be.serverData().ejectionProgress());
    }

    @Test
    void invalidItemsToEjectEntryDropsTheWholeList() {
        NbtCompound server = new NbtCompound();
        NbtList items = new NbtList(NbtIo.TAG_COMPOUND);
        items.add(org.domi.furnace.server.entity.ItemStackNbt.write(DIAMOND2));
        NbtCompound bad = new NbtCompound();
        bad.putString("id", "minecraft:not_an_item");
        items.add(bad);
        server.putList("items_to_eject", items);
        NbtCompound t = new NbtCompound();
        t.putCompound("server_data", server);
        var be = place(new TrialSpawnerBlockEntityTest.Lvl(1), "inactive", false, t);
        assertTrue(be.serverData().itemsToEject().isEmpty());
    }

    @Test
    void ejectionProgressAndRewardedCap() {
        var d = new VaultServerData();
        d.setItemsToEject(List.of(DIAMOND2, EMERALD, stack("gold_ingot", 1)));
        assertEquals(3, d.totalEjectionsNeeded());
        assertEquals(0.0F, d.ejectionProgress(), "남은 3 / 총 3 → 1 − (3−1)/(3−1)");
        d.popNextItemToEject();
        assertEquals(0.5F, d.ejectionProgress(), "남은 2 → 1 − 1/2");
        d.popNextItemToEject();
        assertEquals(1.0F, d.ejectionProgress(), "남은 1 → 1 − 0");
        var one = new VaultServerData();
        one.setItemsToEject(List.of(EMERALD));
        assertEquals(1.0F, one.ejectionProgress(), "총 1 이면 1.0");
        var cap = new VaultServerData();
        for (int i = 0; i < 130; i++) cap.addToRewardedPlayers(new UUID(0, i));
        assertEquals(128, cap.rewardedPlayers().size());
        assertFalse(cap.rewardedPlayers().contains(new UUID(0, 0)), "가장 오래된 둘이 밀려났다");
        assertFalse(cap.rewardedPlayers().contains(new UUID(0, 1)));
        assertTrue(cap.rewardedPlayers().contains(new UUID(0, 2)));
        assertTrue(cap.rewardedPlayers().contains(new UUID(0, 129)));
    }

    @Test
    void blockEntityFactoryLoadsVaultFromChunkNbt() {
        NbtCompound tag = config("minecraft:trial_key", 1, REWARD);
        tag.putString("id", "minecraft:vault");
        BlockEntity be = BlockEntities.loadStatic(POS, vaultState("inactive", false), tag);
        assertInstanceOf(VaultBlockEntity.class, be);
        assertEquals("minecraft:vault", be.saveWithFullMetadata().getString("id"));
    }

    // ------------------------------------------------------------------ 상태

    @Test
    void inactiveBecomesActiveWithPlayerInActivationRangeAndShowsDisplayItem() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(7);
        level.loot.put(REWARD, List.of(DIAMOND2, EMERALD));
        var be = place(level, "inactive", false, config("minecraft:trial_key", 1, REWARD));
        player(level, P1, 1.5, -60, 3.5);   // 블록 거리² = 4 < 4² = 16
        level.time = 100;
        be.tick(level);
        assertEquals(VaultState.ACTIVE, be.getVaultState());
        assertEquals(java.util.Set.of(P1), be.sharedData().connectedPlayers());
        assertEquals(120L, be.serverData().stateUpdatingResumesAt(), "20틱 쉰다");
        // 표시 아이템: 전리품 표를 레벨 난수로 평가한 목록에서 nextInt(개수)
        RandomSource ref = RandomSource.create(7);
        ItemStack expect = List.of(DIAMOND2, EMERALD).get(ref.nextInt(2));
        assertEquals(expect.itemId(), be.sharedData().displayItem().itemId());
        assertSame(level.rnd, level.lastRandom, "표시 아이템은 레벨 난수로 평가한다");
        assertArrayEquals(new double[] {1.5, -59.5, 1.5}, level.lastOrigin, 1.0E-12);
        assertTrue(level.events.stream().anyMatch(e -> e[0] == 3015 && e[4] == 0 && e[1] == 1 && e[2] == -60 && e[3] == 1));
        assertEquals(ref.nextLong(), level.rnd.nextLong());
        // 불길한 금고는 이벤트 데이터 1
        var level2 = new TrialSpawnerBlockEntityTest.Lvl(7);
        var o = place(level2, "inactive", true, config("minecraft:ominous_trial_key", 1, REWARD));
        player(level2, P1, 1.5, -60, 3.5);
        o.tick(level2);
        assertTrue(level2.events.stream().anyMatch(e -> e[0] == 3015 && e[4] == 1));
    }

    @Test
    void ranges_activationIsStricterThanDeactivationAndCreativeCounts() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(7);
        level.loot.put(REWARD, List.of(DIAMOND2));
        var be = place(level, "inactive", false, config("minecraft:trial_key", 1, REWARD));
        Player p = player(level, P1, 1.5, -60, 5.5);   // 블록 거리² 16: 활성 범위(< 16)에는 못 든다
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);
        level.time = 0;
        be.tick(level);
        assertEquals(VaultState.INACTIVE, be.getVaultState());
        // 활성이 되어도(다른 방법으로) 비활성 범위 4.5² = 20.25 안이면 유지, 거리² 25 면 끈다
        var level2 = new TrialSpawnerBlockEntityTest.Lvl(7);
        level2.loot.put(REWARD, List.of(DIAMOND2));
        var act = place(level2, "active", false, config("minecraft:trial_key", 1, REWARD));
        Player q = player(level2, P1, 1.5, -60, 5.5);   // 거리² 16 < 20.25
        q.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);   // 창작 모드도 센다
        act.tick(level2);
        assertEquals(VaultState.ACTIVE, act.getVaultState());
        q.applyMove(1.5, -60, 6.5, 0, 0, true);   // 거리² 25
        level2.time = 20;
        act.tick(level2);
        assertEquals(VaultState.INACTIVE, act.getVaultState());
        assertTrue(act.sharedData().displayItem().isEmpty(), "inactive 진입 시 표시 아이템을 비운다");
        assertTrue(level2.events.stream().anyMatch(e -> e[0] == 3016));
    }

    @Test
    void rewardedPlayersDoNotKeepTheVaultActive() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(7);
        NbtCompound t = config("minecraft:trial_key", 1, REWARD);
        NbtCompound server = new NbtCompound();
        server.putList("rewarded_players", uuids(P1));
        t.putCompound("server_data", server);
        var be = place(level, "inactive", false, t);
        player(level, P1, 1.5, -60, 3.5);
        be.tick(level);
        assertEquals(VaultState.INACTIVE, be.getVaultState());
        assertTrue(be.sharedData().connectedPlayers().isEmpty());
    }

    @Test
    void displayItemCyclesEvery20TicksOnlyWhileActiveUsingLevelRandom() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(11);
        level.loot.put(REWARD, List.of(DIAMOND2, EMERALD, stack("gold_ingot", 3)));
        NbtCompound t = config("minecraft:trial_key", 1, REWARD);
        NbtCompound server = new NbtCompound();
        server.putLong("state_updating_resumes_at", 10_000L);   // 상태 갱신은 막고 순환만 본다
        t.putCompound("server_data", server);
        var be = place(level, "active", false, t);
        level.time = 41;
        be.tick(level);
        assertEquals(0, level.lootSeeds.size(), "20의 배수가 아니면 순환하지 않는다");
        level.time = 40;
        be.tick(level);
        RandomSource ref = RandomSource.create(11);
        int pick = ref.nextInt(3);
        assertEquals(List.of(DIAMOND2, EMERALD, stack("gold_ingot", 3)).get(pick).itemId(), be.sharedData().displayItem().itemId());
        assertEquals(ref.nextLong(), level.rnd.nextLong());
        // 덮어쓴 표시 표
        var level2 = new TrialSpawnerBlockEntityTest.Lvl(11);
        level2.loot.put("minecraft:chests/trial_chambers/reward_common", List.of(EMERALD));
        NbtCompound t2 = config("minecraft:trial_key", 1, REWARD);
        t2.getCompound("config").putString("override_loot_table_to_display", "minecraft:chests/trial_chambers/reward_common");
        t2.putCompound("server_data", server);
        var be2 = place(level2, "active", false, t2);
        level2.time = 40;
        be2.tick(level2);
        assertEquals(item("emerald"), be2.sharedData().displayItem().itemId());
    }

    @Test
    void noKeyOrEmptyTableMeansNoDisplayItem() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(11);
        level.loot.put(REWARD, List.of(DIAMOND2));
        NbtCompound t = new NbtCompound();
        NbtCompound c = new NbtCompound();
        c.putString("loot_table", "minecraft:empty");
        t.putCompound("config", c);
        NbtCompound server = new NbtCompound();
        server.putLong("state_updating_resumes_at", 10_000L);
        t.putCompound("server_data", server);
        var be = place(level, "active", false, t);
        level.time = 40;
        be.tick(level);
        assertTrue(be.sharedData().displayItem().isEmpty());
        assertEquals(0, level.lootSeeds.size(), "표시 아이템을 못 정하면 전리품 표를 평가하지 않는다");
    }

    // ------------------------------------------------------------------ 열쇠

    @Test
    void keyInsertionUnlocksStoresRewardsAndRecordsPlayer() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2, EMERALD));
        var be = place(level, "active", false, config("minecraft:trial_key", 1, REWARD));
        Player p = player(level, P1, 1.5, -60, 3.5);
        be.sharedData().connectedPlayers().add(P1);
        Actor a = new Actor(P1, stack("trial_key", 3));
        a.luck = 1.5F;
        level.time = 1000;
        assertTrue(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, a, 0, a.hand));
        assertEquals(2, a.hand.count(), "열쇠 하나를 쓴다");
        assertEquals(List.of("minecraft:used:minecraft:trial_key"), a.stats);
        assertEquals(VaultState.UNLOCKING, be.getVaultState());
        assertEquals(List.of(DIAMOND2.itemId(), EMERALD.itemId()), be.serverData().itemsToEject().stream().map(ItemStack::itemId).toList());
        assertEquals(2, be.serverData().totalEjectionsNeeded());
        assertEquals(EMERALD.itemId(), be.sharedData().displayItem().itemId(), "표시 아이템 = 목록 끝(가장 먼저 내놓을 것)");
        assertEquals(1014L, be.serverData().stateUpdatingResumesAt(), "14틱 쉰다");
        assertTrue(be.serverData().rewardedPlayers().contains(P1));
        assertTrue(be.sharedData().connectedPlayers().isEmpty(), "보상을 받은 플레이어는 연결 목록에서 빠진다");
        assertTrue(level.sounds.contains("minecraft:block.vault.insert_item"));
        assertEquals(1.5F, level.lastLuck, "행운을 표 평가에 넘긴다");
        assertArrayEquals(new double[] {1.5, -59.5, 1.5}, level.lastOrigin, 1.0E-12);
        assertEquals(0L, level.lootSeeds.get(level.lootSeeds.size() - 1)[0], "시드 없이 평가");
        assertNull(level.lastRandom, "보상은 레벨 난수가 아니라 표의 난수");
        assertNotNull(p);
    }

    @Test
    void wrongKeyOrTooFewKeysFailWithRateLimitedSound() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        var be = place(level, "active", false, config("minecraft:trial_key", 2, REWARD));
        Actor wrong = new Actor(P1, stack("stone", 5));
        level.time = 1000;
        assertFalse(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, wrong, 0, wrong.hand));
        assertEquals(List.of("minecraft:block.vault.insert_item_fail"), level.sounds);
        level.time = 1014;
        VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, wrong, 0, wrong.hand);
        assertEquals(1, level.sounds.size(), "15틱 안에 같은 실패 소리는 다시 내지 않는다");
        level.time = 1015;
        Actor few = new Actor(P1, stack("trial_key", 1));   // 필요 2 개
        VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, few, 0, few.hand);
        assertEquals(2, level.sounds.size());
        assertEquals(VaultState.ACTIVE, be.getVaultState());
        assertEquals(1, few.hand.count());
        assertTrue(few.stats.isEmpty());
        // 2개 필요하고 3개 들면 2개 소비
        Actor ok = new Actor(P2, stack("trial_key", 3));
        assertTrue(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, ok, 0, ok.hand));
        assertEquals(1, ok.hand.count());
    }

    @Test
    void alreadyRewardedPlayerIsRejectedAndKeyKept() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        NbtCompound t = config("minecraft:trial_key", 1, REWARD);
        NbtCompound server = new NbtCompound();
        server.putList("rewarded_players", uuids(P1));
        t.putCompound("server_data", server);
        var be = place(level, "active", false, t);
        Actor a = new Actor(P1, stack("trial_key", 1));
        level.time = 500;
        assertFalse(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, a, 0, a.hand));
        assertEquals(List.of("minecraft:block.vault.reject_rewarded_player"), level.sounds);
        assertEquals(1, a.hand.count());
        assertEquals(VaultState.ACTIVE, be.getVaultState());
    }

    @Test
    void creativeKeepsKeyAndEmptyRewardsDoNothing() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        var be = place(level, "active", false, config("minecraft:trial_key", 1, REWARD));
        Actor creative = new Actor(P1, stack("trial_key", 1));
        creative.creative = true;
        assertTrue(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, creative, 0, creative.hand));
        assertEquals(1, creative.hand.count(), "창작 모드는 열쇠를 소비하지 않는다");
        assertEquals(VaultState.UNLOCKING, be.getVaultState());
        var level2 = new TrialSpawnerBlockEntityTest.Lvl(5);   // 보상이 비면(표 없음) 아무 일도 없다
        var be2 = place(level2, "active", false, config("minecraft:trial_key", 1, REWARD));
        Actor a = new Actor(P1, stack("trial_key", 1));
        assertFalse(VaultBlockEntity.Server.tryInsertKey(level2, POS, be2.blockState(), be2, a, 0, a.hand));
        assertEquals(1, a.hand.count());
        assertEquals(VaultState.ACTIVE, be2.getVaultState());
        assertTrue(be2.serverData().rewardedPlayers().isEmpty());
    }

    @Test
    void inactiveVaultIgnoresKeys() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        var be = place(level, "inactive", false, config("minecraft:trial_key", 1, REWARD));
        Actor a = new Actor(P1, stack("trial_key", 1));
        assertFalse(VaultBlockEntity.Server.tryInsertKey(level, POS, be.blockState(), be, a, 0, a.hand));
        assertEquals(1, a.hand.count());
        assertTrue(level.sounds.isEmpty());
    }

    @Test
    void blockUseItemOnDispatchesByStateAndStack() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        var be = place(level, "inactive", false, config("minecraft:trial_key", 1, REWARD));
        Actor a = new Actor(P1, stack("trial_key", 2));
        Hit hit = new Hit(POS, 1, 1.5, -59, 1.5, false);
        var behavior = B.behavior(be.blockState());
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, behavior.useItemOn(a.hand, be.blockState(), level, POS, a, 0, hit), "비활성");
        level.setBlock(POS, vaultState("active", false), 3);
        assertEquals(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION, behavior.useItemOn(ItemStack.EMPTY, be.blockState(), level, POS, a, 0, hit), "빈 손");
        assertEquals(ItemInteractionResult.SUCCESS, behavior.useItemOn(a.hand, be.blockState(), level, POS, a, 0, hit));
        assertEquals(1, a.hand.count());
        assertEquals(VaultState.UNLOCKING, be.getVaultState());
    }

    // ------------------------------------------------------------------ 배출

    @Test
    void unlockingThenEjectsOneItemEvery20TicksFromTheEnd() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        NbtCompound t = config("minecraft:trial_key", 1, REWARD);
        NbtCompound server = new NbtCompound();
        NbtList items = new NbtList(NbtIo.TAG_COMPOUND);
        items.add(org.domi.furnace.server.entity.ItemStackNbt.write(DIAMOND2));
        items.add(org.domi.furnace.server.entity.ItemStackNbt.write(EMERALD));
        server.putList("items_to_eject", items);
        server.putLong("state_updating_resumes_at", 1014L);
        t.putCompound("server_data", server);
        var be = place(level, "unlocking", false, t);
        be.serverData().totalEjectionsNeeded = 2;
        be.sharedData().setDisplayItem(EMERALD);
        level.time = 1013;
        be.tick(level);
        assertEquals(VaultState.UNLOCKING, be.getVaultState(), "재개 시각 전에는 상태를 바꾸지 않는다");
        level.time = 1014;
        be.tick(level);
        assertEquals(VaultState.EJECTING, be.getVaultState());
        assertEquals(1034L, be.serverData().stateUpdatingResumesAt());
        assertTrue(level.sounds.contains("minecraft:block.vault.open_shutter"));
        // 첫 배출: 목록 끝(에메랄드). 진행도 = 1 − (2−1)/(2−1) = 0 → 높이 0.8 + 0.4×0
        level.time = 1034;
        be.tick(level);
        var box = new org.domi.furnace.server.entity.AABB(-5, -70, -5, 10, -50, 10);
        assertEquals(1, level.getEntitiesOfClass(ItemEntity.class, box, e -> true).size());
        assertEquals(EMERALD.itemId(), level.getEntitiesOfClass(ItemEntity.class, box, e -> true).get(0).getItem().itemId());
        int i = level.sounds.indexOf("minecraft:block.vault.eject_item");
        assertEquals(0.8F, level.pitches.get(i), 1.0E-6F);
        assertEquals(DIAMOND2.itemId(), be.sharedData().displayItem().itemId(), "표시 아이템이 다음 것으로");
        assertEquals(1054L, be.serverData().stateUpdatingResumesAt());
        assertTrue(level.hasEvent(3017));
        // 두 번째: 진행도 = 1 − (1−1)/(2−1) = 1 → 높이 1.2
        level.time = 1054;
        be.tick(level);
        assertEquals(2, level.getEntitiesOfClass(ItemEntity.class, box, e -> true).size());
        int j = level.sounds.lastIndexOf("minecraft:block.vault.eject_item");
        assertEquals(1.2F, level.pitches.get(j), 1.0E-6F);
        assertTrue(be.sharedData().displayItem().isEmpty());
        // 목록이 비면 다음 틱에 배출을 끝내고 범위를 본다: 근처에 보상 받지 않은 플레이어가 없어 inactive, 셔터 닫는 소리
        level.time = 1074;
        be.tick(level);
        assertEquals(VaultState.INACTIVE, be.getVaultState());
        assertEquals(0, be.serverData().totalEjectionsNeeded());
        assertTrue(level.sounds.contains("minecraft:block.vault.close_shutter"));
    }

    @Test
    void afterEjectionAnUnrewardedPlayerKeepsItActive() {
        var level = new TrialSpawnerBlockEntityTest.Lvl(5);
        level.loot.put(REWARD, List.of(DIAMOND2));
        NbtCompound t = config("minecraft:trial_key", 1, REWARD);
        NbtCompound server = new NbtCompound();
        server.putLong("state_updating_resumes_at", 100L);
        t.putCompound("server_data", server);
        var be = place(level, "ejecting", false, t);   // 내놓을 것이 없는 ejecting
        player(level, P2, 1.5, -60, 3.5);
        level.time = 100;
        be.tick(level);
        assertEquals(VaultState.ACTIVE, be.getVaultState(), "배출이 끝나면 비활성 범위로 확인해 active");
        assertEquals(java.util.Set.of(P2), be.sharedData().connectedPlayers());
        assertEquals(120L, be.serverData().stateUpdatingResumesAt());
    }
}
