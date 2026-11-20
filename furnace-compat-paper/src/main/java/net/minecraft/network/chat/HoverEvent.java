package net.minecraft.network.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 글에 마우스를 올렸을 때 보여 주는 것(Mojang 이름 shim, 불변). 글(SHOW_TEXT)·아이템(SHOW_ITEM)·엔티티(SHOW_ENTITY)를 Furnace 호버 이벤트로 옮긴다.
 * 바닐라의 CODEC·legacy 변환 멤버는 없다(호출 위치에서 실패).
 */
public class HoverEvent {

    private final Action<?> action;
    private final Object value;

    public <T> HoverEvent(Action<T> action, T value) {
        this.action = Objects.requireNonNull(action, "action");
        this.value = Objects.requireNonNull(value, "value");
    }

    public Action<?> getAction() { return action; }

    /** 그 종류의 이벤트면 값, 다르면 null(바닐라와 같다). */
    @SuppressWarnings("unchecked")
    public <T> T getValue(Action<T> other) { return action == other ? (T) value : null; }

    /** 호환 모듈 전용. */
    org.domi.furnace.api.text.HoverEvent furnace() { return action.toFurnace(value); }

    /** 호환 모듈 전용. */
    static HoverEvent fromFurnace(org.domi.furnace.api.text.HoverEvent e) {
        if (e == null) return null;
        return switch (e) {
            case org.domi.furnace.api.text.HoverEvent.ShowText t -> new HoverEvent(Action.SHOW_TEXT, Component.fromFurnace(t.text()));
            case org.domi.furnace.api.text.HoverEvent.ShowItem i -> new HoverEvent(Action.SHOW_ITEM, new ItemStackInfo(i));
            case org.domi.furnace.api.text.HoverEvent.ShowEntity en -> new HoverEvent(Action.SHOW_ENTITY, new EntityTooltipInfo(en));
        };
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof HoverEvent h && action == h.action && value.equals(h.value);
    }

    @Override
    public String toString() { return "HoverEvent{action=" + action + ", value='" + value + "'}"; }

    @Override
    public int hashCode() { return 31 * action.hashCode() + value.hashCode(); }

    /** 이벤트 종류(바닐라 1.21.1: 세 개). */
    public static class Action<T> {
        public static final Action<Component> SHOW_TEXT = new Action<>("show_text", true);
        public static final Action<ItemStackInfo> SHOW_ITEM = new Action<>("show_item", true);
        public static final Action<EntityTooltipInfo> SHOW_ENTITY = new Action<>("show_entity", true);

        private final String name;
        private final boolean allowFromServer;

        private Action(String name, boolean allowFromServer) {
            this.name = name;
            this.allowFromServer = allowFromServer;
        }

        public boolean isAllowedFromServer() { return allowFromServer; }

        public String getSerializedName() { return name; }

        @Override
        public String toString() { return "<action " + name + ">"; }

        private org.domi.furnace.api.text.HoverEvent toFurnace(Object value) {
            if (this == SHOW_TEXT) return org.domi.furnace.api.text.HoverEvent.showText(Component.furnace((Component) value));
            if (this == SHOW_ITEM) return ((ItemStackInfo) value).furnace();
            return ((EntityTooltipInfo) value).furnace();
        }
    }

    /** SHOW_ITEM 의 값: 보여 줄 아이템 한 묶음(복사본). */
    public static class ItemStackInfo {
        private final net.minecraft.world.item.ItemStack stack;
        private final org.domi.furnace.api.text.HoverEvent.ShowItem source;

        public ItemStackInfo(net.minecraft.world.item.ItemStack stack) {
            this.stack = stack.copy();
            this.source = null;
        }

        ItemStackInfo(org.domi.furnace.api.text.HoverEvent.ShowItem source) {
            this.stack = null;
            this.source = source;
        }

        public net.minecraft.world.item.ItemStack getItemStack() {
            if (stack != null) return stack.copy();
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("HoverEvent.ItemStackInfo#getItemStack",
                    "an item hover read back from a Furnace text has no item object - only one built with ItemStackInfo(ItemStack) does");
        }

        org.domi.furnace.api.text.HoverEvent furnace() {
            if (source != null) return source;
            var v = stack.furnaceValue();
            StringBuilder sb = new StringBuilder();
            for (var e : v.components().entrySet()) sb.append(sb.length() == 0 ? "" : ",").append(e.getKey()).append(':').append(e.getValue());
            for (String r : v.removedComponents()) sb.append(sb.length() == 0 ? "" : ",").append('!').append(r).append(":{}");
            return new org.domi.furnace.api.text.HoverEvent.ShowItem(v.type(), v.amount(), sb.length() == 0 ? null : "{" + sb + "}");
        }

        @Override
        public boolean equals(Object o) { return this == o || o instanceof ItemStackInfo i && furnace().equals(i.furnace()); }

        @Override
        public int hashCode() { return furnace().hashCode(); }
    }

    /** SHOW_ENTITY 의 값: 엔티티 종류·UUID·이름. */
    public static class EntityTooltipInfo {
        public final net.minecraft.world.entity.EntityType<?> type;
        public final UUID id;
        public final Optional<Component> name;

        public EntityTooltipInfo(net.minecraft.world.entity.EntityType<?> type, UUID id, Optional<Component> name) {
            this.type = type;
            this.id = id;
            this.name = name;
        }

        public EntityTooltipInfo(net.minecraft.world.entity.EntityType<?> type, UUID id, Component name) { this(type, id, Optional.ofNullable(name)); }

        EntityTooltipInfo(org.domi.furnace.api.text.HoverEvent.ShowEntity e) {
            this(net.minecraft.world.entity.EntityType.of(net.minecraft.resources.ResourceLocation.parse(e.type())), e.id(),
                    Optional.ofNullable(e.name() == null ? null : Component.fromFurnace(e.name())));
        }

        org.domi.furnace.api.text.HoverEvent furnace() {
            return new org.domi.furnace.api.text.HoverEvent.ShowEntity(type.furnaceKey().toString(), id, name.map(Component::furnace).orElse(null));
        }

        @Override
        public boolean equals(Object o) { return this == o || o instanceof EntityTooltipInfo e && type.equals(e.type) && id.equals(e.id) && name.equals(e.name); }

        @Override
        public int hashCode() {
            int i = type.hashCode();
            i = 31 * i + id.hashCode();
            return 31 * i + name.hashCode();
        }
    }
}
