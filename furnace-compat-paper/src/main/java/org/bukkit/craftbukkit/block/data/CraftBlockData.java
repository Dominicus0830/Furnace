package org.bukkit.craftbukkit.block.data;

import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.compat.paper.CompatBlockData;

/**
 * CraftBukkit BlockData 의 바탕(N0). NMS 를 쓰는 플러그인이 {@code ((CraftBlockData) data).getState()} 로 NMS 상태를 꺼내고
 * {@code CraftBlockData.fromData(state)} 로 되돌리는 다리다. 실제 값은 {@link CompatBlockData}(이름 기반 상태)이고, 상태 번호로 오간다.
 * 종류별 하위 클래스(CraftStairs 등)는 없다 — Bukkit 쪽 종류별 인터페이스가 아직 없어서다.
 */
public abstract class CraftBlockData implements BlockData {

    protected CraftBlockData() {}

    /** 이 상태의 NMS 블록 상태(상태 번호가 같은 같은 객체). */
    public abstract BlockState getState();

    /** NMS 상태 → Bukkit BlockData(모든 속성이 적힌 사본). */
    public static CraftBlockData fromData(BlockState state) { return CompatBlockData.ofState(state); }

    /** CraftBukkit 은 fromData 와 같은 값을 준다. */
    public static CraftBlockData createData(BlockState state) { return fromData(state); }

    /** 종류와 속성 문자열("[facing=north]" 또는 null). 잘못이면 IllegalArgumentException. */
    public static CraftBlockData newData(Material material, String data) {
        return (CraftBlockData) (material == null ? CompatBlockData.create(data) : CompatBlockData.create(material, data));
    }

    @Override
    public abstract CraftBlockData clone();
}
