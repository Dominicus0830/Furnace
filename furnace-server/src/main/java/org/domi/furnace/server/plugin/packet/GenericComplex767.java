// E5 손 코덱: 값 타입이 크거나 종류마다 모양이 다른 12종(입자·폭발·청크·빛·명령 트리·레시피 둘·발전 과제·태그·서명 채팅·상인 거래·번들 구분자)을
// 바닐라 1.21.1 와이어 그대로 읽고 쓴다. 규칙은 GenericHand767 과 같다: 필드 이름은 Mojang 필드 이름의 snake_case, 필드 순서는 와이어 순서,
// 비트 묶음은 Boolean 필드, 조건부 부분은 Optional. 종류마다 모양이 다른 값(입자·레시피·명령 인자·위치 소스)은 type(명령 인자는 parser) 필드가
// 나머지 필드를 정한다. 값 모양 표는 docs/api/packets.md "복잡한 종류", 바닐라 근거는 docs/feature-parity/context-notes/2026-11-12-easy-nms-e5.md.
// 쓰기 검사는 "바닐라 서버가 보낼 수 있는 값"까지다 — 바닐라 클라이언트가 디코드나 적용에서 끊기는 값(빛 배열 크기, 명령 트리 순환, 같은 레시피 id …)은
// 보내기 전에 IllegalArgumentException 으로 막는다.
package org.domi.furnace.server.plugin.packet;

import static org.domi.furnace.server.plugin.packet.GenericCodec767.BLOCK_POS;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.BOOL;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.F32;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.F64;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.I64;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.IDENTIFIER;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.NBT;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.STRING;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.TEXT;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.UUID_NODE;
import static org.domi.furnace.server.plugin.packet.GenericCodec767.VARINTS;
import static org.domi.furnace.server.plugin.packet.GenericHand767.node;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.command.ArgumentTypeIds;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ComponentType;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.domi.furnace.server.plugin.ApiConvert;
import org.domi.furnace.server.plugin.packet.GenericCodec767.Compiled;
import org.domi.furnace.server.plugin.packet.GenericCodec767.Node;
import org.domi.furnace.server.plugin.packet.GenericHand767.Seq;

final class GenericComplex767 {

    private GenericComplex767() {}

    private static final Node VARINT = GenericCodec767.integer("varint", Integer.class);
    private static final Node I32 = GenericCodec767.integer("i32", Integer.class);
    private static final Node I16_INT = GenericCodec767.integer("i16", Integer.class);
    private static final Node U8 = GenericCodec767.integer("u8", Integer.class);
    private static final Compiled EMPTY = GenericCodec767.shape(List.of(), List.of(), null);

    /** 이 종류의 손 코덱(없으면 null). */
    static Compiled compile(GenericCodec767 c, PacketDef d) {
        if (d.direction() != PacketDirection.CLIENTBOUND) return null;
        return switch (d.wireName()) {
            case "bundle_delimiter" -> new Seq().build();   // 본문 없음(구분자 짝은 서버가 맞춘다 — PacketServiceImpl 이 혼자 보내기를 막는다)
            case "level_particles" -> new Seq()
                    .f("override_limiter", BOOL).f("x", F64).f("y", F64).f("z", F64)
                    .f("x_dist", F32).f("y_dist", F32).f("z_dist", F32).f("max_speed", F32).f("count", I32)
                    .f("particle", particle(c)).build();
            case "explode" -> explode(c, d);
            case "light_update" -> new Seq().f("x", VARINT).f("z", VARINT).f("light_data", LIGHT_DATA).build();
            case "level_chunk_with_light" -> new Seq().f("x", I32).f("z", I32).f("chunk_data", chunkData()).f("light_data", LIGHT_DATA).build();
            case "commands" -> commands();
            case "player_chat" -> new Seq()
                    .f("sender", UUID_NODE).f("index", VARINT).f("signature", GenericCodec767.optional(GenericHand767.fixedBytes(256), false))
                    .f("body", struct(List.of("content", "time_stamp", "salt", "last_seen"),
                            List.of(GenericHand767.string(256), I64, I64, GenericHand767.limitedList(GenericHand767.MESSAGE_SIGNATURE_PACKED, 20))))
                    .f("unsigned_content", GenericCodec767.optional(TEXT, false)).f("filter_mask", FILTER_MASK)
                    .f("chat_type", GenericHand767.CHAT_TYPE_BOUND).build();
            case "update_recipes" -> new Seq().f("recipes", recipes(c)).build();
            case "recipe" -> recipeBook();
            case "update_advancements" -> new Seq()
                    .f("reset", BOOL).f("added", GenericCodec767.list(advancement(c), false))
                    .f("removed", GenericCodec767.list(IDENTIFIER, true)).f("progress", distinctBy(PROGRESS, "id", true)).build();
            case "update_tags" -> new Seq().f("tags", TAGS).build();
            case "merchant_offers" -> new Seq()
                    .f("container_id", VARINT).f("offers", GenericCodec767.list(offer(c), false))
                    .f("villager_level", VARINT).f("villager_xp", VARINT).f("show_progress", BOOL).f("can_restock", BOOL).build();
            default -> null;
        };
    }

    // ------------------------------------------------------------------ 공통

    private static Compiled shape(List<String> names, List<Node> nodes) { return GenericCodec767.shape(names, nodes, null); }

    private static Node struct(List<String> names, List<Node> nodes) { return GenericCodec767.structNode(shape(names, nodes)); }

    private static IllegalArgumentException bad(String msg) { return new IllegalArgumentException(msg); }

