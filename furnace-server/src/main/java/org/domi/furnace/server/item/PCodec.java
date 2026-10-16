// 저장 코덱(바닐라 Codec 의 아이템 컴포넌트 쪽 부분) — 값 모양을 바닐라 RecordCodecBuilder·optionalFieldOf·withAlternative 와 같게 맞춘다.
//  normalize: 받을 수 있는 어떤 입력 꼴(JSON 꼴·NBT 꼴·대체 형식)이든 "정규 값"으로 — 정규 값 = 바닐라 JsonOps 인코더가 낼 모양
//             (기본값인 선택 필드는 빠지고, 수는 필드 타입의 자바 타입: int→Integer, float→Float …).
//  toNbt:     정규 값 → NBT(바닐라 NbtOps 인코딩: 불리언 → byte, 리스트 수집 규칙 등).
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public interface PCodec {

    Object normalize(Object in);

    Object toNbt(Object v);

    default PCodec validate(Predicate<Object> ok, Function<Object, String> error) {
        PCodec self = this;
        return new PCodec() {
            public Object normalize(Object in) {
                Object v = self.normalize(in);
                if (!ok.test(v)) throw new CodecException(error.apply(v));
                return v;
            }
            public Object toNbt(Object v) { return self.toNbt(v); }
        };
    }

    /** 정규화 뒤 값 모양을 바꾼다(바닐라 xmap 이 레코드를 고쳐 만드는 경우 등). */
    default PCodec post(UnaryOperator<Object> f) {
        PCodec self = this;
        return new PCodec() {
            public Object normalize(Object in) { return f.apply(self.normalize(in)); }
            public Object toNbt(Object v) { return self.toNbt(v); }
        };
    }

    default PCodec listOf() { return list(this, 0, Integer.MAX_VALUE); }

    // ------------------------------------------------------------------ 기본 타입

    PCodec INT = intRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
    PCodec NON_NEGATIVE_INT = intRange(0, Integer.MAX_VALUE, "Value must be non-negative: ");
    PCodec POSITIVE_INT = intRange(1, Integer.MAX_VALUE, "Value must be positive: ");

    static PCodec intRange(int min, int max) {
        return intRange(min, max, null);
    }

    static PCodec intRange(int min, int max, String prefix) {
        return new PCodec() {
            public Object normalize(Object in) {
                int v = Val.number(in).intValue();
                if (v < min || v > max) {
                    throw new CodecException(prefix != null ? prefix + v : "Value " + v + " outside of range [" + min + ":" + max + "]");
                }
                return v;
            }
            public Object toNbt(Object v) { return (Integer) v; }
        };
    }

    PCodec BYTE = new PCodec() {
        public Object normalize(Object in) { return Val.number(in).byteValue(); }
        public Object toNbt(Object v) { return (Byte) v; }
    };

    PCodec LONG = new PCodec() {
        public Object normalize(Object in) { return Val.number(in).longValue(); }
        public Object toNbt(Object v) { return (Long) v; }
    };

    PCodec FLOAT = floatRange(-Float.MAX_VALUE, Float.MAX_VALUE, null);
    PCodec NON_NEGATIVE_FLOAT = floatRange(0f, Float.MAX_VALUE, "Value must be non-negative: ");
    PCodec POSITIVE_FLOAT = floatRange(Float.MIN_VALUE, Float.MAX_VALUE, "Value must be positive: ");

    static PCodec floatRange(float min, float max, String prefix) {
        return new PCodec() {
            public Object normalize(Object in) {
                float v = Val.number(in).floatValue();
                if (!(v >= min && v <= max) && !(min == -Float.MAX_VALUE && max == Float.MAX_VALUE)) {
                    throw new CodecException(prefix != null ? prefix + v : "Value " + v + " outside of range [" + min + ":" + max + "]");
                }
                return v;
            }
            public Object toNbt(Object v) { return (Float) v; }
        };
    }

    PCodec DOUBLE = new PCodec() {
        public Object normalize(Object in) { return Val.number(in).doubleValue(); }
        public Object toNbt(Object v) { return (Double) v; }
    };

    PCodec BOOL = new PCodec() {
        public Object normalize(Object in) { return Val.bool(in); }
        public Object toNbt(Object v) { return (byte) ((Boolean) v ? 1 : 0); }
    };

    PCodec STRING = string(Integer.MAX_VALUE);

    static PCodec string(int maxLength) {
        return new PCodec() {
            public Object normalize(Object in) {
                String s = Val.string(in);
                if (s.length() > maxLength) throw new CodecException("String \"" + s + "\" is too long: " + s.length() + " > " + maxLength);
                return s;
            }
            public Object toNbt(Object v) { return (String) v; }
        };
    }

    /** 바닐라 ResourceLocation.CODEC — 이름공간이 없으면 minecraft. 정규 값은 "ns:path". */
    PCodec RESOURCE_LOCATION = new PCodec() {
        public Object normalize(Object in) {
            String s = Val.string(in);
            ResourceId id = ResourceId.tryParse(s);
            if (id == null) throw new CodecException("Not a valid resource location: " + s);
            return id.toString();
        }
        public Object toNbt(Object v) { return (String) v; }
    };

    /** Unit(빈 객체 {}) — 읽을 때는 내용과 상관없이 맵이면 된다. */
    PCodec UNIT = new PCodec() {
        public Object normalize(Object in) {
            if (!Val.isMap(in)) throw new CodecException("Not a JSON object: " + Val.describe(in));
            return Map.of();
        }
        public Object toNbt(Object v) { return new NbtCompound(); }
    };

    /** 레지스트리 원소 참조(바닐라 holderByNameCodec / RegistryFixedCodec): 이름 문자열, 레지스트리에 있어야 한다. */
    static PCodec registryId(String registry) {
        return new PCodec() {
            public Object normalize(Object in) {
                String s = Val.string(in);
                ResourceId id = ResourceId.tryParse(s);
                if (id == null) throw new CodecException("Not a valid resource location: " + s);
                if (ItemContext.installed() && ItemContext.size(registry) > 0 && ItemContext.regs().id(registry, id) == null) {
                    throw new CodecException("Unknown registry key in ResourceKey[minecraft:root / " + registry + "]: " + id);
                }
                return id.toString();
            }
            public Object toNbt(Object v) { return (String) v; }
        };
    }

    /** 이름으로 저장하는 열거형(StringRepresentable). */
    static PCodec enumByName(List<String> names) {
        return new PCodec() {
            public Object normalize(Object in) {
                String s = Val.string(in);
                if (!names.contains(s)) throw new CodecException("Unknown element name:" + s);
                return s;
            }
            public Object toNbt(Object v) { return (String) v; }
        };
    }

    /**
     * 텍스트 컴포넌트 JSON 문자열(바닐라 ComponentSerialization.FLAT_CODEC = flatCodec(MAX)). 정규 값은 바닐라가 쓰는 문자열:
     * 컴포넌트 JSON 을 GsonHelper.toStableString 으로(키를 재귀적으로 정렬, HTML 이스케이프 없음). 읽기는 Gson 관대 파서.
     */
    PCodec TEXT_FLAT = flatText(Integer.MAX_VALUE);

    static PCodec flatText(int maxLength) {
        return new PCodec() {
            public Object normalize(Object in) {
                String s = Val.string(in);
                if (s.length() > maxLength) throw new CodecException("String \"" + s + "\" is too long: " + s.length() + " > " + maxLength);
                return FlatText.canonical(s);
            }
            public Object toNbt(Object v) { return (String) v; }
        };
    }

    /** 임의 NBT compound(바닐라 CompoundTag.CODEC 과 TagParser 문자열 대체 형식). 정규 값은 NbtCompound. */
    PCodec COMPOUND = new PCodec() {
        public Object normalize(Object in) {
            if (in instanceof NbtCompound c) return c.copy();
            if (in instanceof String s) {
                Object t = Snbt.parse(s);
                if (t instanceof NbtCompound c) return c;
                throw new CodecException("Not a compound tag: " + s);
            }
            if (in instanceof Map<?, ?>) return Snbt.fromJsonLike(in);
            throw new CodecException("Not a compound tag: " + Val.describe(in));
        }
        public Object toNbt(Object v) { return ((NbtCompound) v).copy(); }
    };

    // ------------------------------------------------------------------ 조합

    static PCodec list(PCodec element, int min, int max) {
        return new PCodec() {
            public Object normalize(Object in) {
                List<Object> src = in instanceof org.domi.furnace.server.net.io.nbt.NbtList nl ? NbtVals.unwrapList(nl.items()) : Val.list(in);
                if (src.size() < min) throw new CodecException("List must have at least " + min + " elements");   // 실제 문구는 호출부가 덮을 수 있다
                if (src.size() > max) throw new CodecException("List is too long: " + src.size() + ", expected range [" + min + "-" + max + "]");
                List<Object> out = new ArrayList<>(src.size());
                for (Object o : src) out.add(element.normalize(o));
                return out;
            }
            public Object toNbt(Object v) {
                List<Object> tags = new ArrayList<>();
                for (Object o : Val.list(v)) tags.add(element.toNbt(o));
                return NbtVals.list(tags);
            }
        };
    }

    /** unboundedMap(문자열 키 코덱, 값 코덱) — 키도 정규화한다(ResourceLocation 키는 "ns:path"). */
    static PCodec map(PCodec key, PCodec value) {
        return new PCodec() {
            public Object normalize(Object in) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : Val.map(in).entrySet()) out.put((String) key.normalize(e.getKey()), value.normalize(e.getValue()));
                return out;
            }
            public Object toNbt(Object v) {
                NbtCompound c = new NbtCompound();
                for (var e : Val.map(v).entrySet()) c.put(e.getKey(), value.toNbt(e.getValue()));
                return c;
            }
        };
    }

    /**
     * 바닐라 Codec.withAlternative(primary, alternative[, 변환]) — 읽기는 primary 먼저, 실패하면 alternative 를 읽어
     * toPrimary 로 primary 의 정규 값으로 바꾼다. 쓰기는 항상 primary.
     */
    static PCodec alternative(PCodec primary, PCodec alt, UnaryOperator<Object> toPrimary) {
        return new PCodec() {
            public Object normalize(Object in) {
                try {
                    return primary.normalize(in);
                } catch (CodecException first) {
                    Object a;
                    try {
                        a = alt.normalize(in);
                    } catch (CodecException second) {
                        throw first;
                    }
                    return toPrimary.apply(a);
                }
            }
            public Object toNbt(Object v) { return primary.toNbt(v); }
        };
    }

    /**
     * 정규 값 하나에 대해 쓸 형식을 고르는 코덱(바닐라 Codec.either/xmap 으로 "간단한 꼴" 을 고르는 경우).
     * 읽기: 어느 꼴이든 받아 full 정규 값으로. 쓰기: simpleWhen 이 참이면 toSimple 결과(정규 값 자체가 그 꼴).
     */
    static PCodec simpleOrFull(PCodec full, PCodec simple, UnaryOperator<Object> simpleToFull, Predicate<Object> simpleWhen,
                               UnaryOperator<Object> fullToSimple) {
        return new PCodec() {
            public Object normalize(Object in) {
                Object f;
                try {
                    f = full.normalize(in);
                } catch (CodecException e) {
                    f = simpleToFull.apply(simple.normalize(in));
                }
                return f;
            }
            public Object toNbt(Object v) { return full.toNbt(v); }
        };
    }

    // ------------------------------------------------------------------ 레코드

    /** 레코드 필드. */
    record Field(String name, PCodec codec, Kind kind, Object def) {
        enum Kind { REQUIRED, OPTIONAL_DEFAULT, OPTIONAL_LENIENT, OPTIONAL_EMPTY, OR_ELSE }
    }

    /** fieldOf — 반드시 있어야 하고 항상 쓴다. */
    static Field req(String name, PCodec c) { return new Field(name, c, Field.Kind.REQUIRED, null); }

    /** optionalFieldOf(name, default) — 없으면 기본값, 기본값과 같으면 쓰지 않는다(정규 값에서도 빠짐). */
    static Field opt(String name, PCodec c, Object def) { return new Field(name, c, Field.Kind.OPTIONAL_DEFAULT, c.normalize(def)); }

    /** optionalFieldOf(name) — Optional, 없으면 비어 있음(정규 값에서 키가 없다). */
    static Field optEmpty(String name, PCodec c) { return new Field(name, c, Field.Kind.OPTIONAL_EMPTY, null); }

    /** lenientOptionalFieldOf(name, default) — optionalFieldOf 와 같되 잘못된 값도 기본값으로. */
    static Field optLenient(String name, PCodec c, Object def) { return new Field(name, c, Field.Kind.OPTIONAL_LENIENT, c.normalize(def)); }

    /** fieldOf(name).orElse(default) — 없으면 기본값, 쓸 때는 항상 쓴다. */
    static Field orElse(String name, PCodec c, Object def) { return new Field(name, c, Field.Kind.OR_ELSE, c.normalize(def)); }

    static PCodec record(Field... fields) {
        return new PCodec() {
            public Object normalize(Object in) {
                Map<String, Object> src = Val.map(in);
                Map<String, Object> out = new LinkedHashMap<>();
                for (Field f : fields) {
                    Object raw = src.get(f.name);
                    switch (f.kind) {
                        case REQUIRED -> {
                            if (raw == null) throw new CodecException("No key " + f.name + " in MapLike[" + Val.describe(src) + "]");
                            out.put(f.name, f.codec.normalize(raw));
                        }
                        case OPTIONAL_DEFAULT -> {
                            if (raw == null) continue;
                            Object v = f.codec.normalize(raw);
                            if (!Val.same(v, f.def)) out.put(f.name, v);
                        }
                        case OPTIONAL_LENIENT -> {
                            if (raw == null) continue;
                            Object v;
                            try {
                                v = f.codec.normalize(raw);
                            } catch (CodecException e) {
                                continue;
                            }
                            if (!Val.same(v, f.def)) out.put(f.name, v);
                        }
                        case OPTIONAL_EMPTY -> { if (raw != null) out.put(f.name, f.codec.normalize(raw)); }
                        case OR_ELSE -> {
                            Object v;
                            try {
                                v = raw == null ? f.def : f.codec.normalize(raw);
                            } catch (CodecException e) {
                                v = f.def;   // orElse 는 읽기 오류도 기본값으로 덮는다
                            }
                            out.put(f.name, v);
                        }
                    }
                }
                return out;
            }
            public Object toNbt(Object v) {
                Map<String, Object> m = Val.map(v);
                NbtCompound c = new NbtCompound();
                for (Field f : fields) {
                    Object x = m.get(f.name);
                    if (x != null) c.put(f.name, f.codec.toNbt(x));
                }
                return c;
            }
        };
    }

    /** 정규 레코드 값에서 필드 읽기(없으면 레코드 기본값). */
    static Object get(Map<String, Object> rec, String name, Object def) {
        Object v = rec.get(name);
        return v != null ? v : def;
    }

    /** 다른 코덱에 처리를 미루는 코덱(재귀 정의용). */
    static PCodec lazy(java.util.function.Supplier<PCodec> s) {
        return new PCodec() {
            PCodec c;
            PCodec c() { return c != null ? c : (c = s.get()); }
            public Object normalize(Object in) { return c().normalize(in); }
            public Object toNbt(Object v) { return c().toNbt(v); }
        };
    }
}
