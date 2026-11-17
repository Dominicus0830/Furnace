package org.bukkit;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/** vibration 입자의 데이터: 날아갈 곳과 도착까지 틱. 출발점은 입자를 내는 위치다(1.21.1 와이어에 출발점이 없다). */
public class Vibration {

    private final Location origin;
    private final Destination destination;
    private final int arrivalTime;

    /** @deprecated 출발점은 쓰이지 않는다(입자 위치가 출발점). */
    @Deprecated
    public Vibration(Location origin, Destination destination, int arrivalTime) {
        this.origin = origin;
        this.destination = destination;
        this.arrivalTime = arrivalTime;
    }

    public Vibration(Destination destination, int arrivalTime) { this(null, destination, arrivalTime); }

    /** @deprecated 출발점(없으면 null). */
    @Deprecated
    public Location getOrigin() { return origin; }

    public Destination getDestination() { return destination; }

    /** 도착까지 틱. */
    public int getArrivalTime() { return arrivalTime; }

    /** 진동이 날아갈 곳. */
    public interface Destination {

        /** 블록 칸으로. */
        class BlockDestination implements Destination {
            private final Location block;

            public BlockDestination(Location block) {
                if (block == null) throw new IllegalArgumentException("block");
                this.block = block;
            }

            public BlockDestination(Block block) { this(block.getLocation()); }

            public Location getLocation() { return block; }

            public Block getBlock() { return block.getBlock(); }
        }

        /** 엔티티로(따라간다). */
        class EntityDestination implements Destination {
            private final Entity entity;

            public EntityDestination(Entity entity) {
                if (entity == null) throw new IllegalArgumentException("entity");
                this.entity = entity;
            }

            public Entity getEntity() { return entity; }
        }
    }
}