    /**
     * {key: 이름, 나머지…} — 이름(와이어는 names 안 번호, varint)이 나머지 필드 모양을 정한다. shapes 에 없는 이름은 fallback 모양
     * (null 이면 그 이름은 받지 않는다). 쓸 때 이름은 identifier 규칙으로 고친다(이름공간 없으면 minecraft:).
     */
    private static Node keyed(String key, String what, List<String> names, Map<String, Compiled> shapes, Compiled fallback) {
        return node(PacketStruct.class, b -> {
            int id = Types.readVarInt(b);
            if (id < 0 || id >= names.size()) throw bad(what + " 번호가 범위 밖: " + id);
            String name = names.get(id);
            Compiled s = shapes.getOrDefault(name, fallback);
            if (s == null) throw bad(what + " " + name + " 은 아직 다루지 않는다");
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put(key, name);
            m.putAll(GenericCodec767.readStruct(s, b).asMap());
            return PacketStruct.of(m);
        }, (o, v) -> {
            PacketStruct p = (PacketStruct) v;
            if (!(p.asMap().get(key) instanceof String raw)) throw bad(what + " 에 " + key + "(String) 이 없다: " + p);
            String name = GenericCodec767.identifier(raw);
            int id = names.indexOf(name);
            if (id < 0) throw bad("모르는 " + what + ": " + raw);
            Compiled s = shapes.getOrDefault(name, fallback);
            if (s == null) throw bad(what + " " + name + " 은 아직 다루지 않는다");
            LinkedHashMap<String, Object> rest = new LinkedHashMap<>(p.asMap());
            rest.remove(key);
            Types.writeVarInt(o, id);
            GenericCodec767.writeStruct(s, PacketStruct.of(rest), o, what + "(" + name + ")");
        });
    }

    /** 원소가 묶음인 목록 — key 필드 값이 겹치면 쓰지 않는다(바닐라는 맵). 읽을 때 같은 키는 바닐라 맵처럼 마지막 값만 남긴다. */
    private static Node distinctBy(Node elem, String key) { return distinctBy(elem, key, false); }

    /** identifier 가 참이면 키를 바닐라 ResourceLocation 처럼 비교한다("item" = "minecraft:item"). */
    private static Node distinctBy(Node elem, String key, boolean identifier) {
        Node l = GenericCodec767.list(elem, false);
        return node(List.class, b -> {
            LinkedHashMap<Object, Object> m = new LinkedHashMap<>();
            for (Object x : (List<?>) l.read(b)) m.put(distinctKey(((PacketStruct) x).asMap().get(key), identifier), x);
            return List.copyOf(m.values());
        }, (o, v) -> {
            HashSet<Object> seen = new HashSet<>();
            for (Object x : (List<?>) v) {
                if (x instanceof PacketStruct s && s.has(key) && !seen.add(distinctKey(s.get(key), identifier))) {
                    throw bad("같은 " + key + " 가 두 번 있다: " + s.get(key));
                }
            }
            l.write(o, v);
        });
    }

    private static Object distinctKey(Object k, boolean identifier) {
        return identifier && k instanceof String str && str.indexOf(':') < 0 ? "minecraft:" + str : k;
    }

    /** 빈 스택을 받지 않는 아이템(바닐라 ItemStack.STREAM_CODEC — 읽기도 "Empty ItemStack not allowed" 로 실패). */
    private static Node nonEmptyItem(GenericCodec767 c) {
        Node n = c.itemStack();
        return node(ItemStack.class, b -> {
            ItemStack s = (ItemStack) n.read(b);
            if (s.isEmpty()) throw bad("빈 아이템은 올 수 없다(Empty ItemStack not allowed)");
            return s;
        }, (o, v) -> {
            if (((ItemStack) v).isEmpty()) throw bad("빈 아이템은 쓸 수 없다(바닐라 클라이언트가 Empty ItemStack not allowed 로 끊긴다)");
            n.write(o, v);
        });
    }

    /** BitSet(바닐라 writeBitSet = toLongArray — 끝의 0 long 은 빠진다). 값은 long[]; 읽기·쓰기 모두 끝의 0 을 떼어 바닐라와 같은 바이트로. */
    private static final Node BIT_SET = node(long[].class, b -> trim((long[]) GenericCodec767.LONGS.read(b)),
            (o, v) -> GenericCodec767.LONGS.write(o, trim((long[]) v)));

    private static long[] trim(long[] a) { return BitSet.valueOf(a).toLongArray(); }

    private static int bitCount(long[] a) {
        int n = 0;
        for (long x : a) n += Long.bitCount(x);
        return n;
    }

    /** 바닐라 Vector3f(f32 셋) — {x, y, z} Float. */
    private static final Node VEC3F = struct(List.of("x", "y", "z"), List.of(F32, F32, F32));

    // ------------------------------------------------------------------ 입자

    /** 위치 소스(바닐라 PositionSource.STREAM_CODEC: position_source_type 번호 + 내용) — {type:"minecraft:block", pos} · {type:"minecraft:entity", entity_id, y_offset}. */
    private static final Node POSITION_SOURCE = keyed("type", "위치 소스", List.of("minecraft:block", "minecraft:entity"), Map.of(
            "minecraft:block", shape(List.of("pos"), List.of(BLOCK_POS)),
            "minecraft:entity", shape(List.of("entity_id", "y_offset"), List.of(VARINT, F32))), null);

    /** 먼지 입자 크기 — 바닐라 ScalableParticleOptionsBase 생성자가 0.01~4 로 죈다(바닐라 서버는 그 밖의 값을 보낼 수 없다). */
    private static final Node SCALE = node(Float.class, b -> F32.read(b), (o, v) -> {
        float f = (Float) v;
        o.writeFloat(f < 0.01F ? 0.01F : Math.min(f, 4.0F));
    });

    /**
     * 입자(바닐라 ParticleTypes.STREAM_CODEC: particle_type 번호 + 종류별 내용) — {type, …}. 내용이 있는 종류:
     * block·block_marker·falling_dust·dust_pillar {state}, dust {color{x,y,z}, scale}, dust_color_transition {from_color, to_color, scale},
     * entity_effect {color(ARGB i32)}, item {item_stack(빈 것 안 됨)}, sculk_charge {roll}, shriek {delay}, vibration {destination, arrival_in_ticks}.
     * 나머지는 {type} 뿐.
     */
    private static Node particle(GenericCodec767 c) {
        Compiled block = shape(List.of("state"), List.of(c.blockState()));
        Map<String, Compiled> shapes = new HashMap<>();
        for (String b : List.of("minecraft:block", "minecraft:block_marker", "minecraft:falling_dust", "minecraft:dust_pillar")) shapes.put(b, block);
        shapes.put("minecraft:dust", shape(List.of("color", "scale"), List.of(VEC3F, SCALE)));
        shapes.put("minecraft:dust_color_transition", shape(List.of("from_color", "to_color", "scale"), List.of(VEC3F, VEC3F, SCALE)));
        shapes.put("minecraft:entity_effect", shape(List.of("color"), List.of(I32)));
        shapes.put("minecraft:item", shape(List.of("item_stack"), List.of(nonEmptyItem(c))));
        shapes.put("minecraft:sculk_charge", shape(List.of("roll"), List.of(F32)));
        shapes.put("minecraft:shriek", shape(List.of("delay"), List.of(VARINT)));
        shapes.put("minecraft:vibration", shape(List.of("destination", "arrival_in_ticks"), List.of(POSITION_SOURCE, VARINT)));
        return keyed("type", "입자", ParticleOptions.Registry.names(), shapes, EMPTY);
    }

