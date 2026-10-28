// 바닐라 BeaconBlockEntity: 피라미드 단수(levels)·고른 효과 두 개·빛줄기 검사. 주변 플레이어에게 효과를 거는 일.
// 빛줄기는 클라이언트가 자기 월드에서 다시 계산해 그린다. 서버는 "빛줄기가 열려 있는가"만 알면 된다.
package org.domi.furnace.server.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class BeaconBlockEntity extends BlockEntity {

    /** 고를 수 있는 효과(단계별): 신호기 레벨 1..4. */
    public static final List<List<String>> BEACON_EFFECTS = List.of(List.of("minecraft:speed", "minecraft:haste"),
            List.of("minecraft:resistance", "minecraft:jump_boost"), List.of("minecraft:strength"), List.of("minecraft:regeneration"));
    private static final Set<String> VALID_EFFECTS = Set.copyOf(BEACON_EFFECTS.stream().flatMap(List::stream).toList());

    private static final class Section {
        final String color;
        int height = 1;
        Section(String color) { this.color = color; }
    }

    private List<Section> beamSections = new ArrayList<>();
    private List<Section> checkingBeamSections = new ArrayList<>();
    public int levels;
    private int lastCheckY = Integer.MIN_VALUE;
    public String primaryPower, secondaryPower;
    public String customName;

    public BeaconBlockEntity(Pos pos, int state) { super("minecraft:beacon", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "beacon"); }

    @Override public boolean ticking() { return true; }

    /** 유효한 효과만 받는다(바닐라 filterEffect). */
    public static String filterEffect(String effect) { return effect != null && VALID_EFFECTS.contains(effect) ? effect : null; }

    public boolean isBeamActive() { return !beamSections.isEmpty(); }

    /** BeaconBeamBlock(신호기·색 유리·색 유리판) 이면 색 이름. */
    private static String stainedGlass(int state) {
        String n = B.BLOCKS.nameOfState(state);
        if (n.equals("minecraft:beacon")) return "white";
        if (n.endsWith("_stained_glass")) return n.substring(10, n.length() - "_stained_glass".length());
        if (n.endsWith("_stained_glass_pane")) return n.substring(10, n.length() - "_stained_glass_pane".length());
        return null;
    }

    @Override
    public void tick(BlockLevel level) {
        int x = pos.x(), y = pos.y(), z = pos.z();
        if (lastCheckY == Integer.MIN_VALUE) lastCheckY = level.minBuildHeight() - 1;   // 바닐라 setLevel
        Pos origin;
        if (lastCheckY < y) {
            origin = pos;
            checkingBeamSections = new ArrayList<>();
            lastCheckY = y - 1;
        } else {
            origin = new Pos(x, lastCheckY + 1, z);
        }
        Section section = checkingBeamSections.isEmpty() ? null : checkingBeamSections.get(checkingBeamSections.size() - 1);
        int topY = level.worldSurfaceHeight(x, z);
        for (int i = 0; i < 10 && origin.y() <= topY; i++) {
            int state = level.getBlockState(origin);
            String color = stainedGlass(state);
            if (color != null) {
                if (checkingBeamSections.size() <= 1) {
                    section = new Section(color);
                    checkingBeamSections.add(section);
                } else if (section != null) {
                    if (color.equals(section.color)) section.height++;
                    else {   // 색이 달라지면 새 구간(바닐라는 평균 색 — 서버가 보는 값이 아니라 구간 이름만 쓴다)
                        section = new Section(color);
                        checkingBeamSections.add(section);
                    }
                }
            } else {
                // TODO(빛): 바닐라는 getLightBlock(state) >= 15 && 기반암 아님. 지금은 위로 빛을 완전히 막는지로 근사.
                if (section == null || B.lightBlockedAbove(state) && !B.is(state, "bedrock")) {
                    checkingBeamSections.clear();
                    lastCheckY = topY;
                    break;
                }
                section.height++;
            }
            origin = origin.above();
            lastCheckY++;
        }
        int before = levels;
        if (level.gameTime() % 80L == 0L) {
            if (!beamSections.isEmpty()) levels = updateBase(level, x, y, z);
            if (levels > 0 && !beamSections.isEmpty()) {
                applyEffects(level, pos, levels, primaryPower, secondaryPower);
                level.playSound(null, pos, "minecraft:block.beacon.ambient", "blocks", 1.0F, 1.0F);
            }
        }
        if (lastCheckY >= topY) {
            lastCheckY = level.minBuildHeight() - 1;
            boolean wasActive = before > 0;
            beamSections = checkingBeamSections;
            boolean active = levels > 0;
            if (!wasActive && active) {
                level.playSound(null, pos, "minecraft:block.beacon.activate", "blocks", 1.0F, 1.0F);
                // TODO(발전 과제): 근처 플레이어에게 construct_beacon 조건 발동.
            } else if (wasActive && !active) {
                level.playSound(null, pos, "minecraft:block.beacon.deactivate", "blocks", 1.0F, 1.0F);
            }
        }
    }

    /** 바닐라 applyEffects: 범위 levels*10+10 안(아래로 월드 높이만큼) 플레이어에게 primary, 4단이면 secondary 도. */
    public static void applyEffects(BlockLevel level, Pos pos, int levels, String primary, String secondary) {
        MobEffect p = primary == null ? null : MobEffect.byName(primary);
        if (p == null) return;
        double range = levels * 10 + 10;
        int amp = levels >= 4 && primary.equals(secondary) ? 1 : 0;
        int duration = (9 + levels * 2) * 20;
        AABB box = AABB.of(pos.x(), pos.y(), pos.z(), pos.x() + 1, pos.y() + 1, pos.z() + 1).inflate(range)
                .expandTowards(0.0, level.maxBuildHeight() - level.minBuildHeight(), 0.0);
        List<Player> players = new ArrayList<>();
        level.forEachPlayerIn(box, players::add);
        for (Player pl : players) pl.vitals().addEffect(new MobEffectInstance(p, duration, amp, true, true));
        MobEffect s = secondary == null ? null : MobEffect.byName(secondary);
        if (levels >= 4 && !primary.equals(secondary) && s != null) {
            for (Player pl : players) pl.vitals().addEffect(new MobEffectInstance(s, duration, 0, true, true));
        }
    }

    /** 바닐라 updateBase: 단 1..4 가 각각 전부 beacon_base_blocks 인 단 수. */
    private static int updateBase(BlockLevel level, int x, int y, int z) {
        int n = 0;
        for (int tier = 1; tier <= 4; n = tier++) {
            int yy = y - tier;
            if (yy < level.minBuildHeight()) break;
            boolean ok = true;
            for (int xx = x - tier; xx <= x + tier && ok; xx++) {
                for (int zz = z - tier; zz <= z + tier; zz++) {
                    if (!B.isTag(level.getBlockState(new Pos(xx, yy, zz)), "minecraft:beacon_base_blocks")) { ok = false; break; }
                }
            }
            if (!ok) break;
        }
        return n;
    }

    /** 바닐라 data 1 set: 빛줄기가 열려 있으면 선택음. */
    public void setPrimary(String effect, BlockLevel level) {
        if (level != null && !beamSections.isEmpty()) level.playSound(null, pos, "minecraft:block.beacon.power_select", "blocks", 1.0F, 1.0F);
        primaryPower = filterEffect(effect);
    }

    public void setSecondary(String effect) { secondaryPower = filterEffect(effect); }

    @Override
    public void load(NbtCompound tag) {
        primaryPower = tag.tags().get("primary_effect") instanceof String s ? filterEffect(s) : null;
        secondaryPower = tag.tags().get("secondary_effect") instanceof String s ? filterEffect(s) : null;
        customName = tag.tags().get("CustomName") instanceof String s ? s : null;
        lock = tag.tags().get("Lock") instanceof String l && !l.isEmpty() ? l : null;
    }

    /** 잠금(LockCode 열쇠 이름) — 보존만. */
    public String lock;

    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        customName = in.get("minecraft:custom_name") instanceof String n ? n : null;
        lock = in.get("minecraft:lock") instanceof String l && !l.isEmpty() ? l : null;
    }

    @Override
    protected void collectImplicitComponents(java.util.Map<String, Object> out) {
        out.put("minecraft:custom_name", customName);
        if (lock != null) out.put("minecraft:lock", lock);
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        if (primaryPower != null) tag.putString("primary_effect", primaryPower);
        if (secondaryPower != null) tag.putString("secondary_effect", secondaryPower);
        tag.putInt("Levels", levels);
        if (customName != null) tag.putString("CustomName", customName);
        if (lock != null) tag.putString("Lock", lock);
    }

    /** 청크 패킷/갱신: 바닐라 getUpdateTag = saveCustomOnly(Levels·효과·CustomName·Lock). */
    @Override
    public NbtCompound updateTag() { return saveCustomOnly(); }
}
