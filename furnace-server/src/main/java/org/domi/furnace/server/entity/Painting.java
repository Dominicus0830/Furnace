// 바닐라 Painting: 블록 면에 걸리는 그림. 놓을 때 맞는 크기 중 가장 넓은 것들 가운데서 무작위로 정한다(placeable 태그).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;

public class Painting extends HangingEntity {
    public static final int DATA_VARIANT = 8;

    private static final String REGISTRY = "minecraft:painting_variant";

    private static final class Variants {
        static final TagSet TAGS = TagSet.load("registries/tags-1.21.1.txt");
        static final java.util.Map<String, int[]> SIZES = new java.util.HashMap<>();

        static {
            try (var in = Painting.class.getClassLoader().getResourceAsStream("entities/painting_variants-1.21.1.txt")) {
                for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                    String[] p = line.trim().split(" ");
                    if (p.length == 3) SIZES.put("minecraft:" + p[0], new int[] {Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private String variant = "minecraft:kebab";

    public Painting(ChunkLevel level, int id) {
        super(EntityType.PAINTING, level, id);
        setVariant(variant);
    }

    private Painting(ChunkLevel level, int id, Pos pos) {
        super(EntityType.PAINTING, level, id, pos);
        setVariant(variant);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, SetEntityData.SERIALIZER_PAINTING_VARIANT, RegistrySet.current().indexOf(REGISTRY, "minecraft:kebab"));
    }

    public void setVariant(String name) {
        variant = name;
        entityData.set(DATA_VARIANT, RegistrySet.current().indexOf(REGISTRY, name));
        if (blockPos != null) recalculate();
    }

    public String variant() { return variant; }

    private int[] size() { return sizeOf(variant); }

    /** 변종 크기(가로, 세로). 데이터 팩·플러그인 변종은 시작 때 읽은 정의에서, 없으면 바닐라 표에서. */
    static int[] sizeOf(String v) {
        var dyn = org.domi.furnace.server.data.DynamicRegistries.current();
        var def = dyn == null ? null : dyn.definition(REGISTRY, v);
        if (def != null) {
            try {
                return new int[] {def.get("width").getAsInt(), def.get("height").getAsInt()};
            } catch (RuntimeException ignored) {
                // 모양 검사를 거친 정의라 오지 않는다
            }
        }
        return Variants.SIZES.getOrDefault(v, new int[] {1, 1});
    }

    /** 레지스트리에 있는 변종인가. */
    static boolean known(String v) { return RegistrySet.current().indexOf(REGISTRY, v) >= 0; }

    /** Painting.create: 놓을 수 있는 모양 중 이 자리에 맞는 가장 넓은 것들에서 무작위. 없으면 null. */
    public static Painting create(ChunkLevel level, Pos pos, int dir) {
        if (!Dir.horizontal(dir)) return null;
        Painting painting = new Painting(level, level.nextEntityId(), pos);
        List<String> list = new ArrayList<>();
        // placeable 태그: 데이터 팩 태그가 걸려 있으면 그것(데이터 팩·플러그인이 바꾼 것 포함), 아니면 내장 태그
        int[] ids = org.domi.furnace.server.item.ItemContext.hasTag(REGISTRY, "minecraft:placeable")
                ? org.domi.furnace.server.item.ItemContext.tagEntries(REGISTRY, "minecraft:placeable")
                : Variants.TAGS.ids(REGISTRY, "minecraft:placeable");
        if (ids == null) return null;
        List<String> all = RegistrySet.current().entries(REGISTRY);
        for (int i : ids) if (i >= 0 && i < all.size()) list.add(all.get(i));
        if (list.isEmpty()) return null;
        painting.setDirection(dir);
        list.removeIf(v -> {
            painting.setVariant(v);
            return !painting.survives();
        });
        if (list.isEmpty()) return null;
        int max = list.stream().mapToInt(v -> area(v)).max().orElse(0);
        list.removeIf(v -> area(v) < max);
        painting.setVariant(list.get(painting.random.nextInt(list.size())));
        painting.setDirection(dir);
        return painting;
    }

    private static int area(String v) {
        int[] s = sizeOf(v);
        return s[0] * s[1];
    }

    @Override
    protected AABB calculateBoundingBox(Pos pos, int dir) {
        int[] s = sizeOf(variant);
        double cx = pos.x() + 0.5 - Dir.DX[dir] * 0.46875, cy = pos.y() + 0.5 - Dir.DY[dir] * 0.46875, cz = pos.z() + 0.5 - Dir.DZ[dir] * 0.46875;
        int ccw = Dir.counterClockWise(dir);
        double ow = s[0] % 2 == 0 ? 0.5 : 0.0, oh = s[1] % 2 == 0 ? 0.5 : 0.0;
        cx += Dir.DX[ccw] * ow;
        cz += Dir.DZ[ccw] * ow;
        cy += oh;
        int axis = Dir.axis(dir);
        double sx = axis == Dir.AXIS_X ? 0.0625 : s[0], sy = s[1], sz = axis == Dir.AXIS_Z ? 0.0625 : s[0];
        return new AABB(cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2);
    }

    @Override
    public void dropItem(Targetable attacker) {
        if (!level.gameRule("doEntityDrops")) return;
        playSound("minecraft:entity.painting.break", 1.0F, 1.0F);
        if (attacker instanceof Player p && p.gameMode() == org.domi.furnace.server.world.GameMode.CREATIVE) return;
        spawnItem(new ItemStack(ItemContext.id("minecraft:item", "minecraft:painting"), 1));
    }

    @Override
    public void playPlacementSound() { playSound("minecraft:entity.painting.place", 1.0F, 1.0F); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putString("variant", variant);
        tag.putByte("facing", (byte) Dir.to2D(direction));
        super.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.tags().get("variant") instanceof String v && known(v.indexOf(':') < 0 ? "minecraft:" + v : v)) setVariant(v.indexOf(':') < 0 ? "minecraft:" + v : v);
        super.readAdditionalSaveData(tag);
        setDirection(Dir.from2D(tag.getIntOr0("facing")));
    }
}
