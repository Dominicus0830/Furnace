package org.domi.furnace.compat.paper.fixtures;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 다리의 "Furnace 이벤트 하나 = Bukkit 이벤트 객체 하나" 계약을 보는 플러그인(시험용, T-032).
 * 경험치: 우선순위 셋이 받은 Bukkit 객체를 차례로 적는다. 물리: LOW 가 받은 객체를 집합에 넣고 HIGH 가 같은 객체를 꺼낸다 —
 * 여러 스레드가 동시에 내도 짝이 어긋나면(다른 이벤트의 객체를 받으면) mismatch 가 오른다.
 */
public final class SlotProbePlugin extends JavaPlugin implements Listener {

    public static final List<Object> EXP = new CopyOnWriteArrayList<>();
    public static final Set<Object> PHYSICS_OPEN = ConcurrentHashMap.newKeySet();
    public static final AtomicInteger PHYSICS_LOW = new AtomicInteger();
    public static final AtomicInteger PHYSICS_HIGH = new AtomicInteger();
    public static final AtomicInteger MISMATCH = new AtomicInteger();
    public static final List<Object> BREAK = new CopyOnWriteArrayList<>();
    public static final AtomicInteger CHAT = new AtomicInteger();

    public static void reset() {
        EXP.clear();
        PHYSICS_OPEN.clear();
        PHYSICS_LOW.set(0);
        PHYSICS_HIGH.set(0);
        MISMATCH.set(0);
        BREAK.clear();
        CHAT.set(0);
    }

    @Override
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @EventHandler(priority = EventPriority.LOW) public void expLow(PlayerExpChangeEvent e) { EXP.add(e); }

    @EventHandler(priority = EventPriority.NORMAL) public void expNormal(PlayerExpChangeEvent e) { EXP.add(e); }

    @EventHandler(priority = EventPriority.HIGH) public void expHigh(PlayerExpChangeEvent e) { EXP.add(e); }

    @EventHandler(priority = EventPriority.LOW) public void breakLow(BlockBreakEvent e) { BREAK.add(e); }

    @EventHandler(priority = EventPriority.HIGH) public void breakHigh(BlockBreakEvent e) { BREAK.add(e); }

    /** 받은 Bukkit 객체를 붙잡지 않는다(다리가 채팅 이벤트를 붙잡는지 보는 시험용). */
    @EventHandler public void chat(AsyncPlayerChatEvent e) { CHAT.incrementAndGet(); }

    @EventHandler(priority = EventPriority.LOW)
    public void physicsLow(BlockPhysicsEvent e) {
        PHYSICS_LOW.incrementAndGet();
        if (!PHYSICS_OPEN.add(e)) MISMATCH.incrementAndGet();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void physicsHigh(BlockPhysicsEvent e) {
        PHYSICS_HIGH.incrementAndGet();
        if (!PHYSICS_OPEN.remove(e)) MISMATCH.incrementAndGet();
    }
}
