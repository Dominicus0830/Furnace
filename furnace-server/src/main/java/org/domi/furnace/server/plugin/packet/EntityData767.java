// 1.21.1 엔티티 메타데이터 변환: API 값 종류(EntityDataType 31종) ↔ 직렬화 번호·와이어, 이름 있는 칸(EntityDataKey) → 칸 번호,
// 엔티티 종류별 칸 배치(어느 번호에 어떤 값 종류가 있는지 — 없는 칸을 보내면 클라이언트가 끊으므로 미리 거른다).
// 칸 번호 표(EntityDataIndex767)는 서버 엔티티 정의에서 만들었고, 배치 표는 resources/entity_data/layouts-1.21.1.txt 다
// (EntityDataKeysTest 가 둘 다 서버 엔티티의 SynchedData 와 대조한다).
package org.domi.furnace.server.plugin.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.data.EntityDataKey;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.entity.data.VillagerData;
import org.domi.furnace.api.math.Quaternionf;
import org.domi.furnace.api.math.Rotations;
import org.domi.furnace.api.math.Vector3f;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Direction;
import org.domi.furnace.api.world.GlobalPos;
import org.domi.furnace.server.entity.Frog;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.plugin.ApiNbt;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;

public final class EntityData767 {

    /** 직렬화 번호 순서(바닐라 EntityDataSerializers 등록 순서, 1.21.1). */
    static final EntityDataType<?>[] SERIALIZERS = {
            EntityDataType.BYTE, EntityDataType.INT, EntityDataType.LONG, EntityDataType.FLOAT, EntityDataType.STRING, EntityDataType.COMPONENT,
            EntityDataType.OPTIONAL_COMPONENT, EntityDataType.ITEM_STACK, EntityDataType.BOOLEAN, EntityDataType.ROTATIONS, EntityDataType.BLOCK_POS,
            EntityDataType.OPTIONAL_BLOCK_POS, EntityDataType.DIRECTION, EntityDataType.OPTIONAL_UUID, EntityDataType.BLOCK_STATE,
            EntityDataType.OPTIONAL_BLOCK_STATE, EntityDataType.COMPOUND_TAG, EntityDataType.PARTICLE, EntityDataType.PARTICLES,
            EntityDataType.VILLAGER_DATA, EntityDataType.OPTIONAL_UNSIGNED_INT, EntityDataType.POSE, EntityDataType.CAT_VARIANT,
            EntityDataType.WOLF_VARIANT, EntityDataType.FROG_VARIANT, EntityDataType.OPTIONAL_GLOBAL_POS, EntityDataType.PAINTING_VARIANT,
            EntityDataType.SNIFFER_STATE, EntityDataType.ARMADILLO_STATE, EntityDataType.VECTOR3, EntityDataType.QUATERNION
    };

    private static final Map<EntityDataType<?>, Integer> SERIALIZER_IDS = new IdentityHashMap<>();
    static {
        for (int i = 0; i < SERIALIZERS.length; i++) SERIALIZER_IDS.put(SERIALIZERS[i], i);
        EntityDataKey.installIndexResolver(EntityData767::index);   // API 의 EntityDataKey.index()·패킷 get/with 가 쓰는 표(1.21.1)
    }

    /** 바닐라 Sniffer.State·Armadillo.ArmadilloState 순서(와이어는 순번). */
    static final List<String> SNIFFER_STATES = List.of("idling", "feeling_happy", "scenting", "sniffing", "searching", "digging", "rising");
    static final List<String> ARMADILLO_STATES = List.of("idle", "rolling", "scared", "unrolling");
    private static final int STRING_MAX = 32767;

    private final Supplier<BlockRegistry> blocks;
    private final Supplier<ItemRegistry> items;

    EntityData767(Supplier<BlockRegistry> blocks, Supplier<ItemRegistry> items) {
        this.blocks = blocks;
        this.items = items;
    }

    // ------------------------------------------------------------------ 칸 번호·배치

