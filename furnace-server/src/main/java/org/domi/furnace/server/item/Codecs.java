// 컴포넌트들이 함께 쓰는 바닐라 코덱 조각(ExtraCodecs·UUIDUtil·DyeColor·텍스트 트리·직접 레지스트리 값 등).
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

import static org.domi.furnace.server.item.PCodec.opt;
import static org.domi.furnace.server.item.PCodec.optEmpty;
import static org.domi.furnace.server.item.PCodec.record;
import static org.domi.furnace.server.item.PCodec.req;

public final class Codecs {
    private Codecs() {}

    /** ExtraCodecs.UNSIGNED_BYTE — NBT 로는 byte, 값은 0..255. */
    public static final PCodec UNSIGNED_BYTE = new PCodec() {
        public Object normalize(Object in) { return Val.number(in).byteValue() & 0xff; }
        public Object toNbt(Object v) { return (byte) (int) (Integer) v; }
    };

    /** UUIDUtil.CODEC — int 4개 배열. 정규 값 int[4]. */
    public static final PCodec UUID_CODEC = new PCodec() {
        public Object normalize(Object in) {
            List<Object> l = Val.list(in);
            if (l.size() != 4) throw new CodecException("Input is not a list of 4 elements");
            int[] a = new int[4];
            for (int k = 0; k < 4; k++) a[k] = Val.number(l.get(k)).intValue();
            return a;
        }
        public Object toNbt(Object v) { return ((int[]) v).clone(); }
    };

    /** UUIDUtil.LENIENT_CODEC — 문자열 UUID 도 받는다. */
    public static final PCodec UUID_LENIENT = PCodec.alternative(UUID_CODEC, PCodec.STRING, s -> {
        UUID u = UUID.fromString((String) s);
        return uuidInts(u);
    });

    public static int[] uuidInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    /** UUIDUtil.STREAM_CODEC — long 두 개. */
    public static final NCodec UUID_STREAM = new NCodec() {
        public Object read(ByteBuf buf) { return uuidInts(new UUID(buf.readLong(), buf.readLong())); }
        public void write(ByteBuf buf, Object v) {
            int[] a = (int[]) v;
            buf.writeLong((long) a[0] << 32 | a[1] & 0xffffffffL);
            buf.writeLong((long) a[2] << 32 | a[3] & 0xffffffffL);
        }
    };

    /** BlockPos.CODEC — int 3개 배열. 정규 값 int[3]. */
    public static final PCodec BLOCK_POS = new PCodec() {
        public Object normalize(Object in) {
            List<Object> l = Val.list(in);
            if (l.size() != 3) throw new CodecException("Input is not a list of 3 elements");
            return new int[] {Val.number(l.get(0)).intValue(), Val.number(l.get(1)).intValue(), Val.number(l.get(2)).intValue()};
        }
        public Object toNbt(Object v) { return ((int[]) v).clone(); }
    };

    /** BlockPos.STREAM_CODEC — 압축 long. */
    public static final NCodec BLOCK_POS_STREAM = new NCodec() {
        public Object read(ByteBuf buf) {
            long p = buf.readLong();
            return new int[] {(int) (p >> 38), (int) (p << 52 >> 52), (int) (p << 26 >> 38)};
        }
        public void write(ByteBuf buf, Object v) {
            int[] a = (int[]) v;
            buf.writeLong(((long) a[0] & 0x3FFFFFF) << 38 | ((long) a[2] & 0x3FFFFFF) << 12 | (long) a[1] & 0xFFF);
        }
    };

    public static final List<String> DYE_COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    public static final PCodec DYE_COLOR = PCodec.enumByName(DYE_COLORS);
    public static final NCodec DYE_COLOR_STREAM = NCodec.enumById(DYE_COLORS);

