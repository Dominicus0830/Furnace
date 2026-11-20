package com.destroystokyo.paper.event.entity;

import org.bukkit.Location;
import org.bukkit.block.EndGateway;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityTeleportEvent;

/** 플레이어가 아닌 엔티티가 엔드 관문으로 건너려 한다(Paper Entity#changeDimension). EntityTeleportEvent 의 처리기 목록을 같이 쓴다. */
public class EntityTeleportEndGatewayEvent extends EntityTeleportEvent {

    private final EndGateway gateway;

    public EntityTeleportEndGatewayEvent(Entity what, Location from, Location to, EndGateway gateway) {
        super(what, from, to);
        this.gateway = gateway;
    }

    /** 엔티티가 들어간 관문. */
    public EndGateway getGateway() { return gateway; }
}
