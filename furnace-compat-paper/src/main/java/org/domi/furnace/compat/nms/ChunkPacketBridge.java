package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketTypes;

/**
 * 청크·빛 패킷(level_chunk_with_light·light_update) Furnace 범용 패킷 ↔ shim(T-182, ProtocolLib MAP_CHUNK·LIGHT_UPDATE).
 * 범용 패킷의 모양은 서버 손 코덱(GenericComplex): chunk_data{heightmaps, buffer, block_entities_data[{packed_xz, y, type, tag}]},
 * light_data{sky_y_mask, block_y_mask, empty_sky_y_mask, empty_block_y_mask(long[]), sky_updates, block_updates(byte[] 목록)}.
 * 구역 데이터 바이트(buffer)는 해석하지 않고 그대로 넘긴다 — 리스너가 바꾼 바이트가 그대로 나간다.
 */
public final class ChunkPacketBridge {

    private ChunkPacketBridge() {}

    static final String CHUNK = "level_chunk_with_light";
    static final String LIGHT = "light_update";

    static boolean handles(String wireName) { return CHUNK.equals(wireName) || LIGHT.equals(wireName); }

    // ------------------------------------------------------------------ Furnace → shim

    static net.minecraft.network.protocol.Packet<?> fromFurnace(GenericPacket g) {
        String at = g.type().name();
        if (g.type() == PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT) {
            return ClientboundLevelChunkWithLightPacket.of(i(g.get("x"), at + ".x"), i(g.get("z"), at + ".z"),
                    chunkData((PacketStruct) need(g.get("chunk_data"), at + ".chunk_data"), at + ".chunk_data"),
                    lightData((PacketStruct) need(g.get("light_data"), at + ".light_data"), at + ".light_data"));
        }
        if (g.type() == PacketTypes.Clientbound.LIGHT_UPDATE) {
            return ClientboundLightUpdatePacket.of(i(g.get("x"), at + ".x"), i(g.get("z"), at + ".z"),
                    lightData((PacketStruct) need(g.get("light_data"), at + ".light_data"), at + ".light_data"));
        }
        throw new IllegalArgumentException("not a chunk or light packet: " + g.type());
    }

    private static ClientboundLevelChunkPacketData chunkData(PacketStruct s, String at) {
        CompoundTag heightmaps = CompoundTag.fromFurnace((NbtCompound) need(s.get("heightmaps"), at + ".heightmaps"));
        byte[] buffer = (byte[]) need(s.get("buffer"), at + ".buffer");
        List<Object> infos = new ArrayList<>();
        int n = 0;
        for (Object o : (List<?>) need(s.get("block_entities_data"), at + ".block_entities_data")) {
            String eat = at + ".block_entities_data[" + n++ + "]";
            PacketStruct e = (PacketStruct) need(o, eat);
            Object tag = e.get("tag");
            if (tag instanceof Optional<?> opt) tag = opt.orElse(null);
            infos.add(ClientboundLevelChunkPacketData.furnaceInfo(i(e.get("packed_xz"), eat + ".packed_xz"), i(e.get("y"), eat + ".y"),
                    NmsBlockEntityTypes.byName((String) need(e.get("type"), eat + ".type"), eat + ".type"),
                    tag == null ? null : CompoundTag.fromFurnace((NbtCompound) tag)));
        }
        return ClientboundLevelChunkPacketData.furnace(heightmaps, buffer, infos);
    }

    private static ClientboundLightUpdatePacketData lightData(PacketStruct s, String at) {
        return ClientboundLightUpdatePacketData.furnace(bits(s, "sky_y_mask", at), bits(s, "block_y_mask", at), bits(s, "empty_sky_y_mask", at),
                bits(s, "empty_block_y_mask", at), layers(s, "sky_updates", at), layers(s, "block_updates", at));
    }

    private static BitSet bits(PacketStruct s, String name, String at) { return BitSet.valueOf((long[]) need(s.get(name), at + "." + name)); }

