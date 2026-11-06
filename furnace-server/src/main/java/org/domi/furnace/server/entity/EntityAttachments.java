// 바닐라 EntityAttachments 의 탑승 부분: 종류별 승객 부착점(passengerAttachments)·탈것 부착점(vehicleAttachment, ridingOffset 은 (0, -f, 0)).
// 값은 EntityType 빌더 정의. 정의가 없으면 승객은 (0, 높이, 0), 탈것은 (0, 0, 0). 몸 크기 배율(아기 등)만큼 늘이거나 줄인다.
package org.domi.furnace.server.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EntityAttachments {

    private EntityAttachments() {}

    private record Def(List<Vec3> passengers, Vec3 vehicle) {}

    private static final Map<String, Def> DEFS = new HashMap<>();

    private static void p(String name, float... ys) {
        Vec3[] v = new Vec3[ys.length];
        for (int i = 0; i < ys.length; i++) v[i] = new Vec3(0.0, ys[i], 0.0);
        put(name, List.of(v), null);
    }

    private static void put(String name, List<Vec3> passengers, Vec3 vehicle) {
        Def old = DEFS.get("minecraft:" + name);
        DEFS.put("minecraft:" + name, new Def(passengers != null ? passengers : old != null ? old.passengers : null,
                vehicle != null ? vehicle : old != null ? old.vehicle : null));
    }

    /** ridingOffset(f) = vehicleAttachment(0, -f, 0). */
    private static void r(String name, float offset) { put(name, null, new Vec3(0.0, -offset, 0.0)); }

    private static void pv(String name, double x, double y, double z) { put(name, List.of(new Vec3(x, y, z)), null); }

    static {
        r("allay", 0.04F);
        r("bogged", -0.7F);
        p("cat", 0.5125F);
        p("chest_minecart", 0.1875F);
        pv("chicken", 0.0, 0.7, -0.1);
        p("command_block_minecart", 0.1875F);
        p("cow", 1.36875F);
        p("donkey", 1.1125F);
        p("drowned", 2.0125F);
        r("drowned", -0.7F);
        p("elder_guardian", 2.350625F);
        p("ender_dragon", 3.0F);
        p("enderman", 2.80625F);
        p("endermite", 0.2375F);
        p("evoker", 2.0F);
        r("evoker", -0.6F);
        pv("fox", 0.0, 0.6375, -0.25);
        pv("frog", 0.0, 0.375, -0.25);
        p("furnace_minecart", 0.1875F);
        p("ghast", 4.0625F);
        r("ghast", 0.5F);
        r("giant", -3.75F);
        p("goat", 1.1125F);
        p("guardian", 0.975F);
        p("hoglin", 1.49375F);
        p("hopper_minecart", 0.1875F);
        p("horse", 1.44375F);
        p("husk", 2.075F);
        r("husk", -0.7F);
        p("illusioner", 2.0F);
        r("illusioner", -0.6F);
        pv("llama", 0.0, 1.37, -0.3);
        p("minecart", 0.1875F);
        p("mooshroom", 1.36875F);
        p("mule", 1.2125F);
        p("ocelot", 0.6375F);
        p("parrot", 0.4625F);
        p("phantom", 0.3375F);
        r("phantom", -0.125F);
        p("pig", 0.86875F);
        p("piglin", 2.0125F);
        r("piglin", -0.7F);
        p("piglin_brute", 2.0125F);
        r("piglin_brute", -0.7F);
        p("pillager", 2.0F);
        r("pillager", -0.6F);
        pv("ravager", 0.0, 2.2625, -0.0625);
        p("sheep", 1.2375F);
        p("silverfish", 0.2375F);
        r("skeleton", -0.7F);
        p("skeleton_horse", 1.31875F);
        p("sniffer", 2.09375F);
        p("spawner_minecart", 0.1875F);
        p("spider", 0.765F);
        r("stray", -0.7F);
        p("tnt_minecart", 0.1875F);
        pv("trader_llama", 0.0, 1.37, -0.3);
        pv("turtle", 0.0, 0.55625, -0.25);
        p("vex", 0.7375F);
        r("vex", 0.04F);
        p("vindicator", 2.0F);
        r("vindicator", -0.6F);
        p("warden", 3.15F);
        p("witch", 2.2625F);
        r("wither_skeleton", -0.875F);
        pv("wolf", 0.0, 0.81875, -0.0625);
        p("zoglin", 1.49375F);
        p("zombie", 2.0125F);
        r("zombie", -0.7F);
        p("zombie_horse", 1.31875F);
        p("zombie_villager", 2.125F);
        r("zombie_villager", -0.7F);
        p("zombified_piglin", 2.0F);
        r("zombified_piglin", -0.7F);
    }

    /** 플레이어의 탈것 부착점(Player.DEFAULT_VEHICLE_ATTACHMENT): 서 있을 때와 웅크릴 때 같다. */
    public static final Vec3 PLAYER_VEHICLE = new Vec3(0.0, 0.6, 0.0);

    /** EntityAttachments.get(PASSENGER, index, yRot) 의 getClamped: 번호를 마지막 점까지로 자르고 -yRot 만큼 돌린다. scale·height 는 지금 몸 크기. */
    public static Vec3 passenger(EntityType type, int index, float yRot, float height, float scale) {
        Def d = DEFS.get(type.name());
        Vec3 p;
        if (d == null || d.passengers == null) {
            p = new Vec3(0.0, height, 0.0);   // 기본값은 이미 지금 높이(배율 반영)
        } else {
            p = d.passengers.get(Math.clamp(index, 0, d.passengers.size() - 1)).scale(scale);
        }
        return rotate(p, yRot);
    }

    /** EntityAttachments.get(VEHICLE, 0, yRot). */
    public static Vec3 vehicle(EntityType type, float yRot, float scale) {
        Def d = DEFS.get(type.name());
        if (d == null || d.vehicle == null) return Vec3.ZERO;
        return rotate(d.vehicle.scale(scale), yRot);
    }

    static Vec3 rotate(Vec3 p, float yRot) { return p.yRot(-yRot * (float) (Math.PI / 180.0)); }
}