    // ------------------------------------------------------------------ 폭발

    /** 바닐라 1.21.1 클라이언트의 레지스트리 이름(정적 + 동기화). registries/client-registries-1.21.1.txt. */
    private static final class ClientRegistries {
        static final Set<String> NAMES = load();

        private static Set<String> load() {
            try (var in = GenericComplex767.class.getClassLoader().getResourceAsStream("registries/client-registries-1.21.1.txt")) {
                if (in == null) throw new IllegalStateException("registries/client-registries-1.21.1.txt 없음");
                Set<String> names = new HashSet<>();
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    line = line.strip();
                    if (!line.isEmpty() && !line.startsWith("#")) names.add(line);
                }
                return Set.copyOf(names);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    private static void clientRegistry(Object key, String where) {
        if (!(key instanceof String k)) return;   // 값 종류 검사는 코덱이 한다
        String id = GenericCodec767.identifier(k);
        if (!ClientRegistries.NAMES.contains(id)) throw bad(where + " 는 바닐라 클라이언트에 있는 레지스트리여야 한다(없으면 접속이 끊긴다): " + k);
    }

    /** 바닐라 Mth.floor(double). */
    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /**
     * x·y·z, power, to_blow(부서진 칸 — 와이어는 floor(x,y,z) 에서 i8 셋 차이, 값은 절대 좌표 BlockPos), 밀림 셋, block_interaction,
     * small_explosion_particles·large_explosion_particles(입자), explosion_sound(소리). 차이가 i8 밖이면 바닐라는 잘라 엉뚱한 칸을 보내므로 쓰지 않는다.
     */
    private static Compiled explode(GenericCodec767 c, PacketDef d) {
        Node interaction = c.fieldNode(d, "blockInteraction"), sound = c.fieldNode(d, "explosionSound"), particle = particle(c);
        Node toBlow = GenericCodec767.list(BLOCK_POS, false);
        List<String> names = List.of("x", "y", "z", "power", "to_blow", "knockback_x", "knockback_y", "knockback_z", "block_interaction",
                "small_explosion_particles", "large_explosion_particles", "explosion_sound");
        List<Node> nodes = List.of(F64, F64, F64, F32, toBlow, F32, F32, F32, interaction, particle, particle, sound);
        return GenericCodec767.shape(names, nodes, GenericHand767.hand((b, m) -> {
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            m.put("x", x);
            m.put("y", y);
            m.put("z", z);
            m.put("power", b.readFloat());
            int fx = floor(x), fy = floor(y), fz = floor(z);
            int n = Types.readVarInt(b);
            if (n < 0 || (long) n * 3 > b.readableBytes()) throw bad("칸 수 오류: " + n);
            List<Object> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new BlockPos(b.readByte() + fx, b.readByte() + fy, b.readByte() + fz));
            m.put("to_blow", Collections.unmodifiableList(l));
            m.put("knockback_x", b.readFloat());
            m.put("knockback_y", b.readFloat());
            m.put("knockback_z", b.readFloat());
            m.put("block_interaction", interaction.read(b));
            m.put("small_explosion_particles", particle.read(b));
            m.put("large_explosion_particles", particle.read(b));
            m.put("explosion_sound", sound.read(b));
        }, (o, m) -> {
            double x = (Double) m.get("x"), y = (Double) m.get("y"), z = (Double) m.get("z");
            long fx = floor(x), fy = floor(y), fz = floor(z);
            List<?> l = (List<?>) m.get("to_blow");
            for (int i = 0; i < l.size(); i++) {
                if (!(l.get(i) instanceof BlockPos p)) throw bad("to_blow[" + i + "] 은 BlockPos 이어야 한다: " + l.get(i));
                long dx = p.x() - fx, dy = p.y() - fy, dz = p.z() - fz;
                if (dx < -128 || dx > 127 || dy < -128 || dy > 127 || dz < -128 || dz > 127) {
                    throw bad("to_blow[" + i + "] 이 폭발 중심 칸에서 i8(-128~127) 보다 멀다: " + p);
                }
            }
            o.writeDouble(x);
            o.writeDouble(y);
            o.writeDouble(z);
            o.writeFloat((Float) m.get("power"));
            Types.writeVarInt(o, l.size());
            for (Object e : l) {
                BlockPos p = (BlockPos) e;
                o.writeByte((int) (p.x() - fx));
                o.writeByte((int) (p.y() - fy));
                o.writeByte((int) (p.z() - fz));
            }
            o.writeFloat((Float) m.get("knockback_x"));
            o.writeFloat((Float) m.get("knockback_y"));
            o.writeFloat((Float) m.get("knockback_z"));
            interaction.write(o, m.get("block_interaction"));
            particle.write(o, m.get("small_explosion_particles"));
            particle.write(o, m.get("large_explosion_particles"));
            sound.write(o, m.get("explosion_sound"));
        }));
    }

    // ------------------------------------------------------------------ 청크·빛

    /** 빛 구역 배열(바닐라 byteArray(2048) — 읽기는 2048 까지). 받은 클라이언트 DataLayer 는 꼭 2048 바이트여야 해서 쓸 때는 2048 만 받는다. */
    private static final Node DATA_LAYER = node(byte[].class, b -> GenericHand767.bytes(2048).read(b), (o, v) -> {
        byte[] a = (byte[]) v;
        if (a.length != 2048) throw bad("빛 배열은 2048 바이트여야 한다(바닐라 DataLayer): " + a.length);
        GenericHand767.bytes(2048).write(o, a);
    });

    private static final Compiled LIGHT = shape(
            List.of("sky_y_mask", "block_y_mask", "empty_sky_y_mask", "empty_block_y_mask", "sky_updates", "block_updates"),
            List.of(BIT_SET, BIT_SET, BIT_SET, BIT_SET, GenericCodec767.list(DATA_LAYER, false), GenericCodec767.list(DATA_LAYER, false)));

