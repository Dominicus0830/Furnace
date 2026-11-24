package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** NMS shim 5번 묶음 앞부분: 엔티티·플레이어 모습과 탑승 읽기(Axiom 엔티티 조작·voxy 먼 플레이어 모습이 부르는 것). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityLookTest {

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    CraftWorld world;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Looker");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        world = (CraftWorld) bukkit.getWorld();
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    void command(int action) {
        int id = bukkit.getEntityId();
        tp.sendPacket(0x25, buf -> {   // player_command: 엔티티 번호, 동작, 점프 세기
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, id);
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, action);
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, 0);
        });
        server.tick(2);
    }

    @Test
    void playerLookFromAnyThread() throws Exception {
        ServerPlayer p = bukkit.getHandle();
        var loc = tp.player().location();
        assertEquals((int) Math.floor(loc.x()), p.getBlockX());
        assertEquals((int) Math.floor(loc.z()), p.getBlockZ());
        assertFalse(p.isCrouching());
        assertFalse(p.isSwimming());
        assertFalse(p.isInvisible());
        assertFalse(p.isFallFlying());
        assertFalse(p.isSpectator());
        assertFalse(p.isPassenger());
        assertNull(p.getVehicle());
        assertFalse(p.hasDisconnected());
        assertTrue(p.hasPermissions(0));
        assertFalse(p.hasPermissions(1), "op 가 아니면 권한 단계 0");
        assertEquals("Looker", p.getName().getString());
        assertSame(net.minecraft.core.RegistryAccess.SERVER, p.registryAccess());
        command(0);   // PRESS_SHIFT_KEY — 다음 틱에 웅크림 자세
        assertTrue(p.isCrouching(), "시험 스레드(틱 스레드 아님)에서도 플레이어는 최근 값");
        command(1);   // RELEASE_SHIFT_KEY
        assertFalse(p.isCrouching());
        on(() -> {
            bukkit.setGameMode(org.bukkit.GameMode.SPECTATOR);
            return null;
        });
        server.tick(1);
        assertTrue(p.isSpectator());
        on(() -> {
            bukkit.setGameMode(org.bukkit.GameMode.SURVIVAL);
            return null;
        });
        server.tick(1);
        assertFalse(p.isSpectator());
    }

    /** 몸 크기(바닐라 getBbWidth·getBbHeight·getEyeHeight): 플레이어 자세, 월드의 좀비(아기 칸), 아직 월드에 없는 엔티티(기록 재생 — 아기·작은 갑옷 거치대). */
    @Test
    void dimensionsFollowPoseAndData() {
        ServerPlayer p = bukkit.getHandle();
        assertEquals(0.6F, p.getBbWidth());
        assertEquals(1.8F, p.getBbHeight());
        assertEquals(1.62F, p.getEyeHeight(), "서기 — 시험 스레드에서도 플레이어는 최근 값");
        command(0);   // 웅크림
        assertEquals(1.5F, p.getBbHeight());
        assertEquals(1.27F, p.getEyeHeight());
        command(1);
        assertEquals(1.62F, p.getEyeHeight());

        CraftEntity zombie = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.ZOMBIE));
        Entity z = zombie.getHandle();
        on(() -> {
            assertEquals(0.6F, z.getBbWidth());
            assertEquals(1.95F, z.getBbHeight());
            assertEquals(1.74F, z.getEyeHeight());
            org.domi.furnace.api.internal.Internals.get().entityNow(zombie.furnaceHandle(), org.domi.furnace.server.entity.Zombie.class).setBaby(true);   // 서버 좀비의 아기 칸(onSyncedDataUpdated → refreshDimensions)
            assertEquals(0.3F, z.getBbWidth());
            assertEquals(0.975F, z.getBbHeight());
            assertEquals(0.93F, z.getEyeHeight(), "아기 좀비(Zombie.BABY_DIMENSIONS)");
            zombie.remove();
            return null;
        });

        Level level = world.getHandle();
        Entity fresh = EntityType.ZOMBIE.create(level);
        assertEquals(1.74F, fresh.getEyeHeight(), "월드에 없는 엔티티는 바닐라 생성자의 크기");
        var baby = new net.minecraft.nbt.CompoundTag();
        baby.putBoolean("IsBaby", true);
        fresh.load(baby);
        assertEquals(0.93F, fresh.getEyeHeight(), "load 가 아기로 바꾸면 크기도 따라 바뀐다(서버 규칙 재생)");
        assertEquals(0.975F, fresh.getBbHeight());
        Entity stand = EntityType.ARMOR_STAND.create(level);
        assertEquals(1.975F, stand.getBbHeight());
        var small = new net.minecraft.nbt.CompoundTag();
        small.putBoolean("Small", true);
        stand.load(small);
        assertEquals(0.5F, stand.getBbWidth());
        assertEquals(0.9875F, stand.getBbHeight());
        assertEquals(0.88875F, stand.getEyeHeight());
        // 자세 칸: 워든이 땅에서 나오는 자세면 키 1.0·눈 0.85(Warden.getDefaultDimensions)
        Entity warden = EntityType.WARDEN.create(level);
        assertEquals(2.9F, warden.getBbHeight());
        // 플러그인처럼 보호된 칸 상수를 리플렉션으로 꺼내 쓴다(바닐라 setPose 는 shim 에 아직 없다)
        @SuppressWarnings("unchecked")
        var pose = (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.world.entity.Pose>) poseAccessor();
        warden.getEntityData().set(pose, net.minecraft.world.entity.Pose.EMERGING);
        assertEquals(1.0F, warden.getBbHeight());
        assertEquals(0.85F, warden.getEyeHeight());
        // 크기 칸: 저장의 Size 2 는 크기 3 — 0.52·3 너비·키, 눈 0.325·3
        Entity slime = EntityType.SLIME.create(level);
        var size = new net.minecraft.nbt.CompoundTag();
        size.putInt("Size", 2);
        slime.load(size);
        assertEquals(0.52F * 3, slime.getBbWidth());
        assertEquals(0.52F * 3, slime.getBbHeight());
        assertEquals(0.325F * 3, slime.getEyeHeight());
    }

    static Object poseAccessor() {
        try {
            var f = Entity.class.getDeclaredField("DATA_POSE");
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void ridingOnTheOwnerAndFromAnotherThread() throws Exception {
        CraftEntity boat = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.BOAT));
        CraftEntity pig = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.PIG));
        CraftEntity chick = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.CHICKEN));
        Entity b = boat.getHandle(), p = pig.getHandle(), c = chick.getHandle();
        on(() -> {
            assertTrue(boat.addPassenger(pig));
            assertTrue(pig.addPassenger(chick));
            assertTrue(b.hasPassenger(e -> e == p));
            assertFalse(b.hasPassenger(e -> e == c), "바로 탄 승객만");
            assertTrue(p.isPassenger());
            assertSame(b, p.getVehicle());
            assertSame(p, c.getVehicle());
            assertEquals(List.of(b, p, c), b.getSelfAndPassengers().toList());
            List<Entity> indirect = new java.util.ArrayList<>();
            b.getIndirectPassengers().forEach(indirect::add);
            assertEquals(List.of(p, c), indirect);
            assertFalse(p.isInvisible());
            assertFalse(p.isCrouching());
            assertEquals(b.getBlockY(), (int) Math.floor(b.getY()));
            return null;
        });
        // 탄 엔티티의 움직임은 첫 틱 뒤 매 틱 같다(바닐라 rideTick 이 0 으로 되돌린 뒤 중력 한 번) — 그 뒤 값을 기준으로
        server.tick(3);
        String owner = on(() -> p.getKnownMovement().toString());
        // 다른 스레드(틱 스레드 아님): 주인 region 에 물어 같은 답
        var seen = new AtomicReference<Object>();
        var t = new Thread(() -> {
            try {
                seen.set(List.of(p.getVehicle() == b, b.getSelfAndPassengers().count(), p.getKnownMovement().toString()));
            } catch (Throwable e) {
                seen.set(e);
            }
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) {
            server.tick(1);
            t.join(50);
        }
        assertEquals(List.of(true, 3L, owner), seen.get());
        on(() -> {
            chick.remove();
            pig.remove();
            boat.remove();
            return null;
        });
    }

    /** 서버가 저장한 속도(바닐라 getDeltaMovement 와 같은 값, NBT "Motion") — 같은 주인 작업 안에서 읽는다. */
    static String motion(Entity e) {
        var tag = new net.minecraft.nbt.CompoundTag();
        assertTrue(e.save(tag));
        var m = tag.getList("Motion", 6);
        return new net.minecraft.world.phys.Vec3(m.getDouble(0), m.getDouble(1), m.getDouble(2)).toString();
    }

    @Test
    void sharedFlagsAndKnownMovementMatchVanilla() {
        ServerPlayer me = bukkit.getHandle();
        // 공유 플래그가 참인 경우: 투명 효과 → 바닐라 updateInvisibilityStatus 가 플래그 5 를 켠다(다음 틱)
        CraftEntity pig = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y() + 3, at.z() + 0.5), org.bukkit.entity.EntityType.PIG));
        Entity p = pig.getHandle();
        var invisible = new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 400, 0);
        on(() -> ((org.bukkit.entity.LivingEntity) pig).addPotionEffect(invisible));
        server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect give Looker minecraft:invisibility 20 0 true"));
        server.tick(3);
        assertTrue(on(p::isInvisible));
        assertTrue(me.isInvisible(), "플레이어도 공유 플래그 5(아무 스레드에서 최근 값)");
        // 조종하는 플레이어가 없으면 지금 속도 — 공중에서 떨어지는 돼지
        var pigSeen = on(() -> List.of(p.getKnownMovement().toString(), motion(p)));
        assertEquals(pigSeen.get(1), pigSeen.get(0));
        assertFalse(new net.minecraft.world.phys.Vec3(0, 0, 0).toString().equals(pigSeen.get(0)), "떨어지는 중이라 0 이 아니다");
        // 배를 조종하는 플레이어가 있으면 배의 움직임은 그 플레이어가 알린 움직임(배 자신의 속도가 아니다)
        var loc = bukkit.getLocation();
        tp.moveTo(loc.getX() + 0.3, loc.getY(), loc.getZ(), true);   // 플레이어가 알린 움직임을 0 이 아니게(마지막 이동 패킷 거리)
        server.tick(2);
        CraftEntity boat = on(() -> (CraftEntity) world.spawnEntity(loc, org.bukkit.entity.EntityType.BOAT));
        Entity b = boat.getHandle();
        var seen = on(() -> {
            assertTrue(boat.addPassenger(bukkit));
            return List.of(b.getKnownMovement().toString(), me.getKnownMovement().toString(), motion(b), me.getVehicle() == b, me.isPassenger());
        });
        assertEquals(seen.get(1), seen.get(0));
        assertFalse(seen.get(2).equals(seen.get(0)), "배 자신의 속도가 아니다: " + seen);
        assertEquals(List.of(true, true), seen.subList(3, 5));
        on(() -> {
            boat.eject();
            boat.remove();
            pig.remove();
            return null;
        });
        server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect clear Looker minecraft:invisibility"));
        server.tick(5);
        assertFalse(me.isInvisible(), "효과를 지우면 플래그도 꺼진다");
    }

    @Test
    void wrongThreadIsNotHiddenByTheLastLook() {
        CraftEntity cow = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.COW));
        Entity c = cow.getHandle();
        assertFalse(on(c::isInvisible));   // 주인 스레드에서 한 번 읽어 둔다
        // 전역 스레드는 다른 region 을 기다릴 수 없다 — 읽어 둔 값을 조용히 주지 않고 ThreadOwnershipException
        var err = assertThrows(IllegalStateException.class, () -> server.onGlobal(c::isInvisible));
        assertTrue(err instanceof org.domi.furnace.api.concurrent.ThreadOwnershipException
                || err.getCause() instanceof org.domi.furnace.api.concurrent.ThreadOwnershipException, String.valueOf(err));
        on(() -> {
            cow.remove();
            return null;
        });
    }

    @Test
    void smallVanillaShapes() {
        assertEquals("minecraft:player", EntityType.getKey(EntityType.PLAYER).toString());
        assertFalse(EntityType.PLAYER.canSerialize());
        assertFalse(EntityType.byString("lightning_bolt").orElseThrow().canSerialize());
        assertTrue(EntityType.byString("pig").orElseThrow().canSerialize());
        assertTrue(Entity.RemovalReason.KILLED.shouldDestroy());
        assertFalse(Entity.RemovalReason.KILLED.shouldSave());
        assertTrue(Entity.RemovalReason.UNLOADED_TO_CHUNK.shouldSave());
        assertFalse(Entity.RemovalReason.CHANGED_DIMENSION.shouldDestroy());
        assertTrue(Level.isInSpawnableBounds(new BlockPos(29_999_999, 19_999_999, -30_000_000)));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(30_000_000, 0, 0)));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(0, -20_000_001, 0)));
        var age = net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_7;
        assertEquals(0, age.min);
        assertEquals(7, age.max);
        var set = net.minecraft.core.HolderSet.direct(Blocks.STONE.builtInRegistryHolder());
        assertTrue(Blocks.STONE.defaultBlockState().is(set));
        assertFalse(Blocks.DIRT.defaultBlockState().is(set));
        assertSame(world.getHandle().getChunkIfLoaded(at.x() >> 4, at.z() >> 4),
                world.getHandle().getChunkIfLoaded(new BlockPos(at.x(), at.y(), at.z())));
    }
}
