package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.UUID;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-212 다 묶음 1: 플러그인이 상속해 바닐라 생성자로 만드는 "월드에 없는 플레이어"(FAWE {@code PaperweightFakePlayer} 모양 — 프로필 "[WorldEdit]",
 * {@code position()} 재정의). Paper 처럼 서버에 플레이어가 있고 Bukkit 쪽은 그 객체를 {@code getHandle()} 로 주는 CraftPlayer 인데, 월드·접속자
 * 목록에는 없다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsDetachedPlayerTest {

    static final GameProfile PROFILE = new GameProfile(UUID.nameUUIDFromBytes("worldedit".getBytes()), "[WorldEdit]");
    static final ClientInformation INFO = new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.LEFT, false, false);

    /** FAWE 가짜 플레이어 모양(재정의는 그 일부). */
    static final class FakePlayer extends ServerPlayer {
        FakePlayer(ServerLevel level, GameProfile profile) { super(level.getServer(), level, profile, INFO); }

        @Override
        public Vec3 position() { return new Vec3(0, 0, 0); }
    }

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Builder");
        server.tick(3);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    @Test
    void standsAtTheSharedSpawnAndIsTheHandleOfItsCraftPlayer() {
        FakePlayer fake = new FakePlayer(level, PROFILE);
        CraftPlayer cp = fake.getBukkitEntity();
        assertSame(fake, cp.getHandle());
        assertSame(cp, Compat.player(cp.furnaceHandle()));   // 이벤트의 플레이어 칸도 같은 객체
        assertEquals("[WorldEdit]", cp.getName());
        assertEquals(PROFILE.getId(), cp.getUniqueId());
        assertEquals(PROFILE.getId(), fake.getUUID());
        assertFalse(cp.isOnline());
        assertFalse(fake.isRemoved());
        assertNull(fake.connection);
        assertSame(level, fake.level());
        assertNotEquals(Compat.player(tp.player()).getEntityId(), fake.getId());

        var spawn = server.internals().world().defaultSpawn();   // 바닐라 Player 생성자: 칸 가운데·한 칸 위·스폰 각도·숙임 0
        assertEquals(spawn.x() + 0.5, fake.getX());
        assertEquals(spawn.y() + 1, fake.getY());
        assertEquals(spawn.z() + 0.5, fake.getZ());
        assertEquals(spawn.yaw(), fake.getYRot());
        assertEquals(0.0F, fake.getXRot());
        assertEquals(new Vec3(0, 0, 0), fake.position());   // 하위 클래스 재정의가 그대로 불린다
    }

    @Test
    void isNotInTheWorldOrThePlayerList() {
        int online = Bukkit.getOnlinePlayers().size();
        FakePlayer fake = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "Ghost"));
        assertEquals(online, Bukkit.getOnlinePlayers().size());
        assertNull(Bukkit.getPlayer(fake.getUUID()));
        assertNull(Bukkit.getPlayerExact("Ghost"));
        assertEquals(online, world.getPlayers().size());
        server.tick(2);
        assertEquals(online, server.api().onlinePlayers().size());
        assertTrue(tp.player().isOnline());
    }

    @Test
    void absMoveToMovesTheServerPlayerLikeVanilla() {
        FakePlayer fake = new FakePlayer(level, PROFILE);
        fake.absMoveTo(10.5, 70, -3.25, 450.0F, 120.0F);   // 바닐라 absRotateTo: yRot % 360, xRot 는 ±90 으로 자른 뒤 % 360
        assertEquals(10.5, fake.getX());
        assertEquals(70.0, fake.getY());
        assertEquals(-3.25, fake.getZ());
        assertEquals(90.0F, fake.getYRot());
        assertEquals(90.0F, fake.getXRot());
        var loc = fake.getBukkitEntity().getLocation();   // Bukkit 쪽도 같은 서버 플레이어를 본다
        assertEquals(10.5, loc.getX());
        assertEquals(-3.25, loc.getZ());
        fake.absMoveTo(4.0E7, 0, -4.0E7, 0, 0);
        assertEquals(3.0E7, fake.getX());
        assertEquals(-3.0E7, fake.getZ());
    }

    @Test
    void keepsTheProfileProperties() {
        GameProfile skin = new GameProfile(UUID.randomUUID(), "Skinned");
        skin.getProperties().put("textures", new Property("textures", "dmFsdWU=", "c2ln"));
        FakePlayer fake = new FakePlayer(level, skin);
        var props = fake.getBukkitEntity().getPlayerProfile().getProperties();
        assertEquals(1, props.size());
        var q = props.iterator().next();
        assertEquals("textures", q.getName());
        assertEquals("dmFsdWU=", q.getValue());
        assertEquals("c2ln", q.getSignature());
    }

    @Test
    void anOnlinePlayerIsStillMovedThroughBukkit() {
        ServerPlayer online = Compat.player(tp.player()) instanceof CraftPlayer cp ? cp.getHandle() : null;
        assertThrows(UnsupportedNmsCall.class, () -> online.absMoveTo(0, 80, 0, 0, 0));
        assertNotNull(online.connection);
    }

    @Test
    void abilitiesAndGameModeHoldValuesLikeVanilla() {
        FakePlayer fake = new FakePlayer(level, PROFILE);
        var a = fake.getAbilities();   // 바닐라: 값만 드는 객체, 플레이어마다 같은 객체, 서바이벌 기본값
        assertSame(a, fake.getAbilities());
        assertFalse(a.instabuild);
        assertTrue(a.mayBuild);
        assertEquals(0.05F, a.getFlyingSpeed());
        a.instabuild = true;   // 블록 부수기를 흉내 내는 플러그인의 흔한 꼴
        a.setFlyingSpeed(0.2F);
        assertTrue(fake.getAbilities().instabuild);
        assertEquals(0.2F, fake.getAbilities().getFlyingSpeed());
        assertFalse(fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL));   // 같은 모드: 거짓(Paper 와 같다)
        assertThrows(UnsupportedNmsCall.class, () -> fake.setGameMode(net.minecraft.world.level.GameType.CREATIVE));   // Paper: 연결이 없어 NPE
        assertThrows(UnsupportedNmsCall.class, fake::onUpdateAbilities);
        assertTrue(fake.getAbilities().instabuild);
    }
}