    public static int serializerId(EntityDataType<?> type) {
        Integer i = SERIALIZER_IDS.get(type);
        if (i == null) throw new IllegalArgumentException("이 서버 버전에 없는 값 종류: " + type);
        return i;
    }

    public static EntityDataType<?> serializer(int id) { return id >= 0 && id < SERIALIZERS.length ? SERIALIZERS[id] : null; }

    public static OptionalInt index(EntityDataKey<?> key) {
        Integer i = EntityDataIndex767.INDEX.get(key);
        return i == null ? OptionalInt.empty() : OptionalInt.of(i);
    }

    private static volatile Map<String, EntityDataType<?>[]> layouts;

    /** 엔티티 종류의 칸 배치(번호 순 값 종류). 모르는 종류면 null. */
    public static EntityDataType<?>[] layout(String entityType) {
        Map<String, EntityDataType<?>[]> m = layouts;
        if (m == null) {
            synchronized (EntityData767.class) {
                if (layouts == null) layouts = loadLayouts();
                m = layouts;
            }
        }
        return m.get(entityType.indexOf(':') < 0 ? "minecraft:" + entityType : entityType);
    }

    /**
     * 그 종류에 이 칸이 있는가: 칸의 주인 부류가 그 종류의 계층에 있고(바닐라 클래스 계층), 그 번호에 같은 값 종류가 있다.
     * 번호·값 종류만 보면 다른 부류의 칸이 우연히 맞아 통과한다(예: interaction 9번 height 와 LivingEntity.HEALTH).
     */
    public static boolean has(String entityType, EntityDataKey<?> key) {
        String type = entityType.indexOf(':') < 0 ? "minecraft:" + entityType : entityType;
        EntityDataType<?>[] l = layout(type);
        java.util.Set<String> o = owners(type);
        OptionalInt i = index(key);
        return l != null && o != null && o.contains(key.owner()) && i.isPresent() && i.getAsInt() < l.length && l[i.getAsInt()] == key.type();
    }

    private static volatile Map<String, java.util.Set<String>> owners;

    /** 엔티티 종류의 메타데이터 주인 부류들(EntityDataKey.owner 이름). 모르는 종류면 null. */
    public static java.util.Set<String> owners(String entityType) {
        Map<String, java.util.Set<String>> m = owners;
        if (m == null) {
            synchronized (EntityData767.class) {
                if (owners == null) owners = loadOwners();
                m = owners;
            }
        }
        return m.get(entityType.indexOf(':') < 0 ? "minecraft:" + entityType : entityType);
    }

