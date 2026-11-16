package net.minecraft.util.datafix;

import com.mojang.serialization.Dynamic;

/**
 * 데이터 고치기(Mojang 이름 shim, N2b-6). 바닐라는 모든 옛 판 스키마를 담은 DataFixerUpper 를 주지만, Furnace 호환 계층에는 그 스키마가 없다.
 * {@link #getDataFixer()} 는 서버의 데이터 픽서({@code Furnace.dataFixer()}, 구조물 템플릿 읽기와 같은 고침 표 1452~3955)로 블록 상태·블록 이름·아이템 이름·
 * 아이템 칸·블록 엔티티·엔티티를 서버 판(3955)까지 올린다(T-199 — FAWE 의 옛 이름표 {@code LegacyMapper} 가 1631 → 3955 로 올리는 것이 이 길이다). 다른 종류, 목표 판이 3955 가 아닌 호출, 서버 판 밖
 * (1452 미만)의 옛 판은 올리지 않고 값을 그대로 돌려주며 처음 한 번 경고한다. 같은 판(3955) 데이터는 바닐라와 결과가 같다.
 */
public class DataFixers {

    public static final int BLENDING_VERSION = 3441;

    private DataFixers() {}

    public static com.mojang.datafixers.DataFixer getDataFixer() { return Lib.FIXER; }

    /** DataFixerUpper 를 쓰는 본문(CompatLinkTest 가 라이브러리 없이 링크를 볼 때 이 이름만 건너뛴다). */
    static final class Lib {
        private Lib() {}

        private static volatile boolean warned;

        /** 서버 데이터 픽서로 올린 값. 다루지 않는 종류·값(NBT 태그가 아니거나 서버 판 밖)이면 null. */
        @SuppressWarnings("unchecked")
        private static <T> Dynamic<T> fix(String type, Dynamic<T> input, int version, int newVersion) {
            if (!(input.getValue() instanceof net.minecraft.nbt.Tag tag)) return null;
            org.domi.furnace.api.nbt.NbtDataFixer fixer;
            try {
                fixer = org.domi.furnace.api.Furnace.server().dataFixer();
            } catch (UnsupportedOperationException | IllegalStateException e) {
                return null;
            }
            // 서버는 자기 판(3955)까지만 올린다 — 다른 목표 판으로 부른 것은 3955 로 올리면 틀린 값이라 그대로 둔다
            if (newVersion != fixer.currentVersion() || !fixer.supports(version)) return null;
            net.minecraft.nbt.Tag out;
            switch (type) {
                case "block_name" -> {
                    if (!(tag instanceof net.minecraft.nbt.StringTag s)) return null;
                    out = net.minecraft.nbt.StringTag.valueOf(fixer.fixBlockName(s.getAsString(), version));
                }
                case "item_name" -> {
                    if (!(tag instanceof net.minecraft.nbt.StringTag s)) return null;
                    out = net.minecraft.nbt.StringTag.valueOf(fixer.fixItemName(s.getAsString(), version));
                }
                case "block_state", "item_stack", "block_entity", "entity" -> {
                    if (!(tag instanceof net.minecraft.nbt.CompoundTag)) return null;
                    org.domi.furnace.api.nbt.NbtCompound c = (org.domi.furnace.api.nbt.NbtCompound) net.minecraft.nbt.NbtBridge.toFurnace(tag);
                    org.domi.furnace.api.nbt.NbtCompound fixed = switch (type) {
                        case "block_state" -> fixer.fixBlockState(c, version);
                        case "item_stack" -> fixer.fixItemStack(c, version);
                        case "block_entity" -> fixer.fixBlockEntity(c, version);
                        default -> fixer.fixEntity(c, version);
                    };
                    out = net.minecraft.nbt.NbtBridge.fromFurnace(fixed);
                }
                default -> {
                    return null;
                }
            }
            return new Dynamic<>(input.getOps(), (T) out);
        }

        static final com.mojang.datafixers.DataFixer FIXER = new com.mojang.datafixers.DataFixer() {
            @Override
            public <T> Dynamic<T> update(com.mojang.datafixers.DSL.TypeReference type, Dynamic<T> input,
                    int version, int newVersion) {
                if (version >= newVersion) return input;
                Dynamic<T> fixed = fix(type.typeName(), input, version, newVersion);
                if (fixed != null) return fixed;
                if (!warned) {
                    warned = true;
                    org.slf4j.LoggerFactory.getLogger("Furnace").warn(org.domi.furnace.compat.i18n.CompatLang.t("compat.nms-datafix-unsupported", type.typeName(), version, newVersion));
                }
                return input;
            }

            @Override
            public com.mojang.datafixers.schemas.Schema getSchema(int key) {
                throw new UnsupportedOperationException("Furnace: no data fixer schema for version " + key);
            }
        };
    }
}
