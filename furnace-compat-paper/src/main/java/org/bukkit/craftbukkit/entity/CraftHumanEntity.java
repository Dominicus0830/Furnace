package org.bukkit.craftbukkit.entity;

import java.util.Set;
import org.bukkit.entity.HumanEntity;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

/**
 * CraftBukkit 계층을 맞추는 칸(사람 모양 엔티티). Furnace 에서 사람 모양은 플레이어뿐이라 대부분 구현은 {@link CraftPlayer} 에 있다.
 * 권한은 CraftBukkit 처럼 {@code perm} 필드의 {@link PermissibleBase} 에 맡긴다 — 권한 플러그인(LuckPerms 등)이 리플렉션으로
 * 이 필드를 자기 PermissibleBase 하위 클래스로 바꾼다(필드 이름·종류를 바꾸지 말 것).
 */
public abstract class CraftHumanEntity extends CraftLivingEntity implements HumanEntity {

    protected final PermissibleBase perm;

    protected CraftHumanEntity(org.domi.furnace.api.entity.LivingEntity handle) {
        super(handle);
        this.perm = new PermissibleBase(this);
    }

    /** 호환 모듈 전용 — 지금 끼워진 권한 계산 객체(권한 플러그인이 바꿨을 수 있다). */
    public PermissibleBase permissible() { return perm; }

    @Override
    public net.minecraft.world.entity.player.Player getHandle() { return (net.minecraft.world.entity.player.Player) super.getHandle(); }

    @Override
    public boolean isPermissionSet(String name) { return perm.isPermissionSet(name); }

    @Override
    public boolean isPermissionSet(Permission perm) { return this.perm.isPermissionSet(perm); }

    @Override
    public boolean hasPermission(String name) { return perm.hasPermission(name); }

    @Override
    public boolean hasPermission(Permission perm) { return this.perm.hasPermission(perm); }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return perm.addAttachment(plugin, name, value); }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) { return perm.addAttachment(plugin); }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return perm.addAttachment(plugin, name, value, ticks); }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return perm.addAttachment(plugin, ticks); }

    @Override
    public void removeAttachment(PermissionAttachment attachment) { perm.removeAttachment(attachment); }

    @Override
    public void recalculatePermissions() { perm.recalculatePermissions(); }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() { return perm.getEffectivePermissions(); }
}
