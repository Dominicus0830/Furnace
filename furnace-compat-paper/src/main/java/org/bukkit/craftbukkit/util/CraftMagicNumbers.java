package org.bukkit.craftbukkit.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.bukkit.Material;
import org.bukkit.UnsafeValues;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.compat.nms.NmsBlocks;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatFormat;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * {@link UnsafeValues} 구현과 CraftBukkit Material ↔ NMS 블록·아이템 변환(N0). 클래스 이름은 Paper·CraftBukkit 과 같다
 * ({@code Bukkit.getUnsafe().getClass()} 로 서버를 알아보는 코드가 있다). 상태는 없고 부를 때마다 지금 떠 있는 Furnace 서버에 묻는다.
 * CraftBukkit 처럼 블록이 아닌 Material 이면 {@code getBlock} 이 null, 아이템이 아니면 {@code getItem} 이 null 이다.
 */
@SuppressWarnings("deprecation")
public final class CraftMagicNumbers implements UnsafeValues {

    public static final CraftMagicNumbers INSTANCE = new CraftMagicNumbers();

    private CraftMagicNumbers() {}

    private static Furnace f() { return Compat.furnace(); }

    // ---- Material ↔ NMS

    public static Block getBlock(Material material) {
        if (material == null || !material.isBlock()) return null;
        return NmsBlocks.registry().getOptional(net.minecraft.resources.ResourceLocation.parse(material.getKey().toString())).orElse(null);
    }

    public static Item getItem(Material material) {
        if (material == null || !material.isItem()) return null;
        return Item.byKey(material.getKey().toString());
    }

    /** Bukkit Material 열거형에 없는 블록(데이터 팩·플러그인 블록)이면 null. */
    /** 옛 데이터 값(패킷 라이브러리가 링크만 본다): 1.13 이후는 평탄화돼 0. */
    public static byte toLegacyData(net.minecraft.world.level.block.state.BlockState state) { return 0; }

    /** 옛 (재료, 데이터) → 상태. 데이터는 무시하고 기본 상태(평탄화 이후). 블록이 아닌 재료는 null. */
    public static net.minecraft.world.level.block.state.BlockState getBlock(Material material, byte data) {
        Block b = getBlock(material);
        return b == null ? null : b.defaultBlockState();
    }

    public static Material getMaterial(Block block) { return block == null ? null : Material.matchMaterial(block.furnaceKey().toString()); }

    /** CraftBukkit 처럼 null·Bukkit Material 열거형에 없는 아이템이면 AIR(블록 쪽은 null). */
    public static Material getMaterial(Item item) {
        Material m = item == null ? null : Material.matchMaterial(item.furnaceKey());
        return m != null ? m : Material.AIR;
    }

    // ---- UnsafeValues

    /** 1.21.1 의 SharedConstants 값(바닐라 서버 jar version.json 의 world_version). */
    static int dataVersionOf(String minecraftVersion) {
        if ("1.21.1".equals(minecraftVersion)) return 3955;
        throw new UnsupportedFeature("UnsafeValues#getDataVersion", "마인크래프트 " + minecraftVersion + " 의 데이터 버전 표가 없다");
    }

    @Override
    public int getDataVersion() { return dataVersionOf(f().minecraftVersion()); }

    @Override
    public int getProtocolVersion() { return f().packets().protocolVersion(); }

    @Override
    public boolean isSupportedApiVersion(String apiVersion) {
        if (apiVersion == null) return false;
        return !CompatFormat.isNewerThanServerApi(apiVersion);
    }

    @Override
    public void checkSupported(PluginDescriptionFile pdf) throws InvalidPluginException {
        String v = pdf.getAPIVersion();
        if (v != null && CompatFormat.isNewerThanServerApi(v)) throw new InvalidPluginException("Unsupported API version " + v);
    }

    @Override
    public String getMainLevelName() {
        for (var w : f().worlds()) if ("minecraft:overworld".equals(w.dimension())) return w.name();
        throw new IllegalStateException("오버월드가 아직 없다");
    }

    @Override
    public int nextEntityId() { return f().packets().nextEntityId(); }
}