    private static Map<String, java.util.Set<String>> loadOwners() {
        Map<String, java.util.Set<String>> m = new HashMap<>();
        try (var in = EntityData767.class.getClassLoader().getResourceAsStream("entity_data/owners-1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("entity_data/owners-1.21.1.txt 없음");
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                m.put(p[0], java.util.Set.of(java.util.Arrays.copyOfRange(p, 1, p.length)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(m);
    }

    private static Map<String, EntityDataType<?>[]> loadLayouts() {
        Map<String, EntityDataType<?>[]> m = new HashMap<>();
        try (var in = EntityData767.class.getClassLoader().getResourceAsStream("entity_data/layouts-1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("entity_data/layouts-1.21.1.txt 없음");
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                EntityDataType<?>[] l = new EntityDataType<?>[p.length - 1];
                for (int i = 1; i < p.length; i++) {
                    l[i - 1] = EntityDataType.byName(p[i]);
                    if (l[i - 1] == null) throw new IllegalStateException("모르는 값 종류 " + p[i] + " (" + p[0] + ")");
                }
                m.put(p[0], l);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(m);
    }

    // ------------------------------------------------------------------ 와이어

    /**
     * 직렬화 번호 ser 의 값 하나를 읽는다. 이 버전이 모르는 번호면 null(길이를 모르므로 부르는 쪽이 멈춘다).
     * 값을 이름으로 바꾸지 못하면(범위 밖 번호, 직접 정의된 그림·늑대 종류 등) RuntimeException — 부르는 쪽이 Unsupported 로 남기고 멈춘다.
     */
    Value read(int ser, ByteBuf b) {
        EntityDataType<?> t = serializer(ser);
        if (t == null) return null;
        return valueOf(t, readObject(t, b));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Value valueOf(EntityDataType t, Object v) { return Value.of(t, v); }

    private Object readObject(EntityDataType<?> t, ByteBuf b) {
        return switch (t.name()) {
            case "byte" -> b.readByte();
            case "int" -> Types.readVarInt(b);
            case "long" -> Types.readVarLong(b);
            case "float" -> b.readFloat();
            case "string" -> Types.readString(b, STRING_MAX);
            case "component" -> NetworkText.read(b);
            case "optional_component" -> b.readBoolean() ? Optional.of(NetworkText.read(b)) : Optional.empty();
            case "item_stack" -> ApiConvert.item(ItemStackCodecs.readStack(b), items());
            case "boolean" -> b.readBoolean();
            case "rotations" -> new Rotations(b.readFloat(), b.readFloat(), b.readFloat());
            case "block_pos" -> pos(b);
            case "optional_block_pos" -> b.readBoolean() ? Optional.of(pos(b)) : Optional.empty();
            case "direction" -> enumAt(Direction.values(), Types.readVarInt(b), "방향");
            case "optional_uuid" -> b.readBoolean() ? Optional.of(Types.readUuid(b)) : Optional.empty();
            case "block_state" -> ApiConvert.block(Types.readVarInt(b), blocks());
            case "optional_block_state" -> {
                int id = Types.readVarInt(b);
                yield id == 0 ? Optional.empty() : Optional.of(ApiConvert.block(id, blocks()));
            }
            case "compound_tag" -> ApiNbt.toApi(readCompound(b));
            case "particle" -> readParticle(b);
            case "particles" -> {
                int n = Types.readVarInt(b);
                if (n < 0 || n > b.readableBytes()) throw new IllegalArgumentException("입자 수 오류: " + n);
                List<Particle> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) l.add(readParticle(b));
                yield List.copyOf(l);
            }
            case "villager_data" -> new VillagerData(nameAt(Villager.TYPES, Types.readVarInt(b), "주민 종류"),
                    nameAt(Villager.PROFESSIONS, Types.readVarInt(b), "주민 직업"), Types.readVarInt(b));
            case "optional_unsigned_int" -> {
                int v = Types.readVarInt(b);
                yield v == 0 ? OptionalInt.empty() : OptionalInt.of(v - 1);
            }
            case "pose" -> enumAt(Pose.values(), Types.readVarInt(b), "자세");
            case "cat_variant" -> registryName("minecraft:cat_variant", Types.readVarInt(b));
            case "wolf_variant" -> {   // 바닐라 holder(WOLF_VARIANT, 직접 코덱): 0 = 직접 정의, 등록된 종류 = 번호 + 1
                int v = Types.readVarInt(b);
                if (v == 0) throw new IllegalArgumentException("직접 정의된 늑대 종류는 읽지 않는다");
                yield dataRegistryName("minecraft:wolf_variant", v - 1);
            }
            case "frog_variant" -> nameAt(List.of(Frog.VARIANTS), Types.readVarInt(b), "개구리 종류");
            case "optional_global_pos" -> b.readBoolean() ? Optional.of(new GlobalPos(Types.readString(b, STRING_MAX), pos(b))) : Optional.empty();
            case "painting_variant" -> {
                int v = Types.readVarInt(b);
                if (v == 0) throw new IllegalArgumentException("직접 정의된 그림 종류는 읽지 않는다");
                yield dataRegistryName("minecraft:painting_variant", v - 1);
            }
            case "sniffer_state" -> stateAt(SNIFFER_STATES, Types.readVarInt(b));
            case "armadillo_state" -> stateAt(ARMADILLO_STATES, Types.readVarInt(b));
            case "vector3" -> new Vector3f(b.readFloat(), b.readFloat(), b.readFloat());
            case "quaternion" -> new Quaternionf(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
            default -> throw new IllegalStateException("값 종류 " + t);
        };
    }

    /** 값 하나를 쓴다(직렬화 번호는 부르는 쪽이 이미 썼다). */
    void write(ByteBuf o, Value value) {
        EntityDataType<?> t = value.dataType();
        Object v = value.asObject();
        switch (t.name()) {
            case "byte" -> o.writeByte((Byte) v);
            case "int" -> Types.writeVarInt(o, (Integer) v);
            case "long" -> Types.writeVarLong(o, (Long) v);
            case "float" -> o.writeFloat((Float) v);
            case "string" -> Types.writeString(o, (String) v);
            case "component" -> NetworkText.write(o, (Text) v);
            case "optional_component" -> {
                Optional<?> opt = (Optional<?>) v;
                o.writeBoolean(opt.isPresent());
                opt.ifPresent(x -> NetworkText.write(o, (Text) x));
            }
            case "item_stack" -> ItemStackCodecs.writeStack(o, ApiConvert.item((org.domi.furnace.api.item.ItemStack) v, items()));
            case "boolean" -> o.writeBoolean((Boolean) v);
            case "rotations" -> {
                Rotations r = (Rotations) v;
                o.writeFloat(r.x());
                o.writeFloat(r.y());
                o.writeFloat(r.z());
            }
            case "block_pos" -> writePos(o, (BlockPos) v);
            case "optional_block_pos" -> {
                Optional<?> opt = (Optional<?>) v;
                o.writeBoolean(opt.isPresent());
                opt.ifPresent(p -> writePos(o, (BlockPos) p));
            }
            case "direction" -> Types.writeVarInt(o, ((Direction) v).ordinal());
            case "optional_uuid" -> {
                Optional<?> opt = (Optional<?>) v;
                o.writeBoolean(opt.isPresent());
                opt.ifPresent(u -> Types.writeUuid(o, (UUID) u));
            }
            case "block_state" -> Types.writeVarInt(o, ApiConvert.stateId((BlockData) v, blocks()));
            case "optional_block_state" -> {   // 바닐라: 없음·공기 = 0
                Optional<?> opt = (Optional<?>) v;
                Types.writeVarInt(o, opt.isEmpty() ? 0 : ApiConvert.stateId((BlockData) opt.get(), blocks()));
            }
            case "compound_tag" -> new NbtWriter(o).writeCompound(ApiNbt.toServer((org.domi.furnace.api.nbt.NbtCompound) v));
            case "particle" -> particle((Particle) v).write(o);
            case "particles" -> {
                List<?> l = (List<?>) v;
                Types.writeVarInt(o, l.size());
                for (Object p : l) particle((Particle) p).write(o);
            }
            case "villager_data" -> {
                VillagerData d = (VillagerData) v;
                Types.writeVarInt(o, indexIn(Villager.TYPES, d.type(), "주민 종류"));
                Types.writeVarInt(o, indexIn(Villager.PROFESSIONS, d.profession(), "주민 직업"));
                Types.writeVarInt(o, d.level());
            }
            case "optional_unsigned_int" -> {
                OptionalInt opt = (OptionalInt) v;
                Types.writeVarInt(o, opt.isEmpty() ? 0 : opt.getAsInt() + 1);
            }
            case "pose" -> Types.writeVarInt(o, ((Pose) v).id());
            case "cat_variant" -> Types.writeVarInt(o, registryId("minecraft:cat_variant", (String) v));
            case "wolf_variant" -> Types.writeVarInt(o, dataRegistryId("minecraft:wolf_variant", (String) v) + 1);   // 등록된 종류 = 번호 + 1(그림과 같음)
            case "frog_variant" -> Types.writeVarInt(o, indexIn(List.of(Frog.VARIANTS), (String) v, "개구리 종류"));
            case "optional_global_pos" -> {
                Optional<?> opt = (Optional<?>) v;
                o.writeBoolean(opt.isPresent());
                opt.ifPresent(x -> {
                    GlobalPos g = (GlobalPos) x;
                    Types.writeString(o, g.dimension());
                    writePos(o, g.pos());
                });
            }
            case "painting_variant" -> Types.writeVarInt(o, dataRegistryId("minecraft:painting_variant", (String) v) + 1);   // 등록된 종류 = 번호 + 1
            case "sniffer_state" -> Types.writeVarInt(o, indexIn(SNIFFER_STATES, (String) v, "스니퍼 상태"));
            case "armadillo_state" -> Types.writeVarInt(o, indexIn(ARMADILLO_STATES, (String) v, "아르마딜로 상태"));
            case "vector3" -> {
                Vector3f x = (Vector3f) v;
                o.writeFloat(x.x());
                o.writeFloat(x.y());
                o.writeFloat(x.z());
            }
            case "quaternion" -> {
                Quaternionf q = (Quaternionf) v;
                o.writeFloat(q.x());
                o.writeFloat(q.y());
                o.writeFloat(q.z());
                o.writeFloat(q.w());
            }
            default -> throw new IllegalStateException("값 종류 " + t);
        }
    }

    // ------------------------------------------------------------------ 입자

    /** API 입자 → 서버 입자 옵션(없는 종류·블록·아이템이면 IllegalArgumentException). */
    ParticleOptions particle(Particle p) {
        if (!ParticleOptions.Registry.has(p.type())) throw new IllegalArgumentException("알 수 없는 입자 종류: " + p.type());
        return switch (p) {
            case Particle.Simple s -> new ParticleOptions.Simple(s.type());
            case Particle.Block bl -> new ParticleOptions.Block(bl.type(), ApiConvert.stateId(bl.block(), blocks()));
            case Particle.Dust d -> new ParticleOptions.Dust(d.red(), d.green(), d.blue(), d.scale());
            case Particle.DustTransition d -> new ParticleOptions.DustTransition(d.fromRed(), d.fromGreen(), d.fromBlue(), d.toRed(), d.toGreen(), d.toBlue(), d.scale());
            case Particle.Color c -> new ParticleOptions.Color(c.argb());
            case Particle.Item i -> new ParticleOptions.Item(ApiConvert.item(i.item(), items()));
            case Particle.SculkCharge s -> new ParticleOptions.SculkCharge(s.roll());
            case Particle.Shriek s -> new ParticleOptions.Shriek(s.delay());
            case Particle.Vibration v -> switch (v.destination()) {
                case Particle.Vibration.BlockDestination bd -> ParticleOptions.Vibration.toBlock(bd.pos().x(), bd.pos().y(), bd.pos().z(), v.arrivalTicks());
                case Particle.Vibration.EntityDestination ed -> ParticleOptions.Vibration.toEntity(ed.entityId(), ed.yOffset(), v.arrivalTicks());
            };
        };
    }

    /** 입자 한 개를 읽는다(종류 번호 + 종류별 옵션). */
    Particle readParticle(ByteBuf b) {
        int id = Types.readVarInt(b);
        List<String> names = ParticleOptions.Registry.names();
        if (id < 0 || id >= names.size()) throw new IllegalArgumentException("입자 종류 번호 범위 밖: " + id);
        String type = names.get(id);
        if (Particle.BLOCK_TYPES.contains(type)) return new Particle.Block(type, ApiConvert.block(Types.readVarInt(b), blocks()));
        return switch (type) {
            case "minecraft:dust" -> new Particle.Dust(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
            case "minecraft:dust_color_transition" -> new Particle.DustTransition(b.readFloat(), b.readFloat(), b.readFloat(),
                    b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat());
            case "minecraft:entity_effect" -> new Particle.Color(type, b.readInt());
            case "minecraft:item" -> new Particle.Item(ApiConvert.item(ItemStackCodecs.readStack(b), items()));
            case "minecraft:sculk_charge" -> new Particle.SculkCharge(b.readFloat());
            case "minecraft:shriek" -> new Particle.Shriek(Types.readVarInt(b));
            case "minecraft:vibration" -> {
                int src = Types.readVarInt(b);
                Particle.Vibration.Destination d = switch (src) {
                    case 0 -> new Particle.Vibration.BlockDestination(pos(b));
                    case 1 -> new Particle.Vibration.EntityDestination(Types.readVarInt(b), b.readFloat());
                    default -> throw new IllegalArgumentException("진동 위치 소스 종류 범위 밖: " + src);
                };
                yield new Particle.Vibration(d, Types.readVarInt(b));
            }
            default -> new Particle.Simple(type);
        };
    }

    // ------------------------------------------------------------------ 조각

    private BlockRegistry blocks() { return blocks.get(); }

    private ItemRegistry items() { return items.get(); }

    private static BlockPos pos(ByteBuf b) {
        int[] p = Types.readBlockPos(b);
        return new BlockPos(p[0], p[1], p[2]);
    }

    private static void writePos(ByteBuf o, BlockPos p) { Types.writeBlockPos(o, p.x(), p.y(), p.z()); }

    private static NbtCompound readCompound(ByteBuf b) {
        Object tag;
        try (ByteBufInputStream in = new ByteBufInputStream(b)) {
            tag = NbtIo.readUnnamed(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!(tag instanceof NbtCompound c)) throw new IllegalArgumentException("compound 가 아닌 NBT: " + tag);
        return c;
    }

    private static <E> E enumAt(E[] values, int i, String what) {
        if (i < 0 || i >= values.length) throw new IllegalArgumentException(what + " 범위 밖: " + i);
        return values[i];
    }

    private static String nameAt(List<String> names, int i, String what) {
        if (i < 0 || i >= names.size()) throw new IllegalArgumentException(what + " 범위 밖: " + i);
        return "minecraft:" + names.get(i);
    }

    private static int indexIn(List<String> names, String name, String what) {
        int i = names.indexOf(name.startsWith("minecraft:") ? name.substring(10) : name);
        if (i < 0) throw new IllegalArgumentException("알 수 없는 " + what + ": " + name);
        return i;
    }

    private static String stateAt(List<String> names, int i) {
        if (i < 0 || i >= names.size()) throw new IllegalArgumentException("상태 번호 범위 밖: " + i);
        return names.get(i);
    }

    private static String registryName(String registry, int id) {
        String n = ItemContext.installed() ? ItemContext.name(registry, id) : null;
        if (n == null) throw new IllegalArgumentException(registry + " 에 없는 번호: " + id);
        return n;
    }

    private static int registryId(String registry, String name) {
        Integer id = ItemContext.installed() ? ItemContext.id(registry, name.indexOf(':') < 0 ? "minecraft:" + name : name) : null;
        if (id == null) throw new IllegalArgumentException(registry + " 에 없는 이름: " + name);
        return id;
    }

    /** 데이터 팩으로 정해지는 레지스트리(늑대·그림 종류) — 접속자에게 보낸 순서가 번호다. */
    private static String dataRegistryName(String registry, int id) {
        List<String> l = RegistrySet.current().entries(registry);
        if (id < 0 || id >= l.size()) throw new IllegalArgumentException(registry + " 에 없는 번호: " + id);
        return l.get(id);
    }

    private static int dataRegistryId(String registry, String name) {
        int i = RegistrySet.current().indexOf(registry, name.indexOf(':') < 0 ? "minecraft:" + name : name);
        if (i < 0) throw new IllegalArgumentException(registry + " 에 없는 이름: " + name);
        return i;
    }
}