    /**
     * ClientboundLightUpdatePacketData: 마스크 넷(long[] — 비트 i = 맨 아래 빛 구역에서 i 번째), 하늘·블록 빛 배열 목록.
     * 클라이언트는 마스크에 선 비트마다 배열을 하나씩 꺼내므로(모자라면 NoSuchElementException 으로 끊긴다) 배열 수 = 마스크 비트 수여야 쓴다.
     */
    private static final Node LIGHT_DATA = node(PacketStruct.class, b -> GenericCodec767.readStruct(LIGHT, b), (o, v) -> {
        PacketStruct s = (PacketStruct) v;
        Map<String, Object> m = GenericCodec767.checked(LIGHT, s, "light_data");
        for (String layer : List.of("sky", "block")) {
            int bits = bitCount(trim((long[]) m.get(layer + "_y_mask"))), arrays = ((List<?>) m.get(layer + "_updates")).size();
            if (bits != arrays) throw bad("light_data." + layer + "_updates 는 " + layer + "_y_mask 의 비트 수(" + bits + ")만큼 있어야 한다: " + arrays);
        }
        GenericCodec767.writeStruct(LIGHT, s, o, "light_data");
    });

    /**
     * ClientboundLevelChunkPacketData: heightmaps(NBT), buffer(구역 데이터 바이트 — 바닐라 상한 2 MiB, 내용은 그대로 넘긴다),
     * block_entities_data[{packed_xz(구역 안 x&lt;&lt;4|z, u8), y(i16), type(block_entity_type), tag(없으면 Optional.empty)}].
     */
    private static Node chunkData() {
        Node be = struct(List.of("packed_xz", "y", "type", "tag"),
                List.of(U8, I16_INT, GenericCodec767.registryId("minecraft:block_entity_type"), GenericCodec767.NULLABLE_NBT));
        return struct(List.of("heightmaps", "buffer", "block_entities_data"), List.of(NBT, GenericHand767.bytes(2097152), GenericCodec767.list(be, false)));
    }

    // ------------------------------------------------------------------ 명령 트리

    private static final List<String> NODE_TYPES = List.of("ROOT", "LITERAL", "ARGUMENT");

    /** command_argument_type 순서(1.21.1). */
    private static final List<String> PARSERS;

    static {
        List<String> l = new ArrayList<>();
        for (int i = 0; !ArgumentTypeIds.nameOf(i).startsWith("id:"); i++) l.add(ArgumentTypeIds.nameOf(i));
        PARSERS = List.copyOf(l);
    }

    /** 바닐라 ArgumentUtils 수 범위: 플래그 바이트(1 최솟값, 2 최댓값) 다음 있는 것만. */
    private static Compiled numberRange(Node n) {
        Node opt = GenericCodec767.optional(n, false);
        return GenericCodec767.shape(List.of("min", "max"), List.of(opt, opt), GenericHand767.hand((b, m) -> {
            int f = b.readByte();
            m.put("min", (f & 1) != 0 ? Optional.of(n.read(b)) : Optional.empty());
            m.put("max", (f & 2) != 0 ? Optional.of(n.read(b)) : Optional.empty());
        }, (o, m) -> {
            Optional<?> min = (Optional<?>) m.get("min"), max = (Optional<?>) m.get("max");
            for (Optional<?> x : List.of(min, max)) if (x.isPresent() && !n.accepts(x.get())) throw bad("min·max 는 " + n.kind().getSimpleName() + " 이어야 한다: " + x.get());
            o.writeByte((min.isPresent() ? 1 : 0) | (max.isPresent() ? 2 : 0));
            if (min.isPresent()) n.write(o, min.get());
            if (max.isPresent()) n.write(o, max.get());
        }));
    }

    /**
     * 명령 인자 종류 {parser, …}: brigadier:float·double·integer·long {min, max}(Optional), brigadier:string {type}(SINGLE_WORD·QUOTABLE_PHRASE·GREEDY_PHRASE),
     * minecraft:entity {single, players_only}, minecraft:score_holder {multiple}, minecraft:time {min}, minecraft:resource·resource_key·resource_or_tag·
     * resource_or_tag_key {registry_key}. 나머지는 {parser} 뿐.
     */
    private static final Node ARGUMENT_TYPE;

    static {
        Map<String, Compiled> s = new HashMap<>();
        s.put("brigadier:float", numberRange(F32));
        s.put("brigadier:double", numberRange(F64));
        s.put("brigadier:integer", numberRange(I32));
        s.put("brigadier:long", numberRange(I64));
        s.put("brigadier:string", shape(List.of("type"), List.of(GenericCodec767.enumNode("StringArgumentType$StringType",
                List.of("SINGLE_WORD", "QUOTABLE_PHRASE", "GREEDY_PHRASE"), "varint"))));
        s.put("minecraft:entity", new Seq().flags("single", "players_only").build());
        s.put("minecraft:score_holder", new Seq().flags("multiple").build());
        s.put("minecraft:time", shape(List.of("min"), List.of(I32)));
        Compiled registry = shape(List.of("registry_key"), List.of(IDENTIFIER));
        for (String r : List.of("minecraft:resource_or_tag", "minecraft:resource_or_tag_key", "minecraft:resource", "minecraft:resource_key")) s.put(r, registry);
        ARGUMENT_TYPE = keyed("parser", "명령 인자 종류", PARSERS, s, EMPTY);
    }

    /** 받은 클라이언트가 생성자에서 레지스트리를 찾는 인자 종류(resource_key·resource_or_tag_key 는 찾지 않는다). */
    private static final Set<String> LOOKED_UP_PARSERS = Set.of("minecraft:resource", "minecraft:resource_or_tag");

    private static final Compiled ENTRY = shape(List.of("type", "executable", "children", "redirect", "name", "argument_type", "suggestion_id"),
            List.of(STRING, BOOL, VARINTS, GenericCodec767.optional(VARINT, false), GenericCodec767.optional(STRING, false),
                    GenericCodec767.optional(ARGUMENT_TYPE, false), GenericCodec767.optional(IDENTIFIER, false)));

