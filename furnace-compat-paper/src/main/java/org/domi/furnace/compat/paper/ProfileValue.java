package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * 머리 블록·아이템이 저장하는 프로필(바닐라 ResolvableProfile)의 값. 이름·UUID 는 비어 있을 수 있고(null), 속성은 순서가 있다.
 * 블록 엔티티 NBT 의 {@code profile}({@code {name?, id?: int[4], properties?: [{name, value, signature?}]}} 또는 이름 문자열 하나)과 오간다.
 */
public record ProfileValue(UUID id, String name, List<ProfileProperty> properties) {

    public ProfileValue {
        properties = List.copyOf(properties);
    }

    /** 서버가 준 속성 목록(공개 API 형)을 Paper 속성으로. */
    public static List<ProfileProperty> fromApi(List<org.domi.furnace.api.entity.ProfileProperty> props) {
        List<ProfileProperty> out = new ArrayList<>(props.size());
        for (var p : props) out.add(new ProfileProperty(p.name(), p.value(), p.signature()));
        return out;
    }

    /** NBT {@code profile} 값 → 프로필. 모르는 꼴이면 null. */
    public static ProfileValue fromNbt(Object tag) {
        if (tag instanceof String s) return new ProfileValue(null, s, List.of());
        if (!(tag instanceof NbtCompound c)) return null;
        String name = c.get("name") instanceof String s ? s : null;
        UUID id = null;
        if (c.get("id") instanceof int[] a && a.length == 4) id = new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL), ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
        else if (c.get("id") instanceof String s) {
            try {
                id = UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
                // 서버가 읽지 못한 값은 저장 꼴에 남지 않는다
            }
        }
        List<ProfileProperty> props = new ArrayList<>();
        if (c.get("properties") instanceof NbtList l) {
            for (Object o : l.values()) {
                if (!(o instanceof NbtCompound p) || !(p.get("name") instanceof String n) || !(p.get("value") instanceof String v)) continue;
                props.add(new ProfileProperty(n, v, p.get("signature") instanceof String sig ? sig : null));
            }
        }
        return new ProfileValue(id, name, props);
    }

    /** 프로필 → NBT {@code profile}(서버가 저장하는 꼴: 비어 있지 않은 칸만, 속성이 없으면 properties 생략). */
    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        if (name != null) c.putString("name", name);
        if (id != null) {
            long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
            c.putIntArray("id", new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        }
        if (!properties.isEmpty()) {
            NbtList list = new NbtList();
            for (ProfileProperty p : properties) {
                NbtCompound e = new NbtCompound();
                e.putString("name", p.getName());
                e.putString("value", p.getValue());
                if (p.getSignature() != null) e.putString("signature", p.getSignature());
                list.add(e);
            }
            c.putList("properties", list);
        }
        return c;
    }
}