    /** ExtraCodecs.PLAYER_NAME — 16글자 이하, 출력 가능한 ASCII. */
    public static final PCodec PLAYER_NAME = PCodec.STRING.validate(v -> {
        String s = (String) v;
        if (s.length() > 16) return false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c <= 32 || c >= 127) return false;
        }
        return true;
    }, v -> "Player name is invalid: '" + v + "'");

    /** ExtraCodecs.PROPERTY_MAP — 정규 값은 리스트 [{name, value, signature?}]; 옛 맵 꼴 {이름: [값…]} 도 읽는다. */
    public static final PCodec PROPERTY = record(req("name", PCodec.STRING), req("value", PCodec.STRING), optEmpty("signature", PCodec.STRING));
    public static final PCodec PROPERTY_MAP = PCodec.alternative(PROPERTY.listOf(), PCodec.map(PCodec.STRING, PCodec.STRING.listOf()), m -> {
        List<Object> out = new ArrayList<>();
        for (var e : Val.map(m).entrySet()) {
            for (Object v : Val.list(e.getValue())) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", e.getKey());
                p.put("value", v);
                out.add(p);
            }
        }
        return out;
    });

    /** ByteBufCodecs.GAME_PROFILE_PROPERTIES — 최대 16개, (이름 64, 값 32767, 서명 1024?). */
    public static final NCodec PROPERTY_MAP_STREAM = NCodec.list(NCodec.record(
            NCodec.f("name", NCodec.string(64)), NCodec.f("value", NCodec.STRING), NCodec.fOpt("signature", NCodec.string(1024))), 16);

    // ------------------------------------------------------------------ 텍스트 트리(ComponentSerialization.CODEC — 문자열이 아닌 JSON 값)

    /** 정규 값: 바닐라 JsonOps 로 쓴 텍스트(문자열 또는 맵·리스트). */
    public static final PCodec TEXT_TREE = new PCodec() {
        public Object normalize(Object in) {
            String json = toJsonString(in);
            try {
                String canon = JsonTextSerializer.json().deserialize(json).toJson();
                return org.domi.furnace.server.loot.Components.fromJson(com.google.gson.JsonParser.parseString(canon));
            } catch (RuntimeException e) {
                throw new CodecException("Failed to parse text component: " + e.getMessage());
            }
        }
        public Object toNbt(Object v) {
            byte[] b = JsonTextSerializer.json().deserialize(toJsonString(v)).networkNbt();
            ByteBuf buf = Unpooled.wrappedBuffer(b);
            return NbtVals.readNetwork(buf);
        }
    };

    public static final NCodec TEXT_TREE_STREAM = NCodec.TEXT.map(json -> TEXT_TREE.normalize(json), v -> toJsonString(v));

    private static String quoteJson(String s) { return new com.google.gson.JsonPrimitive(s).toString(); }

    static String toJsonString(Object v) {
        if (v instanceof String s) return quoteJson(s);
        return toGson(v).toString();
    }

    private static com.google.gson.JsonElement toGson(Object v) {
        if (v instanceof NbtCompound c) v = c.tags();
        if (v instanceof Map<?, ?> m) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            for (var e : m.entrySet()) o.add((String) e.getKey(), toGson(e.getValue()));
            return o;
        }
        if (Val.isList(v)) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (Object x : Val.list(v)) a.add(toGson(x));
            return a;
        }
        if (v instanceof Boolean b) return new com.google.gson.JsonPrimitive(b);
        if (v instanceof Number n) return new com.google.gson.JsonPrimitive(n);
        return new com.google.gson.JsonPrimitive(String.valueOf(v));
    }

    // ------------------------------------------------------------------ 직접 값을 가질 수 있는 레지스트리 참조(RegistryFileCodec)

    /** 이름 문자열이거나 직접 값(맵). */
    public static PCodec holder(String registry, PCodec direct) {
        PCodec byName = PCodec.registryId(registry);
        return new PCodec() {
            public Object normalize(Object in) {
                if (in instanceof String) return byName.normalize(in);
                if (direct == null) throw new CodecException("Inline definitions not allowed for " + registry);
                return direct.normalize(in);
            }
            public Object toNbt(Object v) { return v instanceof String s ? s : direct.toNbt(v); }
        };
    }

    public static final PCodec SOUND_EVENT_DIRECT = record(req("sound_id", PCodec.RESOURCE_LOCATION), optEmpty("range", PCodec.FLOAT));
    public static final NCodec SOUND_EVENT_DIRECT_STREAM = NCodec.record(NCodec.f("sound_id", NCodec.RESOURCE_LOCATION), NCodec.fOpt("range", NCodec.FLOAT));
    public static final PCodec SOUND_EVENT = holder("minecraft:sound_event", SOUND_EVENT_DIRECT);
    public static final NCodec SOUND_EVENT_STREAM = NCodec.holder("minecraft:sound_event", SOUND_EVENT_DIRECT_STREAM);

    /** RegistryCodecs.homogeneousList — "#태그", 이름 하나, 이름 리스트. 정규 값은 원소가 하나면 이름, 아니면 리스트. */
    public static PCodec holderSet(String registry) {
        PCodec id = PCodec.registryId(registry);
        return new PCodec() {
            public Object normalize(Object in) {
                if (in instanceof String s && s.startsWith("#")) {
                    return "#" + PCodec.RESOURCE_LOCATION.normalize(s.substring(1));
                }
                if (in instanceof String) return id.normalize(in);
                List<Object> out = new ArrayList<>();
                for (Object o : Val.list(in)) out.add(id.normalize(o));
                return out.size() == 1 ? out.get(0) : out;
            }
            public Object toNbt(Object v) {
                if (v instanceof String s) return s;
                List<Object> tags = new ArrayList<>(Val.list(v));
                return NbtVals.list(tags);
            }
        };
    }
}