    /**
     * 노드 하나: 플래그 바이트(종류 0 ROOT·1 LITERAL·2 ARGUMENT, 4 실행 가능, 8 리다이렉트, 16 제안 이름) → children(번호 목록) → redirect → name(LITERAL·ARGUMENT)
     * → argument_type·suggestion_id(ARGUMENT). 값 {type, executable, children(int[]), redirect, name, argument_type, suggestion_id}.
     */
    private static final Node ENTRY_NODE = node(PacketStruct.class, b -> {
        int flags = b.readByte();
        int type = flags & 3;
        if (type == 3) throw bad("명령 노드 종류 3 은 없다");
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("type", NODE_TYPES.get(type));
        m.put("executable", (flags & 4) != 0);
        m.put("children", VARINTS.read(b));
        m.put("redirect", (flags & 8) != 0 ? Optional.of(VARINT.read(b)) : Optional.empty());
        m.put("name", type != 0 ? Optional.of(STRING.read(b)) : Optional.empty());
        m.put("argument_type", type == 2 ? Optional.of(ARGUMENT_TYPE.read(b)) : Optional.empty());
        m.put("suggestion_id", type == 2 && (flags & 16) != 0 ? Optional.of(IDENTIFIER.read(b)) : Optional.empty());
        return PacketStruct.of(m);
    }, (o, v) -> {
        Map<String, Object> m = GenericCodec767.checked(ENTRY, (PacketStruct) v, "entry");
        int type = NODE_TYPES.indexOf(m.get("type"));
        if (type < 0) throw bad("type 은 " + NODE_TYPES + " 가운데 하나: " + m.get("type"));
        Optional<?> redirect = (Optional<?>) m.get("redirect"), name = (Optional<?>) m.get("name"), arg = (Optional<?>) m.get("argument_type"),
                suggestion = (Optional<?>) m.get("suggestion_id");
        if (name.isPresent() != (type != 0)) throw bad("name 은 LITERAL·ARGUMENT 노드에만 있어야 한다: " + m.get("type"));
        if (arg.isPresent() != (type == 2)) throw bad("argument_type 은 ARGUMENT 노드에만 있어야 한다: " + m.get("type"));
        if (suggestion.isPresent() && type != 2) throw bad("suggestion_id 는 ARGUMENT 노드에만 있을 수 있다: " + m.get("type"));
        int flags = type | ((Boolean) m.get("executable") ? 4 : 0) | (redirect.isPresent() ? 8 : 0) | (suggestion.isPresent() ? 16 : 0);
        o.writeByte(flags);
        VARINTS.write(o, m.get("children"));
        if (redirect.isPresent()) VARINT.write(o, redirect.get());
        if (name.isPresent()) STRING.write(o, name.get());
        if (arg.isPresent()) {
            // resource·resource_or_tag 는 받은 클라이언트가 인자를 만들 때 lookupOrThrow 로 레지스트리를 찾는다(없으면 끊긴다)
            if (arg.get() instanceof PacketStruct a && a.has("parser") && a.has("registry_key")
                    && LOOKED_UP_PARSERS.contains(GenericCodec767.identifier(String.valueOf(a.get("parser"))))) {
                clientRegistry(a.get("registry_key"), "argument_type.registry_key");
            }
            ARGUMENT_TYPE.write(o, arg.get());
        }
        if (suggestion.isPresent()) IDENTIFIER.write(o, suggestion.get());
    });

    /**
     * entries(노드 목록), root_index(와이어에서는 목록 뒤). 쓸 때 바닐라 클라이언트가 받는 조건을 본다: 번호가 목록 안, 뿌리가 ROOT,
     * 리다이렉트·자식 그래프에 순환이 없을 것(바닐라 validateEntries — 아니면 "Server sent an impossible command tree" 로 끊긴다).
     */
    private static Compiled commands() {
        Node entries = GenericCodec767.list(ENTRY_NODE, false);
        return GenericCodec767.shape(List.of("entries", "root_index"), List.of(entries, VARINT), GenericHand767.hand((b, m) -> {
            m.put("entries", entries.read(b));
            m.put("root_index", VARINT.read(b));
        }, (o, m) -> {
            List<?> l = (List<?>) m.get("entries");
            int n = l.size(), root = (Integer) m.get("root_index");
            List<Map<String, Object>> es = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                if (!(l.get(i) instanceof PacketStruct s)) throw bad("entries[" + i + "] 은 PacketStruct 이어야 한다: " + l.get(i));
                es.add(GenericCodec767.checked(ENTRY, s, "entries[" + i + "]"));
            }
            if (root < 0 || root >= n) throw bad("root_index 가 목록 밖: " + root + " (노드 " + n + "개)");
            if (!"ROOT".equals(es.get(root).get("type"))) throw bad("root_index 는 ROOT 노드여야 한다: " + es.get(root).get("type"));
            for (int i = 0; i < n; i++) {
                for (int ch : (int[]) es.get(i).get("children")) if (ch < 0 || ch >= n) throw bad("entries[" + i + "].children 의 번호가 목록 밖: " + ch);
                Optional<?> r = (Optional<?>) es.get(i).get("redirect");
                if (r.isPresent() && (!(r.get() instanceof Integer ri) || ri < 0 || ri >= n)) throw bad("entries[" + i + "].redirect 가 목록 밖: " + r.get());
            }
            settle(es, (e, left) -> ((Optional<?>) e.get("redirect")).map(r -> !left.contains((Integer) r)).orElse(true), "리다이렉트");
            settle(es, (e, left) -> {
                for (int ch : (int[]) e.get("children")) if (left.contains(ch)) return false;
                return true;
            }, "자식");
            entries.write(o, l);
            VARINT.write(o, root);
        }));
    }

    /** 바닐라 validateEntries: 남은 노드 가운데 조건을 채운 것을 빼기를 더 뺄 것이 없을 때까지 — 남으면 순환이다. */
    private static void settle(List<Map<String, Object>> es, BiPredicate<Map<String, Object>, Set<Integer>> ready, String what) {
        Set<Integer> left = new HashSet<>();
        for (int i = 0; i < es.size(); i++) left.add(i);
        boolean moved = true;
        while (!left.isEmpty() && moved) moved = left.removeIf(i -> ready.test(es.get(i), left));
        if (!left.isEmpty()) throw bad("명령 트리의 " + what + " 에 순환이 있다(바닐라 클라이언트가 impossible command tree 로 끊긴다): 노드 " + left);
    }

    // ------------------------------------------------------------------ 채팅

