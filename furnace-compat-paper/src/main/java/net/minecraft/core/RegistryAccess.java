package net.minecraft.core;

/**
 * 레지스트리 묶음(Mojang 이름 shim, H7b — 이름표만). {@code MinecraftServer#registryAccess()}·{@code Level#registryAccess()} 가 돌려주고,
 * 플러그인은 주로 직렬화 메서드에 그대로 넘긴다. 레지스트리 꺼내기(registryOrThrow 등)는 아직 없다(없는 멤버 — 호출 위치에서 실패).
 */
public interface RegistryAccess extends HolderLookup.Provider {

    /** 서버가 가진 얼린 묶음 하나. */
    Frozen SERVER = new Frozen() {
        @Override
        public String toString() { return "RegistryAccess[Furnace]"; }
    };

    /** 얼린(바뀌지 않는) 묶음. */
    interface Frozen extends RegistryAccess {}
}
