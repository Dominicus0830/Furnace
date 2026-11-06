// 네트워크 코덱(바닐라 StreamCodec 의 아이템 컴포넌트 쪽 부분). 읽은 결과는 PCodec 과 같은 "정규 값"이라
// 저장·비교·재전송이 한 표현으로 이어진다. 쓰기는 정규 값을 받는다.
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public interface NCodec {

    Object read(ByteBuf buf);

    void write(ByteBuf buf, Object v);

    /** 읽은 값·쓸 값을 바꿔 끼우는 코덱(바닐라 StreamCodec.map). */
    default NCodec map(Function<Object, Object> afterRead, Function<Object, Object> beforeWrite) {
        NCodec self = this;
        return new NCodec() {
            public Object read(ByteBuf buf) { return afterRead.apply(self.read(buf)); }
            public void write(ByteBuf buf, Object v) { self.write(buf, beforeWrite.apply(v)); }
        };
    }

    default NCodec listOf() { return list(this, Integer.MAX_VALUE); }

    // ------------------------------------------------------------------ 기본 타입

    NCodec VAR_INT = new NCodec() {
        public Object read(ByteBuf buf) { return Types.readVarInt(buf); }
        public void write(ByteBuf buf, Object v) { Types.writeVarInt(buf, ((Number) v).intValue()); }
    };

    NCodec INT = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readInt(); }
        public void write(ByteBuf buf, Object v) { buf.writeInt(((Number) v).intValue()); }
    };

    NCodec VAR_LONG = new NCodec() {
        public Object read(ByteBuf buf) {
            long r = 0;
            for (int k = 0; ; k += 7) {
                if (k >= 70) throw new CodecException("VarLong too big");
                byte b = buf.readByte();
                r |= (long) (b & 0x7f) << k;
                if ((b & 0x80) == 0) return r;
            }
        }
        public void write(ByteBuf buf, Object v) {
            long x = ((Number) v).longValue();
            while ((x & ~0x7fL) != 0) { buf.writeByte((int) (x & 0x7f) | 0x80); x >>>= 7; }
            buf.writeByte((int) x);
        }
    };

    NCodec LONG = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readLong(); }
        public void write(ByteBuf buf, Object v) { buf.writeLong(((Number) v).longValue()); }
    };

    NCodec BYTE = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readByte(); }
        public void write(ByteBuf buf, Object v) { buf.writeByte(((Number) v).byteValue()); }
    };

    NCodec FLOAT = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readFloat(); }
        public void write(ByteBuf buf, Object v) { buf.writeFloat(((Number) v).floatValue()); }
    };

    NCodec DOUBLE = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readDouble(); }
        public void write(ByteBuf buf, Object v) { buf.writeDouble(((Number) v).doubleValue()); }
    };

    NCodec BOOL = new NCodec() {
        public Object read(ByteBuf buf) { return buf.readBoolean(); }
        public void write(ByteBuf buf, Object v) { buf.writeBoolean((Boolean) v); }
    };

    NCodec STRING = string(32767);

    /** ByteBufCodecs.stringUtf8(max): VarInt 바이트 길이 + UTF-8, 길이 상한은 max*3 바이트 / max 글자. */
    static NCodec string(int max) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int len = Types.readVarInt(buf);
                int maxBytes = max * 3;
                if (len > maxBytes) throw new CodecException("The received encoded string buffer length is longer than maximum allowed (" + len + " > " + maxBytes + ")");
                if (len < 0) throw new CodecException("The received encoded string buffer length is less than zero! Weird string!");
                if (len > buf.readableBytes()) throw new CodecException("Not enough bytes in buffer, expected " + len + ", but got " + buf.readableBytes());
                String s = buf.toString(buf.readerIndex(), len, StandardCharsets.UTF_8);
                buf.skipBytes(len);
                if (s.length() > max) throw new CodecException("The received string length is longer than maximum allowed (" + s.length() + " > " + max + ")");
                return s;
            }
            public void write(ByteBuf buf, Object v) {
                String s = (String) v;
                if (s.length() > max) throw new CodecException("String too big (was " + s.length() + " characters, max " + max + ")");
                byte[] b = s.getBytes(StandardCharsets.UTF_8);
                if (b.length > max * 3) throw new CodecException("String too big (was " + b.length + " bytes encoded, max " + max * 3 + ")");
                Types.writeVarInt(buf, b.length);
                buf.writeBytes(b);
            }
        };
    }

    /** Unit — 0 바이트, 정규 값 {}. */
    NCodec UNIT = new NCodec() {
        public Object read(ByteBuf buf) { return Map.of(); }
        public void write(ByteBuf buf, Object v) {}
    };

    /** ResourceLocation.STREAM_CODEC(문자열 32767). */
    NCodec RESOURCE_LOCATION = STRING.map(s -> PCodec.RESOURCE_LOCATION.normalize(s), v -> v);

    /** 레지스트리 원소(ByteBufCodecs.registry / idMapper): VarInt id ↔ 이름. */
    static NCodec registryId(String registry) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int id = Types.readVarInt(buf);
                String n = ItemContext.name(registry, id);
                if (n == null) throw new CodecException("Unknown " + registry + " id: " + id);
                return n;
            }
            public void write(ByteBuf buf, Object v) {
                Integer id = ItemContext.id(registry, (String) v);
                if (id == null) throw new CodecException("Unknown " + registry + " element: " + v);
                Types.writeVarInt(buf, id);
            }
        };
    }

    /** Holder(ByteBufCodecs.holder): VarInt 0 = 바로 뒤에 직접 값, 아니면 레지스트리 id+1. 정규 값은 이름 문자열 또는 직접 값(맵). */
    static NCodec holder(String registry, NCodec direct) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int n = Types.readVarInt(buf);
                if (n == 0) {
                    if (direct == null) throw new CodecException("Direct " + registry + " holders are not allowed");
                    return direct.read(buf);
                }
                String name = ItemContext.name(registry, n - 1);
                if (name == null) throw new CodecException("Unknown " + registry + " id: " + (n - 1));
                return name;
            }
            public void write(ByteBuf buf, Object v) {
                if (v instanceof String s) {
                    Integer id = ItemContext.id(registry, s);
                    if (id == null) throw new CodecException("Unknown " + registry + " element: " + s);
                    Types.writeVarInt(buf, id + 1);
                } else {
                    if (direct == null) throw new CodecException("Direct " + registry + " holders are not allowed");
                    Types.writeVarInt(buf, 0);
                    direct.write(buf, v);
                }
            }
        };
    }

    /**
     * HolderSet(ByteBufCodecs.holderSet): VarInt n — 0 이면 태그 이름(ResourceLocation), 아니면 n-1 개의 레지스트리 id.
     * 정규 값(저장 코덱과 같은 꼴): "#태그", 원소 하나면 "이름", 그 밖에는 이름 리스트.
     */
    static NCodec holderSet(String registry) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int n = Types.readVarInt(buf) - 1;
                if (n == -1) return "#" + RESOURCE_LOCATION.read(buf);
                if (n > 4096) throw new CodecException(n + " elements exceeded max size of: 4096");
                List<Object> ids = new ArrayList<>(n);
                for (int k = 0; k < n; k++) ids.add(registryId(registry).read(buf));
                return ids.size() == 1 ? ids.get(0) : ids;
            }
            public void write(ByteBuf buf, Object v) {
                if (v instanceof String s && s.startsWith("#")) {
                    Types.writeVarInt(buf, 0);
                    RESOURCE_LOCATION.write(buf, s.substring(1));
                    return;
                }
                List<Object> ids = v instanceof String ? List.of(v) : Val.list(v);
                Types.writeVarInt(buf, ids.size() + 1);
                for (Object o : ids) registryId(registry).write(buf, o);
            }
        };
    }

    /** 순서 번호(VarInt)로 보내는 열거형. 정규 값은 이름. */
    static NCodec enumById(List<String> names) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int id = Types.readVarInt(buf);
                return id >= 0 && id < names.size() ? names.get(id) : names.get(0);   // ByteBufCodecs.idMapper(ByIdMap.ZERO 등)
            }
            public void write(ByteBuf buf, Object v) {
                int id = names.indexOf((String) v);
                if (id < 0) throw new CodecException("Unknown enum value: " + v);
                Types.writeVarInt(buf, id);
            }
        };
    }

    /** 텍스트(ComponentSerialization.STREAM_CODEC — 네트워크 NBT). 정규 값은 바닐라 JSON 문자열. */
    NCodec TEXT = new NCodec() {
        public Object read(ByteBuf buf) {
            Object tag = NbtVals.readNetwork(buf);
            if (tag == null) throw new CodecException("Empty text component");
            return TextNbtJson.toJson(tag);
        }
        public void write(ByteBuf buf, Object v) {
            buf.writeBytes(JsonTextSerializer.json().deserialize((String) v).networkNbt());
        }
    };

    /** CompoundTag(ByteBufCodecs.COMPOUND_TAG — 2 MiB 상한). */
    NCodec COMPOUND = new NCodec() {
        public Object read(ByteBuf buf) {
            Object tag = NbtVals.readNetwork(buf);
            if (tag instanceof NbtCompound c) return c;
            throw new CodecException("Not a compound tag: " + Val.describe(tag));
        }
        public void write(ByteBuf buf, Object v) { NbtVals.writeNetwork(buf, v); }
    };

    // ------------------------------------------------------------------ 조합

    static NCodec list(NCodec element, int max) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int n = Types.readVarInt(buf);
                if (n > max) throw new CodecException(n + " elements exceeded max size of: " + max);
                List<Object> out = new ArrayList<>(Math.min(n, 256));
                for (int k = 0; k < n; k++) out.add(element.read(buf));
                return out;
            }
            public void write(ByteBuf buf, Object v) {
                List<Object> l = Val.list(v);
                if (l.size() > max) throw new CodecException(l.size() + " elements exceeded max size of: " + max);
                Types.writeVarInt(buf, l.size());
                for (Object o : l) element.write(buf, o);
            }
        };
    }

    /** ByteBufCodecs.optional: 불리언 + 값. 정규 값 null = 비어 있음. */
    static NCodec optional(NCodec c) {
        return new NCodec() {
            public Object read(ByteBuf buf) { return buf.readBoolean() ? c.read(buf) : null; }
            public void write(ByteBuf buf, Object v) {
                buf.writeBoolean(v != null);
                if (v != null) c.write(buf, v);
            }
        };
    }

    /** ByteBufCodecs.map: VarInt 개수 + (키, 값)… */
    static NCodec map(NCodec key, NCodec value, int max) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                int n = Types.readVarInt(buf);
                if (n > max) throw new CodecException(n + " elements exceeded max size of: " + max);
                Map<String, Object> out = new LinkedHashMap<>();
                for (int k = 0; k < n; k++) {
                    String kk = (String) key.read(buf);
                    out.put(kk, value.read(buf));
                }
                return out;
            }
            public void write(ByteBuf buf, Object v) {
                Map<String, Object> m = Val.map(v);
                Types.writeVarInt(buf, m.size());
                for (var e : m.entrySet()) {
                    key.write(buf, e.getKey());
                    value.write(buf, e.getValue());
                }
            }
        };
    }

    /** 레코드 필드(네트워크). */
    record F(String name, NCodec codec, Kind kind, Object def) {
        enum Kind { ALWAYS, DEFAULTED, OPTIONAL }
    }

    /** 정규 값에 항상 있는 필드. */
    static F f(String name, NCodec c) { return new F(name, c, F.Kind.ALWAYS, null); }

    /** 저장 코덱에서 optionalFieldOf(name, def) 인 필드 — 네트워크로는 항상 보내고, 읽은 값이 기본값이면 정규 값에서 뺀다. */
    static F fDef(String name, NCodec c, Object def) { return new F(name, c, F.Kind.DEFAULTED, def); }

    /** Optional 필드 — 불리언 접두. */
    static F fOpt(String name, NCodec c) { return new F(name, c, F.Kind.OPTIONAL, null); }

    static NCodec record(F... fields) {
        return new NCodec() {
            public Object read(ByteBuf buf) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (F f : fields) {
                    switch (f.kind) {
                        case ALWAYS -> out.put(f.name, f.codec.read(buf));
                        case DEFAULTED -> {
                            Object v = f.codec.read(buf);
                            if (!Val.same(v, f.def)) out.put(f.name, v);
                        }
                        case OPTIONAL -> {
                            if (buf.readBoolean()) out.put(f.name, f.codec.read(buf));
                        }
                    }
                }
                return out;
            }
            public void write(ByteBuf buf, Object v) {
                Map<String, Object> m = Val.map(v);
                for (F f : fields) {
                    Object x = m.get(f.name);
                    switch (f.kind) {
                        case ALWAYS -> {
                            if (x == null) throw new CodecException("Missing field " + f.name);
                            f.codec.write(buf, x);
                        }
                        case DEFAULTED -> f.codec.write(buf, x != null ? x : f.def);
                        case OPTIONAL -> {
                            buf.writeBoolean(x != null);
                            if (x != null) f.codec.write(buf, x);
                        }
                    }
                }
            }
        };
    }

    static NCodec lazy(java.util.function.Supplier<NCodec> s) {
        return new NCodec() {
            NCodec c;
            NCodec c() { return c != null ? c : (c = s.get()); }
            public Object read(ByteBuf buf) { return c().read(buf); }
            public void write(ByteBuf buf, Object v) { c().write(buf, v); }
        };
    }
}
