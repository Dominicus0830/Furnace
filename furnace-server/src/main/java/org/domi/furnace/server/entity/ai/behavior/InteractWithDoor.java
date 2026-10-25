// 바닐라 InteractWithDoor: 경로 위의 나무 문을 열고 지나가며, 지나간 문은 근처에 다른 몹이 지나가려는 게 아니면 다시 닫는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DoorBlock;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.navigation.Node;
import org.domi.furnace.server.entity.ai.navigation.Path;

public final class InteractWithDoor {
    private static final int COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE = 20;
    private static final double SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN = 3.0;
    private static final double MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS = 2.0;

    private InteractWithDoor() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            private Node lastCheckedNode;
            private int remainingCooldown;

            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!present(mob, MemoryModuleType.PATH) || !registered(mob, MemoryModuleType.DOORS_TO_CLOSE) || !registered(mob, MemoryModuleType.NEAREST_LIVING_ENTITIES)) return false;
                var brain = mob.getBrain();
                Path path = brain.getMemory(MemoryModuleType.PATH).get();
                Optional<Set<GlobalPos>> doors = brain.getMemory(MemoryModuleType.DOORS_TO_CLOSE);
                if (path.notStarted() || path.isDone()) return false;
                if (lastCheckedNode == path.getNextNode()) remainingCooldown = COOLDOWN_BEFORE_RERUNNING_IN_SAME_NODE;
                else if (--remainingCooldown > 0) return false;
                lastCheckedNode = path.getNextNode();
                Node prev = path.getPreviousNode(), next = path.getNextNode();
                Pos prevPos = new Pos(prev.x, prev.y, prev.z), nextPos = new Pos(next.x, next.y, next.z);
                int state = level.getBlockState(prevPos);
                if (DoorBlock.isWoodenDoor(state)) {
                    DoorBlock door = (DoorBlock) B.behavior(state);
                    if (!door.isOpen(state)) door.setOpen(null, level, state, prevPos, true);
                    doors = rememberDoorToClose(mob, doors, level, prevPos);
                }
                int state2 = level.getBlockState(nextPos);
                if (DoorBlock.isWoodenDoor(state2)) {
                    DoorBlock door = (DoorBlock) B.behavior(state2);
                    if (!door.isOpen(state2)) {
                        door.setOpen(null, level, state2, nextPos, true);
                        doors = rememberDoorToClose(mob, doors, level, nextPos);
                    }
                }
                if (doors.isPresent()) closeDoorsThatIHaveOpenedOrPassedThrough(level, mob, prevPos, nextPos, doors.get(), brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES));
                return true;
            }
        };
    }

    public static void closeDoorsThatIHaveOpenedOrPassedThrough(ChunkLevel level, Mob mob, Pos prev, Pos next, Set<GlobalPos> doors, Optional<List<Targetable>> nearby) {
        Iterator<GlobalPos> it = doors.iterator();
        while (it.hasNext()) {
            GlobalPos g = it.next();
            Pos pos = g.pos();
            if (!pos.equals(prev) && !pos.equals(next)) {
                if (isDoorTooFarAway(level, mob, g)) {
                    it.remove();
                } else {
                    int state = level.getBlockState(pos);
                    if (!DoorBlock.isWoodenDoor(state)) {
                        it.remove();
                    } else {
                        DoorBlock door = (DoorBlock) B.behavior(state);
                        if (!door.isOpen(state)) it.remove();
                        else if (areOtherMobsComingThroughDoor(mob, pos, nearby)) it.remove();
                        else {
                            door.setOpen(null, level, state, pos, false);
                            it.remove();
                        }
                    }
                }
            }
        }
    }

    private static boolean areOtherMobsComingThroughDoor(Mob mob, Pos pos, Optional<List<Targetable>> nearby) {
        if (nearby.isEmpty()) return false;
        for (Targetable t : nearby.get()) {
            if (t instanceof Mob m && m.type() == mob.type()) {
                double dx = pos.x() + 0.5 - m.x(), dy = pos.y() + 0.5 - m.y(), dz = pos.z() + 0.5 - m.z();
                if (dx * dx + dy * dy + dz * dz < MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS * MAX_DISTANCE_TO_HOLD_DOOR_OPEN_FOR_OTHER_MOBS && isMobComingThroughDoor(m, pos)) return true;
            }
        }
        return false;
    }

    private static boolean isMobComingThroughDoor(Mob mob, Pos pos) {
        var path = mob.getBrain().getMemory(MemoryModuleType.PATH);
        if (path.isEmpty() || path.get().isDone()) return false;
        Node prev = path.get().getPreviousNode();
        if (prev == null) return false;
        Node next = path.get().getNextNode();
        return pos.equals(new Pos(prev.x, prev.y, prev.z)) || pos.equals(new Pos(next.x, next.y, next.z));
    }

    private static boolean isDoorTooFarAway(ChunkLevel level, Mob mob, GlobalPos g) {
        double dx = g.pos().x() + 0.5 - mob.x(), dy = g.pos().y() + 0.5 - mob.y(), dz = g.pos().z() + 0.5 - mob.z();
        return !g.dimension().equals(level.dimension()) || !(dx * dx + dy * dy + dz * dz < SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN * SKIP_CLOSING_DOOR_IF_FURTHER_AWAY_THAN);
    }

    private static Optional<Set<GlobalPos>> rememberDoorToClose(Mob mob, Optional<Set<GlobalPos>> existing, ChunkLevel level, Pos pos) {
        GlobalPos g = new GlobalPos(level.dimension(), pos);
        if (existing.isPresent()) {
            existing.get().add(g);
            return existing;
        }
        Set<GlobalPos> set = new HashSet<>();
        set.add(g);
        mob.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE, set);
        return Optional.of(set);
    }
}