    private static final Node FILTER_TYPE = GenericCodec767.enumNode("FilterMask$Type", List.of("PASS_THROUGH", "FULLY_FILTERED", "PARTIALLY_FILTERED"), "varint");

    /** FilterMask: {type, mask} — mask(BitSet long[])는 PARTIALLY_FILTERED 일 때만. */
    private static final Node FILTER_MASK = node(PacketStruct.class, b -> {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Object t = FILTER_TYPE.read(b);
        m.put("type", t);
        m.put("mask", "PARTIALLY_FILTERED".equals(t) ? Optional.of(BIT_SET.read(b)) : Optional.empty());
        return PacketStruct.of(m);
    }, (o, v) -> {
        PacketStruct s = (PacketStruct) v;
        if (!s.names().equals(List.of("type", "mask")) || !(s.asMap().get("mask") instanceof Optional<?> mask)) {
            throw bad("filter_mask 는 {type: String, mask: Optional<long[]>} 이어야 한다: " + s);
        }
        Object t = s.asMap().get("type");
        if (mask.isPresent() != "PARTIALLY_FILTERED".equals(t)) throw bad("filter_mask.mask 는 type 이 PARTIALLY_FILTERED 일 때만 있어야 한다: " + t);
        if (mask.isPresent() && !(mask.get() instanceof long[])) throw bad("filter_mask.mask 는 long[] 이어야 한다");
        FILTER_TYPE.write(o, t);
        if (mask.isPresent()) BIT_SET.write(o, mask.get());
    });

    // ------------------------------------------------------------------ 레시피

