// 앵무새 어깨 앉기(LandOnOwnersShoulderGoal)·플레이어 어깨 몹 NBT 저장/복원·내려놓기 시험.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ParrotShoulderTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static PlayerSleepingTest.Lvl floor() {
        var level = new PlayerSleepingTest.Lvl();
        for (int x = -8; x < 16; x++) for (int z = -8; z < 16; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:grass_block"), 3);
        return level;
    }

    private static Parrot tameParrot(PlayerSleepingTest.Lvl level, Player owner) {
        var parrot = new Parrot(level, level.nextEntityId());
        parrot.moveTo(owner.x(), owner.y(), owner.z(), 0, 0);
        level.addFreshEntity(parrot);
        parrot.setTame(true, false);
        parrot.setOwnerUUID(owner.uuid());
        return parrot;
    }

    private static org.domi.furnace.server.entity.ai.Goal goal(Parrot p) {
        return p.goalSelector.getAvailableGoals().stream().map(w -> w.getGoal()).filter(g -> g.getClass().getSimpleName().equals("LandOnOwnersShoulderGoal")).findFirst().orElseThrow();
    }

    @Test
    void goalTableHasLandOnShoulder() {
        var level = floor();
        assertTrue(MobGoalTableTest.table(new Parrot(level, level.nextEntityId()).goalSelector).contains("3 LandOnOwnersShoulderGoal"));
    }

    @Test
    void parrotLandsOnShoulderAfterCooldownAndDropsOffAfterTwentyTicks() {
        var level = floor();
        var player = PlayerSleepingTest.player(level, 2.5, -60, 2.5);
        var parrot = tameParrot(level, player);
        var g = goal(parrot);
        assertFalse(g.canUse(), "쿨다운 100틱 전");
        parrot.setNoAi(true);   // 목표가 날려 보내지 않도록 쿨다운만 센다
        for (int i = 0; i < 101; i++) parrot.tick();
        parrot.moveTo(player.x(), player.y(), player.z(), 0, 0);
        assertTrue(g.canUse());
        g.start();
        g.tick();
        assertFalse(parrot.isAlive(), "어깨에 앉으면 엔티티는 사라진다");
        assertEquals("minecraft:parrot", player.shoulderLeft().tags().get("id"));
        assertTrue(player.shoulderRight().tags().isEmpty());

        level.gameTime = 20;
        player.removeEntitiesOnShoulder();
        assertFalse(player.shoulderLeft().tags().isEmpty(), "앉힌 지 20틱이 안 지났다");
        level.gameTime = 21;
        player.removeEntitiesOnShoulder();
        assertTrue(player.shoulderLeft().tags().isEmpty());
        var back = level.getEntitiesOfClass(Parrot.class, player.boundingBox().inflate(3.0), x -> x.isAlive());
        assertEquals(1, back.size());
        assertEquals(-59.3, back.get(0).y(), 1.0E-6, "플레이어 y + 0.7");
        assertEquals(player.uuid(), back.get(0).getOwnerUUID());
    }

    @Test
    void twoShouldersThenRefuseAndAirborneRefuses() {
        var level = floor();
        var player = PlayerSleepingTest.player(level, 2.5, -60, 2.5);
        var a = new NbtCompound(); a.putString("id", "minecraft:parrot");
        var b = new NbtCompound(); b.putString("id", "minecraft:parrot"); b.putInt("Variant", 3);
        var c = new NbtCompound(); c.putString("id", "minecraft:parrot");
        assertTrue(player.setEntityOnShoulder(a));
        assertTrue(player.setEntityOnShoulder(b));
        assertSame(a, player.shoulderLeft());
        assertSame(b, player.shoulderRight());
        assertFalse(player.setEntityOnShoulder(c), "양 어깨가 찼다");
        var q = PlayerSleepingTest.player(level, 5.5, -60, 5.5);
        q.applyMove(5.5, -58, 5.5, 0, 0, false);
        assertFalse(q.setEntityOnShoulder(c), "공중이면 앉지 못한다");
    }

    @Test
    void shoulderNbtRoundTrip() {
        var level = floor();
        var player = PlayerSleepingTest.player(level, 2.5, -60, 2.5);
        var a = new NbtCompound(); a.putString("id", "minecraft:parrot"); a.putInt("Variant", 2);
        player.setEntityOnShoulder(a);
        var out = new NbtCompound();
        player.saveShoulders(out);
        assertTrue(out.contains("ShoulderEntityLeft"));
        assertFalse(out.contains("ShoulderEntityRight"));
        var other = PlayerSleepingTest.player(level, 4.5, -60, 4.5);
        other.loadShoulders(out);
        assertEquals(2, other.shoulderLeft().getIntOr0("Variant"));
        assertTrue(other.shoulderRight().tags().isEmpty());
    }

    @Test
    void hurtDropsShoulderParrot() {
        var level = floor();
        var player = PlayerSleepingTest.player(level, 2.5, -60, 2.5);
        var a = new NbtCompound(); a.putString("id", "minecraft:parrot");
        player.setEntityOnShoulder(a);
        level.gameTime = 40;
        player.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.byKey("out_of_world"), 1.0F);   // 스폰 직후 무적을 넘는 피해
        assertTrue(player.shoulderLeft().tags().isEmpty());
        assertEquals(1, level.getEntitiesOfClass(Parrot.class, player.boundingBox().inflate(3.0), x -> true).size());
    }

    @Test
    void compoundSerializerEncodesNamelessNbt() {
        var c = new NbtCompound();
        c.putString("id", "x");
        var pkt = new org.domi.furnace.server.net.protocol.play.SetEntityData(5, java.util.List.of(
                new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(Player.DATA_SHOULDER_LEFT, org.domi.furnace.server.net.protocol.play.SetEntityData.SERIALIZER_COMPOUND, c)));
        var buf = io.netty.buffer.Unpooled.buffer();
        pkt.write(buf);
        byte[] b = new byte[buf.readableBytes()];
        buf.readBytes(b);
        // 엔티티 5, 인덱스 19, 직렬화기 16, TAG_Compound(10), TAG_String(8) 이름 "id", 값 "x", TAG_End, 목록 끝 0xFF
        assertArrayEquals(new byte[] {5, 19, 16, 10, 8, 0, 2, 'i', 'd', 0, 1, 'x', 0, (byte) 0xFF}, b);
    }
}