    private static List<byte[]> layers(PacketStruct s, String name, String at) {
        List<byte[]> out = new ArrayList<>();
        for (Object o : (List<?>) need(s.get(name), at + "." + name)) out.add((byte[]) o);
        return out;
    }

    // ------------------------------------------------------------------ shim → Furnace

    public static GenericPacket toFurnace(ClientboundLevelChunkWithLightPacket p) {
        String at = CHUNK;
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("x", p.getX());
        m.put("z", p.getZ());
        m.put("chunk_data", chunkData(need(p.getChunkData(), at + ".chunk_data"), at + ".chunk_data"));
        m.put("light_data", lightData(need(p.getLightData(), at + ".light_data"), at + ".light_data"));
        return GenericPacket.of(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT, PacketStruct.of(m));
    }

    public static GenericPacket toFurnace(ClientboundLightUpdatePacket p) {
        String at = LIGHT;
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("x", p.getX());
        m.put("z", p.getZ());
        m.put("light_data", lightData(need(p.getLightData(), at + ".light_data"), at + ".light_data"));
        return GenericPacket.of(PacketTypes.Clientbound.LIGHT_UPDATE, PacketStruct.of(m));
    }

    private static PacketStruct chunkData(ClientboundLevelChunkPacketData d, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("heightmaps", need(d.getHeightmaps(), at + ".heightmaps").furnace());   // 바닐라도 null 이면 쓰다 실패한다
        m.put("buffer", need(d.furnaceBuffer(), at + ".buffer"));
        List<PacketStruct> infos = new ArrayList<>();
        int n = 0;
        for (Object o : need(d.furnaceBlockEntities(), at + ".block_entities_data")) {
            String eat = at + ".block_entities_data[" + n++ + "]";
            Object[] v = ClientboundLevelChunkPacketData.furnaceInfoValues(need(o, eat));
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            e.put("packed_xz", v[0]);
            e.put("y", v[1]);
            e.put("type", NmsBlockEntityTypes.name(need((net.minecraft.world.level.block.entity.BlockEntityType<?>) v[2], eat + ".type"), eat + ".type"));
            e.put("tag", v[3] == null ? Optional.empty() : Optional.of(((CompoundTag) v[3]).furnace()));
            infos.add(PacketStruct.of(e));
        }
        m.put("block_entities_data", infos);
        return PacketStruct.of(m);
    }

    private static PacketStruct lightData(ClientboundLightUpdatePacketData d, String at) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("sky_y_mask", need(d.getSkyYMask(), at + ".skyYMask").toLongArray());
        m.put("block_y_mask", need(d.getBlockYMask(), at + ".blockYMask").toLongArray());
        m.put("empty_sky_y_mask", need(d.getEmptySkyYMask(), at + ".emptySkyYMask").toLongArray());
        m.put("empty_block_y_mask", need(d.getEmptyBlockYMask(), at + ".emptyBlockYMask").toLongArray());
        m.put("sky_updates", layers(need(d.getSkyUpdates(), at + ".skyUpdates"), at + ".skyUpdates"));
        m.put("block_updates", layers(need(d.getBlockUpdates(), at + ".blockUpdates"), at + ".blockUpdates"));
        return PacketStruct.of(m);
    }

    private static List<byte[]> layers(List<byte[]> in, String at) {
        List<byte[]> out = new ArrayList<>(in.size());
        int n = 0;
        for (byte[] a : in) {
            String lat = at + "[" + n++ + "]";
            if (need(a, lat).length != 2048) throw new IllegalArgumentException(lat + " light array must be 2048 bytes (vanilla DataLayer): " + a.length);
            out.add(a);
        }
        return out;
    }

    private static int i(Object o, String at) { return ((Number) need(o, at)).intValue(); }

    static <T> T need(T v, String at) {
        if (v == null) throw new IllegalArgumentException(at + " is null (vanilla also fails to write it)");
        return v;
    }
}
