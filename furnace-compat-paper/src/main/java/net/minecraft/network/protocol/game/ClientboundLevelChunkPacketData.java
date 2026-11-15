package net.minecraft.network.protocol.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.domi.furnace.compat.nms.NbtWire;
import org.domi.furnace.compat.nms.NmsBlockEntityTypes;

/**
 * 청크 자료 shim(Mojang 이름, T-182). 바닐라 1.21.1 과 같은 모양 — 하이트맵(NBT), 구역 데이터 바이트(buffer), 블록 엔티티 항목 목록.
 * 패킷 라이브러리는 필드를 형식으로 찾고(ProtocolLib WrappedLevelChunkData.ChunkData: CompoundTag·byte[]·List), 항목 클래스는
 * List 필드의 형식 인자에서 찾는다 — 그래서 {@code List<BlockEntityInfo>} 로 선언한다. 블록을 숨기는 플러그인(Orebfuscator)은 buffer 를 바꿔 넣는다.
 */
public class ClientboundLevelChunkPacketData {

    /** 바닐라: buffer 상한(2 MiB). */
    private static final int TWO_MEGABYTES = 2097152;

    private final CompoundTag heightmaps;
    private final byte[] buffer;
    private final List<BlockEntityInfo> blockEntitiesData;

    /**
     * 바닐라: 바이트에서 읽기. 하이트맵 NBT 가 없으면(태그 0) 빈 compound 로 받는다 — 패킷 라이브러리가 빈 자료를 0 버퍼로 만들 때
     * (ProtocolLib ChunkData.fromValues)와 같은 결과다(Paper 에서는 ProtocolLib 이 readNbt 를 가로채 빈 compound 를 준다).
     */
    public ClientboundLevelChunkPacketData(RegistryFriendlyByteBuf buf, int x, int z) {
        var h = NbtWire.read(buf);
        this.heightmaps = h == null ? new CompoundTag() : CompoundTag.fromFurnace(h);
        int size = buf.readVarInt();
        if (size < 0 || size > TWO_MEGABYTES) throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
        this.buffer = new byte[size];
        buf.readBytes(this.buffer);
        int n = buf.readVarInt();
        this.blockEntitiesData = new ArrayList<>(Math.min(n, 1024));
        for (int i = 0; i < n; i++) {
            int packedXZ = buf.readUnsignedByte();
            int y = buf.readShort();
            BlockEntityType<?> type = NmsBlockEntityTypes.registry().byId(buf.readVarInt());
            var tag = NbtWire.read(buf);
            blockEntitiesData.add(new BlockEntityInfo(packedXZ, y, type, tag == null ? null : CompoundTag.fromFurnace(tag)));
        }
    }

    /** 이 모듈 전용: 값으로(Furnace 패킷에서 되돌릴 때). 바닐라에 없는 생성자라 공개하지 않는다(라이브러리가 생성자를 모양으로 찾는다). */
    public static ClientboundLevelChunkPacketData furnace(CompoundTag heightmaps, byte[] buffer, List<?> blockEntitiesData) {
        return new ClientboundLevelChunkPacketData(heightmaps, buffer, blockEntitiesData);
    }

    private ClientboundLevelChunkPacketData(CompoundTag heightmaps, byte[] buffer, List<?> blockEntitiesData) {
        this.heightmaps = heightmaps;
        this.buffer = buffer;
        this.blockEntitiesData = new ArrayList<>(blockEntitiesData.size());
        for (Object o : blockEntitiesData) this.blockEntitiesData.add((BlockEntityInfo) o);
    }

    /** 이 모듈 전용: 블록 엔티티 항목 만들기(바닐라처럼 항목 클래스는 private 이다). */
    public static Object furnaceInfo(int packedXZ, int y, BlockEntityType<?> type, CompoundTag tag) { return new BlockEntityInfo(packedXZ, y, type, tag); }

    /** 이 모듈 전용: 항목 값 [packedXZ, y, type, tag]. */
    public static Object[] furnaceInfoValues(Object info) {
        BlockEntityInfo e = (BlockEntityInfo) info;
        return new Object[] {e.packedXZ, e.y, e.type, e.tag};
    }

    /** 바닐라: 바이트로 쓰기. */
    public void write(RegistryFriendlyByteBuf buf) {
        NbtWire.write(buf, heightmaps == null ? null : heightmaps.furnace());
        buf.writeVarInt(buffer.length);
        buf.writeBytes(buffer);
        buf.writeVarInt(blockEntitiesData.size());
        for (BlockEntityInfo e : blockEntitiesData) {
            buf.writeByte(e.packedXZ);
            buf.writeShort(e.y);
            buf.writeVarInt(NmsBlockEntityTypes.registry().getId(e.type));
            NbtWire.write(buf, e.tag == null ? null : e.tag.furnace());
        }
    }

    /** 바닐라: 구역 데이터를 읽는 버퍼. */
    public FriendlyByteBuf getReadBuffer() { return new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(buffer)); }

    public CompoundTag getHeightmaps() { return heightmaps; }

    /** 이 모듈 전용: 구역 데이터 바이트(복사하지 않는다). */
    public byte[] furnaceBuffer() { return buffer; }

    /** 이 모듈 전용: 블록 엔티티 항목({@link #furnaceInfoValues} 로 읽는다). */
    public List<?> furnaceBlockEntities() { return blockEntitiesData; }

    /** 바닐라: 블록 엔티티 항목 하나(private, 필드도 바닐라처럼 final — 스키마 privateAccess). packedXZ = 구역 안 (x &lt;&lt; 4) | z. */
    private static class BlockEntityInfo {

        final int packedXZ;
        final int y;
        final BlockEntityType<?> type;
        final CompoundTag tag;

        BlockEntityInfo(int packedXZ, int y, BlockEntityType<?> type, CompoundTag tag) {
            this.packedXZ = packedXZ;
            this.y = y;
            this.type = type;
            this.tag = tag;
        }
    }
}
