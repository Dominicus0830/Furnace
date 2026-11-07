// 플레이어별 숨기기·변장이 몹·물건 추적기(EntityTracker)와 연결별 패킷 변환(ViewRewriter)에서 어떻게 나가는지.
package org.domi.furnace.server.entity.view;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.AddEntity;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.Respawn;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityViewTrackerTest {

    private static final Object PLUGIN_A = "A", PLUGIN_B = "B";

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void clear() { EntityViewRules.clearForTest(); }

    private record Sent(int id, ByteBuf body) {}

    private static Player player(double x, double z) {
        Player p = new Player(900_000 + (int) (Math.random() * 1000), UUID.randomUUID(), "P", new org.domi.furnace.server.InlineEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(x, -60, z, 0, 0, true);
        return p;
    }

    private static EmbeddedChannel ch(Player p) { return (EmbeddedChannel) p.channel(); }

    private static List<Sent> drain(Player p) {
        EmbeddedChannel ch = ch(p);
        ch.runPendingTasks();
        List<Sent> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(new Sent(Types.readVarInt(b), b));
        }
        return out;
    }

    private static List<Integer> ids(List<Sent> l) {
        List<Integer> out = new ArrayList<>();
        for (Sent s : l) out.add(s.id);
        return out;
    }

    private static List<Integer> drainIds(Player p) {
        List<Sent> l = drain(p);
        List<Integer> out = ids(l);
        for (Sent s : l) s.body.release();
        return out;
    }

    /** add_entity 본문의 종류 번호(엔티티 번호·uuid 다음). */
    private static int addType(Sent s) {
        ByteBuf b = s.body.duplicate();
        Types.readVarInt(b);
        b.skipBytes(16);
        return Types.readVarInt(b);
    }

    private static UUID addUuid(Sent s) {
        ByteBuf b = s.body.duplicate();
        Types.readVarInt(b);
        return new UUID(b.readLong(), b.readLong());
    }

    /** set_entity_data 의 칸 번호들. */
    private static List<Integer> dataIndices(Sent s) {
        ByteBuf b = s.body.duplicate();
        Types.readVarInt(b);
        List<Integer> idx = new ArrayList<>();
        // 값 길이를 다 알 필요 없게 서버 쪽 레코드를 직접 다시 읽지 않고, 첫 칸만 확인할 수 있는 단순 형식만 쓴다
        int i = b.readUnsignedByte();
        while (i != 0xFF) {
            idx.add(i);
            int ser = Types.readVarInt(b);
            switch (ser) {
                case SetEntityData.SERIALIZER_BYTE -> b.skipBytes(1);
                case SetEntityData.SERIALIZER_INT, SetEntityData.SERIALIZER_POSE -> Types.readVarInt(b);
                case SetEntityData.SERIALIZER_FLOAT -> b.skipBytes(4);
                case SetEntityData.SERIALIZER_BOOLEAN -> b.skipBytes(1);
                default -> { return idx; }   // 시험에 쓰는 값 종류만
            }
            i = b.readUnsignedByte();
        }
        return idx;
    }

    private static Sent first(List<Sent> l, int id) {
        for (Sent s : l) if (s.id == id) return s;
        throw new AssertionError("패킷 " + id + " 없음: " + ids(l));
    }

    private static Appearance as(String type) {
        return new Appearance(EntityType.registryId(type), type, 0, new int[0], null, null, null);
    }

    private static BlockPlacementTest.TestLevel levelFor(Player... ps) {
        return new BlockPlacementTest.TestLevel() {
            @Override
            protected Iterable<Player> trackingPlayers() { return List.of(ps); }
        };
    }

    // ------------------------------------------------------------------ 숨기기

    @Test
    void hiddenEntityIsRemovedForThatViewerOnly_andComesBackOnShow() {
        Player a = player(3, 3), b = player(4, 4);
        var level = levelFor(a, b);
        ItemEntity e = new ItemEntity(level, level.nextEntityId(), 0.5, -59, 0.5, B.stack("minecraft:dirt", 1));
        level.addFreshEntity(e);
        drainIds(a);
        drainIds(b);

        var c = EntityViewRules.hide(PLUGIN_A, a, e.uuid(), null, false);
        assertTrue(c.changed());
        assertFalse(EntityViewRules.canSee(a, e.uuid()));
        assertTrue(EntityViewRules.canSee(b, e.uuid()));
        level.refreshViews(e, a, false);
        assertEquals(List.of(RemoveEntities.ID), drainIds(a));
        assertFalse(e.tracker.isSeenBy(a));
        assertTrue(e.tracker.isSeenBy(b));
        assertEquals(List.of(), drainIds(b));

        // 플레이어가 움직여 다시 보아도 숨김은 그대로
        e.tracker.updatePlayer(a, level.viewers());
        assertEquals(List.of(), drainIds(a));

        // 다른 플러그인도 숨겼으면 둘 다 거둬야 보인다
        EntityViewRules.hide(PLUGIN_B, a, e.uuid(), null, false);
        EntityViewRules.show(PLUGIN_A, a, e.uuid(), null, false);
        assertFalse(EntityViewRules.canSee(a, e.uuid()));
        EntityViewRules.show(PLUGIN_B, a, e.uuid(), null, false);
        assertTrue(EntityViewRules.canSee(a, e.uuid()));
        level.refreshViews(e, a, false);
        assertEquals(List.of(0x00, AddEntity.ID, SetEntityData.ID, 0x5A, 0x00), drainIds(a), "다시 보이면 스폰 묶음 전체");
        assertEquals(0, EntityViewRules.targetCount(), "규칙이 다 거둬지면 대상도 지운다");
        assertFalse(EntityViewRules.active());
    }

    @Test
    void hiddenByDefault_onlyShownViewersSee() {
        Player a = player(3, 3), b = player(4, 4);
        var level = levelFor(a, b);
        ItemEntity e = new ItemEntity(level, level.nextEntityId(), 0.5, -59, 0.5, B.stack("minecraft:dirt", 1));
        EntityViewRules.setHiddenByDefault(PLUGIN_A, e.uuid(), true, null, false);
        EntityViewRules.show(PLUGIN_A, a, e.uuid(), null, false);
        level.addFreshEntity(e);
        assertTrue(e.tracker.isSeenBy(a));
        assertFalse(e.tracker.isSeenBy(b));
        assertEquals(List.of(), drainIds(b));

        // 기본 숨김을 건 플러그인이 꺼지면 모두 본다
        List<EntityViewRules.Change> changes = EntityViewRules.removeOwner(PLUGIN_A);
        assertEquals(1, changes.size());
        level.refreshViews(e, null, false);
        assertTrue(e.tracker.isSeenBy(b));
    }

    // ------------------------------------------------------------------ 변장

    @Test
    void disguisedItemSpawnsAsZombie_andItemDataIsNotForwarded() {
        Player a = player(3, 3), b = player(4, 4);
        var level = levelFor(a, b);
        ItemEntity e = new ItemEntity(level, level.nextEntityId(), 0.5, -59, 0.5, B.stack("minecraft:dirt", 1));
        level.addFreshEntity(e);
        drainIds(a);
        drainIds(b);

        EntityViewRules.setAppearance(PLUGIN_A, e.uuid(), a, as("minecraft:zombie"), null, false);
        level.refreshViews(e, a, true);
        List<Sent> out = drain(a);
        // 지우고 → 묶음 [add_entity(좀비), 속도] — 아이템 칸(8)은 좀비의 체력 칸이라 넘기지 않는다
        assertEquals(List.of(RemoveEntities.ID, 0x00, AddEntity.ID, 0x5A, 0x00), ids(out));
        assertEquals(EntityType.registryId("minecraft:zombie"), addType(first(out, AddEntity.ID)));
        assertEquals(e.uuid(), addUuid(first(out, AddEntity.ID)));
        assertEquals(List.of(), drainIds(b), "다른 사람은 그대로");
        out.forEach(s -> s.body.release());

        // 상태 비트(칸 0)는 모든 엔티티에 공통이라 넘어간다
        e.setInvisible(true);
        e.tracker.sendChanges();
        List<Sent> data = drain(a);
        assertEquals(List.of(0), dataIndices(first(data, SetEntityData.ID)));
        data.forEach(s -> s.body.release());

        // 변장을 지우면 아이템으로 다시 스폰된다
        EntityViewRules.setAppearance(PLUGIN_A, e.uuid(), a, null, null, false);
        level.refreshViews(e, a, true);
        List<Sent> back = drain(a);
        assertEquals(EntityType.ITEM.id(), addType(first(back, AddEntity.ID)));
        assertTrue(ids(back).contains(SetEntityData.ID));
        back.forEach(s -> s.body.release());
        assertEquals(0, a.viewRewriter().appliedCount());
    }

    @Test
    void livingDisguisedAsNonLiving_dropsAttributesAndLivingData() {
        Player a = player(3, 3);
        var level = levelFor(a);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        EntityViewRules.setAppearance(PLUGIN_A, z.uuid(), null, as("minecraft:armor_stand"), null, false);
        // 갑옷 거치대는 살아 있는 것 — 속성은 보낸다
        level.addFreshEntity(z);
        List<Integer> asStand = drainIds(a);
        assertTrue(asStand.contains(UpdateAttributes.ID), asStand.toString());

        EntityViewRules.setAppearance(PLUGIN_A, z.uuid(), null, as("minecraft:snowball"), null, false);
        level.refreshViews(z, null, true);
        List<Integer> asBall = drainIds(a);
        assertFalse(asBall.contains(UpdateAttributes.ID), "살아 있지 않은 모습에 속성을 보내면 클라이언트가 끊긴다: " + asBall);
        assertTrue(asBall.contains(AddEntity.ID));

        z.setHealth(5);   // 체력 칸(9)은 눈덩이에 없다 → 보내지 않는다
        z.tracker.sendChanges();
        assertFalse(drainIds(a).contains(SetEntityData.ID));
    }

    @Test
    void playerDisguiseSendsProfileFirst_andCleansUpOnRemove() {
        Player a = player(3, 3);
        var level = levelFor(a);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        UUID[] profileUuid = new UUID[1];
        var prof = new Appearance.Profile("Steve", u -> {
            profileUuid[0] = u;
            return new PlayerInfoUpdate(List.of(new PlayerInfoUpdate.Entry(u, "Steve", 0, 0)));
        }, false);
        int playerType = EntityType.registryId("minecraft:player");
        EntityViewRules.setAppearance(PLUGIN_A, z.uuid(), null, new Appearance(playerType, "minecraft:player", 0, new int[0], null, prof, null), null, false);
        level.addFreshEntity(z);
        List<Sent> out = drain(a);
        List<Integer> seq = ids(out);
        assertEquals(List.of(0x00, PlayerInfoUpdate.ID, SetPlayerTeam.ID, AddEntity.ID), seq.subList(0, 4), "프로필 → 이름표 숨김 팀 → 스폰: " + seq);
        Sent add = first(out, AddEntity.ID);
        assertEquals(playerType, addType(add));
        assertEquals(profileUuid[0], addUuid(add), "가짜 프로필 uuid 로 스폰해야 그 스킨을 쓴다");
        assertNotEquals(z.uuid(), profileUuid[0]);
        int addAt = seq.indexOf(AddEntity.ID);
        assertEquals(SetEntityData.ID, seq.get(addAt + 1), "스킨 겉 레이어를 켠다: " + seq);
        assertEquals(List.of(17), dataIndices(out.get(addAt + 1)));
        out.forEach(s -> s.body.release());

        z.discard();
        assertEquals(List.of(RemoveEntities.ID, PlayerInfoRemove.ID, SetPlayerTeam.ID), drainIds(a));
        assertEquals(0, a.viewRewriter().appliedCount());
    }

    @Test
    void dimensionChangeDropsAppliedViews_andRemovesFakeProfiles() {
        Player a = player(3, 3);
        var level = levelFor(a);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        var prof = new Appearance.Profile("Alex", u -> new PlayerInfoUpdate(List.of(new PlayerInfoUpdate.Entry(u, "Alex", 0, 0))), true);
        a.send(new Respawn(0, "minecraft:overworld", 0, 0, -1, false, null, null, 0, (byte) 0));
        EntityViewRules.setAppearance(PLUGIN_A, z.uuid(), null,
                new Appearance(EntityType.registryId("minecraft:player"), "minecraft:player", 0, new int[0], null, prof, null), null, false);
        level.addFreshEntity(z);
        drainIds(a);
        assertEquals(1, a.viewRewriter().appliedCount());
        a.send(new Respawn(0, "minecraft:overworld", 0, 0, -1, false, null, null, 0, (byte) 0));
        assertEquals(List.of(Respawn.ID), drainIds(a), "같은 차원 리스폰은 엔티티가 남는다");
        assertEquals(1, a.viewRewriter().appliedCount());
        a.send(new Respawn(0, "minecraft:the_nether", 0, 0, -1, false, null, null, 0, (byte) 0));
        assertEquals(List.of(Respawn.ID, PlayerInfoRemove.ID), drainIds(a));
        assertEquals(0, a.viewRewriter().appliedCount());
    }

    @Test
    void metadataOverridesAreSentAtSpawn_andWinOverRealValues() {
        Player a = player(3, 3);
        var level = levelFor(a);
        Zombie z = new Zombie(level, level.nextEntityId());
        z.moveTo(0.5, -60, 0.5, 0, 0);
        // 칸 3(이름 보임)을 덮는다 — 덮는 패킷은 칸 3 하나만
        Packet over = new SetEntityData(z.id(), List.of(new SetEntityData.Item(3, SetEntityData.SERIALIZER_BOOLEAN, true)));
        EntityViewRules.setAppearance(PLUGIN_A, z.uuid(), null,
                new Appearance(EntityType.registryId("minecraft:creeper"), "minecraft:creeper", 0, new int[] {3}, eid -> over, null, null), null, false);
        level.addFreshEntity(z);
        List<Sent> out = drain(a);
        int addAt = ids(out).indexOf(AddEntity.ID);
        assertEquals(SetEntityData.ID, out.get(addAt + 1).id, "스폰 바로 뒤에 덮어쓰기");
        assertEquals(List.of(3), dataIndices(out.get(addAt + 1)));
        out.forEach(s -> s.body.release());

        z.setCustomNameVisible(false);   // 진짜 값이 바뀌어도 덮은 칸은 보내지 않는다
        z.setSilent(true);
        z.tracker.sendChanges();
        List<Sent> ch = drain(a);
        List<Integer> idx = dataIndices(first(ch, SetEntityData.ID));
        assertTrue(idx.contains(4), idx.toString());
        assertFalse(idx.contains(3), "덮은 칸: " + idx);
        ch.forEach(s -> s.body.release());
    }

    @Test
    void viewerRulesVanishWhenViewerLeaves_targetHidesSurvive() {
        Player a = player(3, 3), b = player(4, 4);
        UUID mob = UUID.randomUUID();
        EntityViewRules.hide(PLUGIN_A, a, mob, null, false);
        EntityViewRules.hide(PLUGIN_A, a, b.uuid(), null, true);
        EntityViewRules.setAppearance(PLUGIN_A, b.uuid(), null, as("minecraft:pig"), null, true);
        EntityViewRules.hide(PLUGIN_A, b, mob, null, false);
        a.markDisconnected();
        assertTrue(EntityViewRules.canSee(a, mob), "나간 사람이 건 규칙은 없어진다");
        assertFalse(EntityViewRules.canSee(b, mob), "다른 사람 규칙은 남는다");
        b.markDisconnected();
        assertNull(EntityViewRules.globalAppearance(b.uuid()), "나간 플레이어의 변장은 지운다");
        assertEquals(0, EntityViewRules.targetCount());
    }

    @Test
    void sharedPrefixFollowsClassLevels() {
        int zombie = EntityType.registryId("minecraft:zombie"), creeper = EntityType.registryId("minecraft:creeper"),
                stand = EntityType.registryId("minecraft:armor_stand"), item = EntityType.registryId("minecraft:item");
        int player = EntityKinds.PLAYER_TYPE_ID;
        assertEquals(16, EntityKinds.sharedPrefix(zombie, EntityKinds.MOB, creeper, EntityKinds.MOB), "몹끼리: 몹 비트(15)까지");
        assertEquals(15, EntityKinds.sharedPrefix(player, EntityKinds.PLAYER, zombie, EntityKinds.MOB), "플레이어 ↔ 몹: 살아 있는 것 칸(14)까지");
        assertEquals(15, EntityKinds.sharedPrefix(stand, EntityKinds.LIVING, zombie, EntityKinds.MOB));
        assertEquals(8, EntityKinds.sharedPrefix(item, EntityKinds.NON_LIVING, zombie, EntityKinds.MOB));
        assertEquals(Integer.MAX_VALUE, EntityKinds.sharedPrefix(player, EntityKinds.PLAYER, player, EntityKinds.PLAYER));
        assertEquals(EntityKinds.NON_LIVING, EntityKinds.kindOf("minecraft:block_display"));
        assertEquals(EntityKinds.MOB, EntityKinds.kindOf("minecraft:ender_dragon"));
        // 모든 종류가 레지스트리에 있다(오타 확인)
        for (String n : EntityKinds.NON_LIVING_TYPES) {
            assertTrue(EntityType.registryId(n) >= 0, n);
        }
    }
}
