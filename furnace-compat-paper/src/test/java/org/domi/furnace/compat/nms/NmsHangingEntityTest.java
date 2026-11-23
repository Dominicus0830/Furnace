package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.Rotation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 걸이 엔티티의 방향·돌림(Axiom 소환·조작 길): {@code HangingEntity#getDirection}·{@code rotate(Rotation)}, {@code ItemFrame#getRotation}·{@code setRotation(int)}.
 * 정답은 Paper 1.21.1 jar 의 바이트코드(javap) — rotate 는 수평 방향만 돌리고 각도는 그대로 둔 채 wrapDegrees(yRot)+돌림을 돌려준다.
 * 월드 밖 엔티티(create → load)의 값과, 넣은 뒤 서버 엔티티의 값이 같은지 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsHangingEntityTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Frames");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    /** 서쪽을 보는 액자(동쪽 블록에 붙음)의 저장 태그: 돌 아이템, 돌림 3. */
    CompoundTag frameTag(int x, int facing, boolean withItem) {
        CompoundTag tag = new CompoundTag();
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x + 0.5));
        pos.add(DoubleTag.valueOf(at.y() + 0.5));
        pos.add(DoubleTag.valueOf(at.z() + 0.5));
        tag.put("Pos", pos);
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(12.0F));   // 저장 각도는 setDirection 이 덮는다
        rot.add(FloatTag.valueOf(7.0F));
        tag.put("Rotation", rot);
        tag.putInt("TileX", x);
        tag.putInt("TileY", at.y());
        tag.putInt("TileZ", at.z());
        tag.putByte("Facing", (byte) facing);
        if (withItem) {
            CompoundTag item = new CompoundTag();
            item.putString("id", "minecraft:stone");
            item.putInt("count", 1);
            tag.put("Item", item);
        }
        tag.putByte("ItemRotation", (byte) 3);
        return tag;
    }

    org.domi.furnace.server.entity.ItemFrame serverFrame(Entity e) {
        return on(() -> org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) e.getBukkitEntity()).furnaceHandle(),
                org.domi.furnace.server.entity.ItemFrame.class));
    }

    @Test
    void detachedFrameFollowsTheLoadedTagAndReplaysOnAdd() {
        int x = at.x();
        on(() -> { world.getBlockAt(x + 1, at.y(), at.z()).setType(Material.STONE); return null; });
        Entity e = EntityType.byString("item_frame").orElseThrow().create(level);
        ItemFrame f = assertInstanceOf(ItemFrame.class, e);
        assertEquals(Direction.SOUTH, f.getDirection(), "바닐라 필드 기본값");
        f.load(frameTag(x, 4, true));
        assertEquals(Direction.WEST, f.getDirection());
        assertEquals(90.0F, f.getYRot(), "ItemFrame#setDirection: 수평이면 yRot = 2D 값 × 90");
        assertEquals(0.0F, f.getXRot());
        assertEquals(3, f.getRotation(), "아이템이 있으면 ItemRotation");
        // rotate: 서쪽 → 시계 90 → 북쪽, 각도는 그대로라 wrapDegrees(90) + 270
        assertEquals(360.0F, f.rotate(Rotation.CLOCKWISE_90));
        assertEquals(Direction.NORTH, f.getDirection());
        assertEquals(90.0F, f.getYRot(), "rotate 는 각도를 바꾸지 않는다");
        f.setRotation(13);
        assertEquals(5, f.getRotation(), "8 로 나눈 나머지");
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        try {
            var s = serverFrame(e);
            assertEquals(org.domi.furnace.server.block.Dir.NORTH, (int) on(s::direction), "넣을 때 기록한 rotate 를 재생");
            assertEquals(5, (int) on(s::rotation));
            assertEquals(Direction.NORTH, on(f::getDirection), "월드에 들어간 뒤는 서버 값");
            assertEquals(5, (int) on(f::getRotation));
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    @Test
    void detachedFrameWithoutItemKeepsRotationZeroAndVerticalFacing() {
        ItemFrame f = (ItemFrame) EntityType.byString("glow_item_frame").orElseThrow().create(level);
        f.load(frameTag(at.x(), 1, false));
        assertEquals(0, f.getRotation(), "빈 액자는 ItemRotation 을 읽지 않는다");
        assertEquals(Direction.UP, f.getDirection());
        assertEquals(-90.0F, f.getXRot(), "위를 보면 xRot = −90");
        assertEquals(0.0F, f.getYRot());
        assertEquals(wrap(0.0F) + 180.0F, f.rotate(Rotation.CLOCKWISE_180));
        assertEquals(Direction.UP, f.getDirection(), "위·아래는 돌리지 않는다");
    }

    @Test
    void paintingReadsItsTwoDimensionalFacing() {
        HangingEntity p = (HangingEntity) EntityType.byString("painting").orElseThrow().create(level);
        CompoundTag tag = frameTag(at.x(), 0, false);
        tag.remove("Facing");
        tag.putByte("facing", (byte) 3);   // 2D 3 = 동
        p.load(tag);
        assertEquals(Direction.EAST, p.getDirection());
        assertEquals(270.0F, p.getYRot());
        assertEquals(7.0F, p.getXRot(), "HangingEntity#setDirection 은 xRot 을 건드리지 않는다");
        assertEquals(wrap(270.0F) + 90.0F, p.rotate(Rotation.COUNTERCLOCKWISE_90));
        assertEquals(Direction.NORTH, p.getDirection());
    }

    @Test
    void liveFrameRotatesOnTheServer() {
        int x = at.x() + 4;
        on(() -> { world.getBlockAt(x, at.y(), at.z() + 1).setType(Material.STONE); return null; });
        Entity e = EntityType.byString("item_frame").orElseThrow().create(level);
        e.load(frameTag(x, 2, true));   // 북쪽을 봄(남쪽 블록에 붙음)
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        ItemFrame f = (ItemFrame) e;
        try {
            assertEquals(Direction.NORTH, on(f::getDirection));
            float yRot = on(f::getYRot);
            assertEquals(wrap(yRot) + 180.0F, on(() -> f.rotate(Rotation.CLOCKWISE_180)));
            assertEquals(Direction.SOUTH, on(f::getDirection));
            assertEquals(yRot, on(f::getYRot), "rotate 는 각도를 바꾸지 않는다");
            on(() -> { f.setRotation(6); return null; });
            assertEquals(6, (int) on(f::getRotation));
            assertEquals(6, (int) on(serverFrame(e)::rotation));
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    private static float wrap(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }
}
