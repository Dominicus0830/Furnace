// S→C 엔티티 데이터(0x58) — (index byte, 직렬화기 id VarInt, 값) 목록, 0xFF 로 끝. byte·int(VarInt)·float·아이템·boolean·자세 값을 쓴다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
public record SetEntityData(int entityId, List<Item> items) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SET_ENTITY_DATA;
    /** EntityDataSerializers 순번. */
    public static final int SERIALIZER_BYTE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_BYTE, SERIALIZER_INT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_INT, SERIALIZER_LONG = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_LONG, SERIALIZER_FLOAT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_FLOAT, SERIALIZER_STRING = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_STRING, SERIALIZER_ITEM = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_ITEM_STACK, SERIALIZER_BOOLEAN = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_BOOLEAN, SERIALIZER_COMPOUND = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_COMPOUND_TAG,
            SERIALIZER_DIRECTION = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_DIRECTION, SERIALIZER_FROG_VARIANT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_FROG_VARIANT, SERIALIZER_WOLF_VARIANT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_WOLF_VARIANT, SERIALIZER_ARMADILLO_STATE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_ARMADILLO_STATE, SERIALIZER_SNIFFER_STATE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_SNIFFER_STATE, SERIALIZER_BLOCK_POS = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_BLOCK_POS, SERIALIZER_OPTIONAL_BLOCK_POS = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_OPTIONAL_BLOCK_POS, SERIALIZER_OPTIONAL_UUID = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_OPTIONAL_UUID, SERIALIZER_BLOCK_STATE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_BLOCK_STATE, SERIALIZER_OPTIONAL_BLOCK_STATE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_OPTIONAL_BLOCK_STATE, SERIALIZER_CAT_VARIANT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_CAT_VARIANT, SERIALIZER_OPTIONAL_INT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_OPTIONAL_UNSIGNED_INT, SERIALIZER_VILLAGER_DATA = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_VILLAGER_DATA, SERIALIZER_POSE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_POSE, SERIALIZER_PARTICLE = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_PARTICLE, SERIALIZER_PARTICLES = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_PARTICLES, SERIALIZER_COMPONENT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_COMPONENT, SERIALIZER_OPTIONAL_COMPONENT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_OPTIONAL_COMPONENT, SERIALIZER_PAINTING_VARIANT = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_PAINTING_VARIANT, SERIALIZER_ROTATIONS = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_ROTATIONS, SERIALIZER_VECTOR3 = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_VECTOR3, SERIALIZER_QUATERNION = org.domi.furnace.server.entity.EntityDataIds.SERIALIZER_QUATERNION;
    /** Entity.DATA_AIR_SUPPLY_ID, LivingEntity.DATA_HEALTH_ID, Player.DATA_SCORE_ID. */
    public static final int AIR_SUPPLY = 1, HEALTH = 9, SCORE = 16, PLAYER_ABSORPTION = 15;

    public record Item(int index, int serializer, Object value) {}

    public static SetEntityData health(int entityId, float health) {
        return new SetEntityData(entityId, List.of(new Item(HEALTH, SERIALIZER_FLOAT, health)));
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        for (Item it : items) {
            buf.writeByte(it.index());
            Types.writeVarInt(buf, it.serializer());
            switch (it.serializer()) {
                case SERIALIZER_BYTE -> buf.writeByte(((Number) it.value()).byteValue());
                case SERIALIZER_INT, SERIALIZER_DIRECTION, SERIALIZER_FROG_VARIANT, SERIALIZER_ARMADILLO_STATE, SERIALIZER_SNIFFER_STATE, SERIALIZER_POSE, SERIALIZER_CAT_VARIANT, SERIALIZER_BLOCK_STATE -> Types.writeVarInt(buf, ((Number) it.value()).intValue());
                case SERIALIZER_LONG -> Types.writeVarLong(buf, ((Number) it.value()).longValue());
                case SERIALIZER_STRING -> Types.writeString(buf, (String) it.value());
                case SERIALIZER_FLOAT -> buf.writeFloat(((Number) it.value()).floatValue());
                case SERIALIZER_BOOLEAN -> buf.writeBoolean((Boolean) it.value());
                case SERIALIZER_ITEM -> ContainerSetContent.writeSlot(buf, (org.domi.furnace.server.entity.ItemStack) it.value());
                case SERIALIZER_BLOCK_POS -> {   // BlockPos: 위치(long). 값은 {x,y,z}
                    int[] p = (int[]) it.value();
                    buf.writeLong(((long) (p[0] & 0x3FFFFFF) << 38) | ((long) (p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFF));
                }
                case SERIALIZER_OPTIONAL_BLOCK_POS -> {   // Optional<BlockPos>: 있으면 true + 위치(long). 값은 null 또는 {x,y,z}
                    int[] p = (int[]) it.value();
                    buf.writeBoolean(p != null);
                    if (p != null) buf.writeLong(((long) (p[0] & 0x3FFFFFF) << 38) | ((long) (p[2] & 0x3FFFFFF) << 12) | (p[1] & 0xFFF));
                }
                case SERIALIZER_OPTIONAL_BLOCK_STATE -> Types.writeVarInt(buf, it.value() == null ? 0 : (Integer) it.value());   // 0 = 없음
                case SERIALIZER_OPTIONAL_UUID -> {   // Optional<UUID>: 값은 null 또는 UUID
                    java.util.UUID u = (java.util.UUID) it.value();
                    buf.writeBoolean(u != null);
                    if (u != null) Types.writeUuid(buf, u);
                }
                case SERIALIZER_OPTIONAL_INT -> {   // OptionalInt: 0 = 없음, 아니면 값 + 1
                    Integer v = (Integer) it.value();
                    Types.writeVarInt(buf, v == null ? 0 : v + 1);
                }
                case SERIALIZER_VILLAGER_DATA -> {   // {종류, 직업, 레벨} 레지스트리 번호와 레벨
                    int[] d = (int[]) it.value();
                    Types.writeVarInt(buf, d[0]);
                    Types.writeVarInt(buf, d[1]);
                    Types.writeVarInt(buf, d[2]);
                }
                case SERIALIZER_PAINTING_VARIANT -> Types.writeVarInt(buf, ((Number) it.value()).intValue() + 1);   // 레지스트리 홀더
                // 늑대 종류도 바닐라 1.21.1 은 ByteBufCodecs.holder(WOLF_VARIANT, 직접 코덱) — 0 은 직접 정의, 등록된 종류는 번호 + 1(그림과 같음)
                case SERIALIZER_WOLF_VARIANT -> Types.writeVarInt(buf, ((Number) it.value()).intValue() + 1);
                case SERIALIZER_COMPOUND -> new org.domi.furnace.server.net.io.nbt.NbtWriter(buf).writeCompound((org.domi.furnace.server.net.io.nbt.NbtCompound) it.value());   // CompoundTag: 무명 루트 NBT
                case SERIALIZER_COMPONENT -> buf.writeBytes(((org.domi.furnace.api.text.Text) it.value()).networkNbt());
                case SERIALIZER_OPTIONAL_COMPONENT -> {   // Optional<Component>: 있으면 true + 텍스트 NBT. 값은 null 또는 Text
                    var t = (org.domi.furnace.api.text.Text) it.value();
                    buf.writeBoolean(t != null);
                    if (t != null) buf.writeBytes(t.networkNbt());
                }
                case SERIALIZER_VECTOR3, SERIALIZER_QUATERNION, SERIALIZER_ROTATIONS -> {   // 값은 float[3] / float[4](x, y, z, w)
                    for (float f : (float[]) it.value()) buf.writeFloat(f);
                }
                case SERIALIZER_PARTICLE -> ((ParticleOptions) it.value()).write(buf);   // 입자 한 개(종류 id + 옵션). 값은 ParticleOptions
                case SERIALIZER_PARTICLES -> {   // List<ParticleOptions>(효과 입자): 개수 VarInt + 입자마다 종류·옵션. 값은 List<ParticleOptions>
                    @SuppressWarnings("unchecked")
                    var list = (List<ParticleOptions>) it.value();
                    Types.writeVarInt(buf, list.size());
                    for (ParticleOptions p : list) p.write(buf);
                }
                default -> throw new IllegalArgumentException("serializer " + it.serializer());
            }
        }
        buf.writeByte(0xFF);
    }
}
