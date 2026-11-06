// 내부 접근 예제 플러그인 — 안정 API(엔티티 NBT)로 되는 일은 그쪽으로, 안 되는 일만 Internals 로 서버 내부 객체를 꺼내 쓴다.
// 빌드: 서버 jar(furnace-server.jar)를 compileOnly 로 둔다(이 모듈은 project(":furnace-server")). 설명은 docs/api/internals.md.
package org.domi.furnace.plugintest.internals;

import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.internal.Internals;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeInstance;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.world.RegionLevel;

public final class InternalsExamplePlugin extends FurnacePlugin implements Listener {

    private static final NbtCompound CALM = NbtCompound.parse("{NoAI:1b,Silent:1b}");
    private Internals internals;

    @Override
    public void onEnable() {
        internals = Internals.get();
        // 내부 클래스는 마인크래프트 버전마다 바뀔 수 있다 — 맞지 않는 서버에서는 NoSuchMethodError 대신 이유가 분명한 오류로 켜지지 않는다
        internals.requireMinecraft("1.21.1");
        server().events().register(this, this);
        server().commands().register(this, "calm", (sender, args) -> calmNearby(sender));
        server().commands().register(this, "lightlevel", (sender, args) -> lightLevel(sender));
    }

    /** 1단(안정 API): 내부 클래스 없이 /data merge 와 같은 NBT 합치기로 주변 몹을 멈춘다. 버전이 바뀌어도 키만 같으면 그대로 돈다. */
    private void calmNearby(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.world().nearbyEntities(p.location(), 16).thenAccept(list -> {
            for (Entity e : list) {
                if (!(e instanceof Player)) e.mergeNbt(CALM);
            }
        });
    }

    /** 2단(내부 객체, 비동기): 그 칸을 가진 region 스레드에서 RegionLevel 을 꺼내 빛 세기를 읽는다. */
    private void lightLevel(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        var pos = p.location().blockPos();
        internals.withLevel(p.world(), pos, RegionLevel.class,
                        level -> level.getRawBrightness(new org.domi.furnace.server.block.Pos(pos.x(), pos.y(), pos.z()), level.skyDarken()))
                .thenAccept(light -> p.sendMessage(Text.text("빛: " + light)));
    }

    /** 2단(내부 객체, 동기): 이벤트 처리기는 그 엔티티를 가진 region 스레드에서 돈다 → entityNow 로 바로 꺼낸다. 맞은 좀비는 조금씩 빨라진다. */
    @Subscribe(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!e.entity().type().equals("minecraft:zombie")) return;
        Mob mob = internals.entityNow(e.entity(), Mob.class);
        AttributeInstance speed = mob.attributes().get(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(Math.min(0.5, speed.baseValue() * 1.1));
    }
}
