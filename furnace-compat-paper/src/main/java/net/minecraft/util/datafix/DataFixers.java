package net.minecraft.util.datafix;

/**
 * 데이터 고치기(Mojang 이름 shim, N2b-6). 바닐라는 모든 옛 판 스키마를 담은 DataFixerUpper 를 주지만, Furnace 호환 계층에는 그 스키마가 없다.
 * {@link #getDataFixer()} 는 판을 올리지 않고 값을 그대로 돌려주는 고치기다 — 같은 판(3955) 데이터는 바닐라와 결과가 같고, 옛 판 데이터(옛 스케매틱 등)는
 * 올라가지 않는다(처음 한 번 경고). 옛 판 올리기는 TASK(N2b-6 보고서).
 */
public class DataFixers {

    public static final int BLENDING_VERSION = 3441;

    private DataFixers() {}

    public static com.mojang.datafixers.DataFixer getDataFixer() { return Lib.FIXER; }

    /** DataFixerUpper 를 쓰는 본문(CompatLinkTest 가 라이브러리 없이 링크를 볼 때 이 이름만 건너뛴다). */
    static final class Lib {
        private Lib() {}

        private static volatile boolean warned;

        static final com.mojang.datafixers.DataFixer FIXER = new com.mojang.datafixers.DataFixer() {
            @Override
            public <T> com.mojang.serialization.Dynamic<T> update(com.mojang.datafixers.DSL.TypeReference type, com.mojang.serialization.Dynamic<T> input,
                    int version, int newVersion) {
                if (version < newVersion && !warned) {
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
