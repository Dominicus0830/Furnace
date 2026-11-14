package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.math.Rotations;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Entry;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Direction;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * 엔티티 메타데이터(set_entity_data) shim ↔ Furnace 다리(2026-11-14 ProtocolLib 점검). 예전에는 플러그인이 ProtocolLib·NMS 로 보낸
 * 메타데이터 패킷(홀로그램 이름표·발광·투명)이 "타입 레코드 종류라 shim 변환이 없다" 로 조용히 버려졌고, 서버가 보내는 메타데이터는
 * ProtocolLib 리스너에 보이지 않았다.
 */
class EntityDataBridgeTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("ed.jar"), "name: Ed\nversion: '1'\nmain: ed.P\napi-version: '1.21'\n",
                Map.of("ed.P", "package ed; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static BlockData block(String id) { return Furnace.server().blockTypes().get(id).orElseThrow().defaultData(); }

    /** 다리가 맡는 값 종류마다 하나(바닐라 칸 번호와 상관없이 칸 0..n). */
    private static List<Entry> everyBridgedType() {
        List<Entry> e = new ArrayList<>();
        int i = 0;
        e.add(new Entry(i++, Value.of(EntityDataType.BYTE, (byte) 0x60)));
        e.add(new Entry(i++, Value.of(EntityDataType.INT, 300)));
        e.add(new Entry(i++, Value.of(EntityDataType.LONG, 1L << 40)));
        e.add(new Entry(i++, Value.of(EntityDataType.FLOAT, 0.75f)));
        e.add(new Entry(i++, Value.of(EntityDataType.STRING, "문자열")));
        e.add(new Entry(i++, Value.of(EntityDataType.COMPONENT, Text.text("이름"))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_COMPONENT, Optional.of(Text.text("홀로그램")))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_COMPONENT, Optional.<Text>empty())));
        e.add(new Entry(i++, Value.of(EntityDataType.ITEM_STACK, new ItemStack("minecraft:diamond_sword", 1))));
        e.add(new Entry(i++, Value.of(EntityDataType.ITEM_STACK, ItemStack.EMPTY)));
        e.add(new Entry(i++, Value.of(EntityDataType.BOOLEAN, true)));
        e.add(new Entry(i++, Value.of(EntityDataType.ROTATIONS, new Rotations(10f, -20f, 350f))));
        e.add(new Entry(i++, Value.of(EntityDataType.BLOCK_POS, new BlockPos(1, -64, 3))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_BLOCK_POS, Optional.of(new BlockPos(-5, 70, 9)))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_BLOCK_POS, Optional.<BlockPos>empty())));
        e.add(new Entry(i++, Value.of(EntityDataType.DIRECTION, Direction.WEST)));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_UUID, Optional.of(new UUID(1, 2)))));
        e.add(new Entry(i++, Value.of(EntityDataType.BLOCK_STATE, block("minecraft:oak_stairs"))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.of(block("minecraft:stone")))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.<BlockData>empty())));
        e.add(new Entry(i++, Value.of(EntityDataType.COMPOUND_TAG, new NbtCompound().putInt("a", 5).put("s", "t"))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_UNSIGNED_INT, OptionalInt.of(7))));
        e.add(new Entry(i++, Value.of(EntityDataType.OPTIONAL_UNSIGNED_INT, OptionalInt.empty())));
        e.add(new Entry(i++, Value.of(EntityDataType.POSE, Pose.SLEEPING)));
        e.add(new Entry(i++, Value.of(EntityDataType.SNIFFER_STATE, "feeling_happy")));
        e.add(new Entry(i++, Value.of(EntityDataType.ARMADILLO_STATE, "unrolling")));
        return e;
    }

    @Test
    void everyBridgedTypeRoundTrips() throws Exception {
        try (FurnaceTestServer server = start()) {
            var p = new ClientboundSetEntityDataPacket(77, everyBridgedType());
            Set<String> covered = new TreeSet<>();
            for (Entry en : p.entries()) covered.add(en.value().dataType().name());
            assertEquals(new TreeSet<>(EntityDataBridge.bridgedTypes()), covered, "표본이 다리의 모든 값 종류를 덮어야 한다");

            var shim = (net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket) PacketDelivery.fromFurnace(p);
            assertEquals(77, shim.id());
            assertEquals(p.entries().size(), shim.packedItems().size());
            // 값 종류가 바닐라 직렬화 상수로 잡혔다(ProtocolLib 은 이 객체로 Registry 를 찾는다)
            assertTrue(shim.packedItems().get(6).serializer() == EntityDataSerializers.OPTIONAL_COMPONENT);
            assertTrue(shim.packedItems().get(17).value() instanceof net.minecraft.world.level.block.state.BlockState);
            assertEquals(p, PacketDelivery.convert(shim), "Furnace → shim → Furnace 가 같은 값");
        }
    }

    @Test
    void unbridgedTypesAreRefusedWholeNotDropped() throws Exception {
        try (FurnaceTestServer server = start()) {
            // 서버가 읽은 값 중 shim 이 아직 없는 종류(주민 데이터)가 섞이면 패킷 전체를 되돌리지 않는다(일부 칸만 보이면 Paper 와 다르다)
            var p = new ClientboundSetEntityDataPacket(5, List.of(new Entry(0, Value.of(EntityDataType.BYTE, (byte) 0)),
                    new Entry(18, Value.of(EntityDataType.VILLAGER_DATA, new org.domi.furnace.api.entity.data.VillagerData("minecraft:plains", "minecraft:farmer", 2)))));
            assertThrows(UnsupportedNmsCall.class, () -> PacketDelivery.fromFurnace(p));
        }
    }

    /** 플러그인이 NMS(=ProtocolLib 의 sendServerPacket 길)로 보낸 메타데이터가 클라이언트에 그대로 닿는다 — 예전에는 조용히 버려졌다. */
    @Test
    void pluginSentMetadataReachesTheClient() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Holo");
            server.tick(3);
            var items = new ArrayList<SynchedEntityData.DataValue<?>>();
            items.add(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x60));   // 투명 + 발광
            items.add(new SynchedEntityData.DataValue<>(2, EntityDataSerializers.OPTIONAL_COMPONENT,
                    Optional.of((net.minecraft.network.chat.Component) net.minecraft.network.chat.Component.literal("홀로그램"))));
            items.add(new SynchedEntityData.DataValue<>(3, EntityDataSerializers.BOOLEAN, true));
            var shim = new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(900001, items);
            tp.clearReceived();
            server.onGlobal(() -> {
                ((CraftPlayer) Bukkit.getPlayer("Holo")).getHandle().connection.send(shim);
                return null;
            });
            var got = tp.awaitPacket(PacketTypes.Clientbound.SET_ENTITY_DATA, x -> x.entityId() == 900001);
            assertEquals(List.of(new Entry(0, Value.ofByte(0x60)), new Entry(2, Value.ofOptionalText(Text.text("홀로그램"))), new Entry(3, Value.ofBoolean(true))),
                    got.entries());
        }
    }
}