    /** recipe_serializer 등록 순서(1.21.1 RecipeSerializer). */
    private static final List<String> RECIPE_SERIALIZERS = List.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless",
            "minecraft:crafting_special_armordye", "minecraft:crafting_special_bookcloning", "minecraft:crafting_special_mapcloning",
            "minecraft:crafting_special_mapextending", "minecraft:crafting_special_firework_rocket", "minecraft:crafting_special_firework_star",
            "minecraft:crafting_special_firework_star_fade", "minecraft:crafting_special_tippedarrow", "minecraft:crafting_special_bannerduplicate",
            "minecraft:crafting_special_shielddecoration", "minecraft:crafting_special_shulkerboxcoloring", "minecraft:crafting_special_suspiciousstew",
            "minecraft:crafting_special_repairitem", "minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking",
            "minecraft:stonecutting", "minecraft:smithing_transform", "minecraft:smithing_trim", "minecraft:crafting_decorated_pot");

    private static final Node CRAFTING_CATEGORY = GenericCodec767.enumNode("CraftingBookCategory", List.of("BUILDING", "REDSTONE", "EQUIPMENT", "MISC"), "varint");
    private static final Node COOKING_CATEGORY = GenericCodec767.enumNode("CookingBookCategory", List.of("FOOD", "BLOCKS", "MISC"), "varint");

    /**
     * 레시피 {id, type(recipe_serializer), …}. 재료(ingredient)는 아이템 목록 List&lt;ItemStack&gt;(빈 아이템 없음, 빈 목록 = 빈 칸).
     * crafting_shaped {group, category, width, height, ingredients(width×height), result, show_notification} — 가로·세로 1~3,
     * crafting_shapeless {group, category, ingredients(1~9), result}, smelting·blasting·smoking·campfire_cooking {group, category, ingredient, result, experience, cooking_time},
     * stonecutting {group, ingredient, result}, smithing_transform {template, base, addition, result}, smithing_trim {template, base, addition},
     * crafting_special_*·crafting_decorated_pot {category}. 같은 id 두 번은 쓰지 않는다(바닐라 클라이언트 RecipeManager 가 중복 키로 끊긴다).
     */
    private static Node recipes(GenericCodec767 c) {
        Node item = nonEmptyItem(c);
        Node ingredient = GenericCodec767.list(item, false);
        Node ingredients = GenericCodec767.list(ingredient, false);
        Map<String, Compiled> s = new HashMap<>();
        s.put("minecraft:crafting_shaped", shaped(item, ingredient));
        s.put("minecraft:crafting_shapeless", GenericCodec767.shape(List.of("group", "category", "ingredients", "result"),
                List.of(STRING, CRAFTING_CATEGORY, ingredients, item), GenericHand767.hand((b, m) -> {
                    m.put("group", STRING.read(b));
                    m.put("category", CRAFTING_CATEGORY.read(b));
                    m.put("ingredients", ingredients.read(b));
                    m.put("result", item.read(b));
                }, (o, m) -> {
                    int n = ((List<?>) m.get("ingredients")).size();
                    if (n < 1 || n > 9) throw bad("crafting_shapeless 재료는 1~9개: " + n);
                    STRING.write(o, m.get("group"));
                    CRAFTING_CATEGORY.write(o, m.get("category"));
                    ingredients.write(o, m.get("ingredients"));
                    item.write(o, m.get("result"));
                })));
        Compiled cooking = shape(List.of("group", "category", "ingredient", "result", "experience", "cooking_time"),
                List.of(STRING, COOKING_CATEGORY, ingredient, item, F32, VARINT));
        for (String t : List.of("minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking")) s.put(t, cooking);
        s.put("minecraft:stonecutting", shape(List.of("group", "ingredient", "result"), List.of(STRING, ingredient, item)));
        s.put("minecraft:smithing_transform", shape(List.of("template", "base", "addition", "result"), List.of(ingredient, ingredient, ingredient, item)));
        s.put("minecraft:smithing_trim", shape(List.of("template", "base", "addition"), List.of(ingredient, ingredient, ingredient)));
        Compiled special = shape(List.of("category"), List.of(CRAFTING_CATEGORY));
        for (String t : RECIPE_SERIALIZERS) if (t.contains(":crafting_special_") || t.equals("minecraft:crafting_decorated_pot")) s.put(t, special);
        Node body = keyed("type", "레시피 종류", RECIPE_SERIALIZERS, s, null);
        Node holder = node(PacketStruct.class, b -> {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("id", IDENTIFIER.read(b));
            m.putAll(((PacketStruct) body.read(b)).asMap());
            return PacketStruct.of(m);
        }, (o, v) -> {
            PacketStruct p = (PacketStruct) v;
            if (!(p.asMap().get("id") instanceof String id)) throw bad("레시피에 id(String) 가 없다: " + p);
            LinkedHashMap<String, Object> rest = new LinkedHashMap<>(p.asMap());
            rest.remove("id");
            IDENTIFIER.write(o, id);
            body.write(o, PacketStruct.of(rest));
        });
        Node list = GenericCodec767.list(holder, false);
        return node(List.class, list::read, (o, v) -> {
            HashSet<String> seen = new HashSet<>();
            for (Object x : (List<?>) v) {
                if (x instanceof PacketStruct p && p.asMap().get("id") instanceof String id && !seen.add(GenericCodec767.identifier(id))) {
                    throw bad("같은 레시피 id 가 두 번 있다(바닐라 클라이언트가 끊긴다): " + id);
                }
            }
            list.write(o, v);
        });
    }

    /** crafting_shaped: 재료 수가 width×height 로 정해진다(앞에 개수가 없다). */
    private static Compiled shaped(Node item, Node ingredient) {
        Node ingredients = node(List.class, null, null);
        return GenericCodec767.shape(List.of("group", "category", "width", "height", "ingredients", "result", "show_notification"),
                List.of(STRING, CRAFTING_CATEGORY, VARINT, VARINT, ingredients, item, BOOL), GenericHand767.hand((b, m) -> {
                    m.put("group", STRING.read(b));
                    m.put("category", CRAFTING_CATEGORY.read(b));
                    int w = Types.readVarInt(b), h = Types.readVarInt(b);
                    long n = (long) w * h;
                    if (w < 0 || h < 0 || n > b.readableBytes()) throw bad("모양 크기 오류: " + w + "×" + h);
                    m.put("width", w);
                    m.put("height", h);
                    List<Object> l = new ArrayList<>((int) n);
                    for (int i = 0; i < n; i++) l.add(ingredient.read(b));
                    m.put("ingredients", Collections.unmodifiableList(l));
                    m.put("result", item.read(b));
                    m.put("show_notification", BOOL.read(b));
                }, (o, m) -> {
                    int w = (Integer) m.get("width"), h = (Integer) m.get("height");
                    List<?> l = (List<?>) m.get("ingredients");
                    if (w < 1 || w > 3 || h < 1 || h > 3) throw bad("crafting_shaped 가로·세로는 1~3: " + w + "×" + h);
                    if (l.size() != w * h) throw bad("crafting_shaped 재료 수는 width×height(" + w * h + ")여야 한다: " + l.size());
                    STRING.write(o, m.get("group"));
                    CRAFTING_CATEGORY.write(o, m.get("category"));
                    Types.writeVarInt(o, w);
                    Types.writeVarInt(o, h);
                    for (int i = 0; i < l.size(); i++) {
                        if (!(l.get(i) instanceof List<?>)) throw bad("ingredients[" + i + "] 은 List<ItemStack> 이어야 한다: " + l.get(i));
                        ingredient.write(o, l.get(i));
                    }
                    item.write(o, m.get("result"));
                    BOOL.write(o, m.get("show_notification"));
                }));
    }

    private static final List<String> BOOK_STATES = List.of("INIT", "ADD", "REMOVE");

    /**
     * 레시피 책(ClientboundRecipePacket): state(INIT·ADD·REMOVE), book_settings{crafting_open, crafting_filtering, furnace_…, blast_furnace_…, smoker_…},
     * recipes(이름 목록), to_highlight(INIT 일 때만).
     */
    private static Compiled recipeBook() {
        Node state = GenericCodec767.enumNode("ClientboundRecipePacket$State", BOOK_STATES, "varint");
        List<String> bits = new ArrayList<>();
        for (String t : List.of("crafting", "furnace", "blast_furnace", "smoker")) {
            bits.add(t + "_open");
            bits.add(t + "_filtering");
        }
        Node settings = struct(bits, Collections.nCopies(bits.size(), BOOL));
        Node names = GenericCodec767.list(IDENTIFIER, false);
        return GenericCodec767.shape(List.of("state", "book_settings", "recipes", "to_highlight"),
                List.of(state, settings, names, GenericCodec767.optional(names, false)), GenericHand767.hand((b, m) -> {
                    Object s = state.read(b);
                    m.put("state", s);
                    m.put("book_settings", settings.read(b));
                    m.put("recipes", names.read(b));
                    m.put("to_highlight", "INIT".equals(s) ? Optional.of(names.read(b)) : Optional.empty());
                }, (o, m) -> {
                    Object s = m.get("state");
                    Optional<?> hl = (Optional<?>) m.get("to_highlight");
                    if (hl.isPresent() != "INIT".equals(s)) throw bad("to_highlight 는 state 가 INIT 일 때만 있어야 한다: " + s);
                    if (hl.isPresent() && !(hl.get() instanceof List<?>)) throw bad("to_highlight 는 List<String> 이어야 한다");
                    state.write(o, s);
                    settings.write(o, m.get("book_settings"));
                    names.write(o, m.get("recipes"));
                    if (hl.isPresent()) names.write(o, hl.get());
                }));
    }

    // ------------------------------------------------------------------ 발전 과제

    private static final Node ADVANCEMENT_TYPE = GenericCodec767.enumNode("AdvancementType", List.of("TASK", "CHALLENGE", "GOAL"), "varint");

    /**
     * DisplayInfo: title, description, icon(빈 것 안 됨), type(TASK·CHALLENGE·GOAL), 플래그 i32(1 배경 있음·2 토스트·4 숨김) 자리에 show_toast·hidden,
     * background(선택), x, y.
     */
    private static Node display(GenericCodec767 c) {
        Node icon = nonEmptyItem(c);
        Compiled s = shape(List.of("title", "description", "icon", "type", "show_toast", "hidden", "background", "x", "y"),
                List.of(TEXT, TEXT, icon, ADVANCEMENT_TYPE, BOOL, BOOL, GenericCodec767.optional(IDENTIFIER, false), F32, F32));
        return node(PacketStruct.class, b -> {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("title", TEXT.read(b));
            m.put("description", TEXT.read(b));
            m.put("icon", icon.read(b));
            m.put("type", ADVANCEMENT_TYPE.read(b));
            int f = b.readInt();
            m.put("show_toast", (f & 2) != 0);
            m.put("hidden", (f & 4) != 0);
            m.put("background", (f & 1) != 0 ? Optional.of(IDENTIFIER.read(b)) : Optional.empty());
            m.put("x", b.readFloat());
            m.put("y", b.readFloat());
            return PacketStruct.of(m);
        }, (o, v) -> {
            Map<String, Object> m = GenericCodec767.checked(s, (PacketStruct) v, "display");
            Optional<?> bg = (Optional<?>) m.get("background");
            if (bg.isPresent() && !(bg.get() instanceof String)) throw bad("display.background 는 String 이어야 한다: " + bg.get());
            TEXT.write(o, m.get("title"));
            TEXT.write(o, m.get("description"));
            icon.write(o, m.get("icon"));
            ADVANCEMENT_TYPE.write(o, m.get("type"));
            o.writeInt((bg.isPresent() ? 1 : 0) | ((Boolean) m.get("show_toast") ? 2 : 0) | ((Boolean) m.get("hidden") ? 4 : 0));
            if (bg.isPresent()) IDENTIFIER.write(o, bg.get());
            o.writeFloat((Float) m.get("x"));
            o.writeFloat((Float) m.get("y"));
        });
    }

    /** AdvancementHolder: {id, parent, display, requirements(List&lt;List&lt;String&gt;&gt;), sends_telemetry_event}. */
    private static Node advancement(GenericCodec767 c) {
        return struct(List.of("id", "parent", "display", "requirements", "sends_telemetry_event"),
                List.of(IDENTIFIER, GenericCodec767.optional(IDENTIFIER, false), GenericCodec767.optional(display(c), false),
                        GenericCodec767.list(GenericCodec767.list(STRING, false), false), BOOL));
    }

    /** 진행도: {id, criteria[{name, obtained(달성 시각 epoch ms, 없으면 empty)}]} — 바닐라는 맵이라 같은 id·name 은 쓰지 않는다. */
    private static final Node PROGRESS = struct(List.of("id", "criteria"), List.of(IDENTIFIER,
            distinctBy(struct(List.of("name", "obtained"), List.of(STRING, GenericCodec767.optional(I64, false))), "name")));

    // ------------------------------------------------------------------ 태그

    /** 태그: [{registry, tags[{name, entries(그 레지스트리 번호 int[])}]}] — 바닐라는 맵이라 같은 registry·name 은 쓰지 않는다. */
    private static final Node TAGS = clientRegistries(distinctBy(struct(List.of("registry", "tags"), List.of(IDENTIFIER,
            distinctBy(struct(List.of("name", "entries"), List.of(IDENTIFIER, VARINTS)), "name", true))), "registry", true));

    /** 태그 묶음의 registry 는 클라이언트에 있는 레지스트리여야 한다(TagCollector 가 registryOrThrow — 빈 목록이라도 끊긴다). */
    private static Node clientRegistries(Node tags) {
        return node(List.class, tags::read, (o, v) -> {
            for (Object x : (List<?>) v) if (x instanceof PacketStruct p && p.has("registry")) clientRegistry(p.get("registry"), "registry");
            tags.write(o, v);
        });
    }

    // ------------------------------------------------------------------ 상인 거래

    /**
     * ItemCost(바닐라: 아이템 번호, 개수, 같아야 하는 컴포넌트 목록) — 값은 ItemStack(종류·개수·components = 조건 컴포넌트).
     * 지운 컴포넌트(!x)는 조건으로 나타낼 수 없어 쓰지 않는다. 컴포넌트 값은 그 종류의 네트워크 코덱 그대로.
     */
    private static Node itemCost(GenericCodec767 c) {
        return node(ItemStack.class, b -> {
            int item = Types.readVarInt(b);
            if (ItemContext.name("minecraft:item", item) == null) throw bad("없는 아이템 번호: " + item);
            int count = Types.readVarInt(b);
            if (count <= 0) throw bad("거래 값 개수는 1 이상: " + count);
            int n = Types.readVarInt(b);
            if (n < 0 || n > b.readableBytes()) throw bad("컴포넌트 수 오류: " + n);
            LinkedHashMap<String, Object> e = new LinkedHashMap<>();
            try {
                for (int i = 0; i < n; i++) {
                    ComponentType t = DataComponents.byNetworkId(Types.readVarInt(b));
                    if (e.containsKey(t.name())) throw bad("같은 조건 컴포넌트가 두 번 있다: " + t.name());
                    e.put(t.name(), t.network().read(b));
                }
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw bad("조건 컴포넌트를 읽지 못했다: " + ex.getMessage());
            }
            return ApiConvert.item(new org.domi.furnace.server.entity.ItemStack(item, count, ComponentPatch.ofNormalized(e)), c.itemRegistry());
        }, (o, v) -> {
            ItemStack a = (ItemStack) v;
            if (a.isEmpty()) throw bad("거래 값은 빈 아이템일 수 없다");
            if (!a.removedComponents().isEmpty()) throw bad("거래 값에 지운 컴포넌트를 둘 수 없다(바닐라 조건은 '같아야 하는 컴포넌트'뿐): " + a.removedComponents());
            org.domi.furnace.server.entity.ItemStack s;
            try {
                s = ApiConvert.item(a, c.itemRegistry());
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw bad("거래 값 컴포넌트를 읽지 못했다: " + ex.getMessage());
            }
            Types.writeVarInt(o, s.itemId());
            Types.writeVarInt(o, s.count());
            Types.writeVarInt(o, s.patch().size());
            for (var e : s.patch().entries().entrySet()) {
                ComponentType t = DataComponents.require(e.getKey());
                Types.writeVarInt(o, DataComponents.networkId(t));
                t.network().write(o, e.getValue());
            }
        });
    }

    /**
     * MerchantOffer: base_cost_a, result(빈 것 안 됨), cost_b(선택), out_of_stock, uses, max_uses, xp, special_price_diff, price_multiplier, demand.
     * 바닐라 클라이언트는 out_of_stock 이 참이면 uses 를 max_uses 로 맞춘다.
     */
    private static Node offer(GenericCodec767 c) {
        Node cost = itemCost(c);
        return struct(List.of("base_cost_a", "result", "cost_b", "out_of_stock", "uses", "max_uses", "xp", "special_price_diff", "price_multiplier", "demand"),
                List.of(cost, nonEmptyItem(c), GenericCodec767.optional(cost, false), BOOL, I32, I32, I32, I32, F32, I32));
    }
}
