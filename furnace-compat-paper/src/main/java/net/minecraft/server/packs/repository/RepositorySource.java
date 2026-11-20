package net.minecraft.server.packs.repository;

import java.util.function.Consumer;

/** 팩을 찾아 주는 곳(바닐라 interface). Furnace 에는 이것을 모으는 팩 저장소가 없다. */
public interface RepositorySource {
    void loadPacks(Consumer<Pack> consumer);
}
