package org.bukkit.craftbukkit.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;

/**
 * Paper CraftBanner: 현수막 블록 엔티티 NBT(바닐라 BannerBlockEntity — patterns: [{pattern: "minecraft:…", color: "red"}]) 사본 위에서 무늬를 읽고 쓴다.
 * 바탕 색은 블록 종류에서 오고 NBT 에 없어서, setBaseColor 는 이 상태 안에서만 바뀐다(Paper 도 블록 엔티티 필드만 바꿔 저장·다시 읽기에서 사라진다).
 * 레지스트리 키가 아닌 인라인 무늬({asset_id, translation_key})는 Paper 처럼 건너뛰고, update 하면 빠진다.
 */
public class CraftBanner extends CraftBlockEntityState implements org.bukkit.block.Banner {

    private DyeColor base;
    private List<Pattern> patterns;

    public CraftBanner(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        base = colorOfBlock(((org.domi.furnace.compat.paper.CompatBlockData) data).furnace().type());
        patterns = org.domi.furnace.compat.paper.BannerLayers.read(snapshotNbt().getList("patterns"), Integer.MAX_VALUE);
    }

    /** minecraft:light_gray_wall_banner → LIGHT_GRAY. */
    private static DyeColor colorOfBlock(String block) {
        String path = block.substring(block.indexOf(':') + 1);
        path = path.endsWith("_wall_banner") ? path.substring(0, path.length() - "_wall_banner".length())
                : path.endsWith("_banner") ? path.substring(0, path.length() - "_banner".length()) : path;
        DyeColor c = dye(path);
        return c == null ? DyeColor.WHITE : c;
    }

    private static DyeColor dye(String vanillaName) { return org.domi.furnace.compat.paper.BannerLayers.dye(vanillaName); }

    @Override
    public DyeColor getBaseColor() { return base; }

    @Override
    public void setBaseColor(DyeColor color) {
        if (color == null) throw new IllegalArgumentException("color");
        base = color;
    }

    @Override
    public List<Pattern> getPatterns() { return new ArrayList<>(patterns); }

    @Override
    public void setPatterns(List<Pattern> patterns) { this.patterns = new ArrayList<>(patterns); }

    @Override
    public void addPattern(Pattern pattern) { patterns.add(pattern); }

    @Override
    public Pattern getPattern(int i) { return patterns.get(i); }

    @Override
    public Pattern removePattern(int i) { return patterns.remove(i); }

    @Override
    public void setPattern(int i, Pattern pattern) { patterns.set(i, pattern); }

    @Override
    public int numberOfPatterns() { return patterns.size(); }

    /** 무늬 겹을 바닐라 저장 모양으로. 잘못된 겹은 몇 번째인지 알려 준다. */
    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        NbtList out = org.domi.furnace.compat.paper.BannerLayers.write(patterns);
        // 빈 목록도 적는다: 블록 엔티티 load 는 바닐라처럼 patterns 가 없으면 옛 무늬를 그대로 두므로(Paper applyTo 는 setPatterns 로 직접 비운다)
        snapshotNbt().putList("patterns", out);
    }

    /** Paper 사본 NBT 는 블록 엔티티 저장 꼴이라 빈 무늬를 쓰지 않는다. */
    @Override
    public NbtCompound getSnapshotNBT() {
        NbtCompound n = super.getSnapshotNBT();
        if (n.getList("patterns").isEmpty()) n.remove("patterns");
        return n;
    }
}
