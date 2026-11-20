package net.minecraft.server.packs;

/**
 * 연 팩의 내용(Mojang 이름 shim). Furnace 에는 팩 저장소(PackRepository)가 없어 이것을 만드는 shim 이 없다 —
 * {@link net.minecraft.server.packs.repository.Pack#open} 은 UnsupportedNmsCall 이다. 형(type)만 있어 시그니처가 맞는다.
 */
public interface PackResources extends AutoCloseable {

    String packId();

    @Override
    void close();
}
