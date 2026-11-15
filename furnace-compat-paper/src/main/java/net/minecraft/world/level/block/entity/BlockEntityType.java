package net.minecraft.world.level.block.entity;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 블록 엔티티 종류(Mojang 이름 shim, N2b-6). 종류 이름(키)과 "이 블록 상태에 붙을 수 있는가"만 둔다 — 생성기·레지스트리 상수는 없다.
 * 같은 키는 같은 객체다(호환 모듈 전용 {@link #furnaceOf}).
 */
public class BlockEntityType<T extends BlockEntity> {

    private static final ConcurrentHashMap<String, BlockEntityType<?>> BY_KEY = new ConcurrentHashMap<>();

    private final ResourceLocation key;

    private BlockEntityType(ResourceLocation key) { this.key = key; }

    /** 호환 모듈 전용 - 키("minecraft:chest")로 종류를 찾는다(없으면 만든다). */
    public static BlockEntityType<?> furnaceOf(String key) {
        return BY_KEY.computeIfAbsent(key, k -> new BlockEntityType<>(ResourceLocation.parse(k)));
    }

    /** 바닐라 getKey: 종류의 레지스트리 키. */
    public static ResourceLocation getKey(BlockEntityType<?> type) { return type.key; }

    /** 바닐라 isValid: 그 블록 상태의 블록이 이 종류의 블록인가(서버 표 기준). */
    public boolean isValid(BlockState state) {
        return key.toString().equals(org.domi.furnace.compat.nms.NmsBlockStates.bridge().blockEntityType(state.furnaceStateId()));
    }

    @Override
    public String toString() { return "BlockEntityType{" + key + "}"; }
}
