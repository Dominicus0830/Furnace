// 머리 아이템의 스킨 자동 채우기(Paper SPIGOT-6558). 이름·UUID 만 있는 머리가 인벤토리 칸에 쓰이면 비동기로 프로필(스킨)을 찾아,
// 그 칸이 아직 그 프로필을 들고 있을 때만 채운 프로필로 바꾼다.
//
// Paper 는 CraftMetaSkull#applyToItem(메타를 NMS 아이템에 적용할 때) 에서 텍스처가 없으면 PlayerProfile#update() 를 돌려 결과를 그 NMS 스택에 다시 쓴다 —
// 그 NMS 스택은 곧 인벤토리 칸에 들어가는 것이라 "머리가 칸에 들어간 뒤 스킨이 채워진다" 로 보인다. Furnace 의 ItemStack 은 불변이라 그 쓰기를 칸 하나를
// 소유 스레드에서 읽고-비교하고-쓰는 Inventory#update 로 한다(읽기와 쓰기 사이에 다른 쓰기가 끼지 않는다, 칸이 그새 바뀌었으면 건드리지 않는다).
// 인벤토리에 붙지 않은 평범한 ItemStack 은 Paper 도 채우지 않는다(NMS 로 옮겨지지 않으므로).
package org.domi.furnace.compat.paper;

import com.destroystokyo.paper.profile.CraftPlayerProfile;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.nbt.Snbt;

public final class SkullProfileFill {

    /** 머리 아이템의 소유자 프로필 컴포넌트(머리 메타도 이 이름을 쓴다). */
    public static final String PROFILE = "minecraft:profile";

    private static final System.Logger LOG = System.getLogger("FurnaceCompat");

    private SkullProfileFill() {}

    /**
     * 이 스택이 막 칸에 **쓰였다**(쓰기가 성공한 뒤에 부른다). 채울 것이 있으면 비동기로 채우기를 시작한다(기다리지 않는다).
     * 이 메서드는 예외를 던지지 않는다 — 이상한 프로필(이름 16자 초과·속성 과다 등)이나 조회 풀 문제가 칸 쓰기를 망치면 안 된다.
     */
    public static void afterWrite(Inventory handle, int index, ItemStack written) {
        try {
            ProfileValue original = incomplete(written);
            if (original == null) return;
            CraftPlayerProfile lookup = lookupProfile(original);
            ProfileValue normalized = lookup.buildValue();   // 조회에 쓴 꼴(UUID 0 → null) — 못 찾았을 때 "아무것도 안 바뀜" 을 이것과 비교한다
            lookup.update().whenComplete((filled, error) -> {
                try {
                    if (error != null || filled == null) {
                        LOG.log(System.Logger.Level.DEBUG, "skull profile lookup failed", error);
                        return;
                    }
                    ProfileValue found = ((CraftPlayerProfile) filled).buildValue();
                    if (found.equals(normalized)) return;   // 못 찾음(404·429·네트워크) — 칸을 다시 쓰지 않는다
                    handle.update(index, cur -> replaceIfStillTheSame(cur, original, found)).whenComplete((changed, e) -> {
                        if (e == null) return;
                        // 칸이 사라짐·접속 끊김·범위 밖은 채울 곳이 없는 것이라 조용히, 그 밖의 예외는 버그일 수 있어 드러낸다
                        boolean expected = e instanceof IllegalStateException || e instanceof IndexOutOfBoundsException
                                || e.getCause() instanceof IllegalStateException || e.getCause() instanceof IndexOutOfBoundsException;
                        LOG.log(expected ? System.Logger.Level.DEBUG : System.Logger.Level.WARNING, "skull profile fill dropped", e);
                    });
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "skull profile fill failed", e);
                }
            });
        } catch (RuntimeException e) {   // 이상한 프로필·조회 풀이 막혔거나 닫힘
            LOG.log(System.Logger.Level.DEBUG, "skull profile lookup not started", e);
        }
    }

    private static final java.util.UUID NIL_UUID = new java.util.UUID(0L, 0L);

    /** 조회에 쓸 프로필 — 이름만 있는 머리는 UUID 0 으로 저장돼 있지만 조회에서는 "UUID 를 모른다"(null)로 본다(Paper CraftPlayerProfile 의 빈 UUID). */
    private static CraftPlayerProfile lookupProfile(ProfileValue value) {
        java.util.UUID id = NIL_UUID.equals(value.id()) ? null : value.id();
        CraftPlayerProfile profile = new CraftPlayerProfile(id, value.name());
        for (var p : value.properties()) profile.setProperty(p);
        return profile;
    }

    /** 머리 종류 아이템이 프로필을 들고 있는데 텍스처가 없으면 그 프로필, 아니면 null. */
    static ProfileValue incomplete(ItemStack stack) {
        if (stack == null || stack.isEmpty() || MetaKind.of(stack.type()) != MetaKind.SKULL) return null;
        ProfileValue p = profile(stack);
        if (p == null || !lookupProfile(p).getTextures().isEmpty()) return null;
        return p;
    }

    /** 칸의 지금 값이 여전히 original 프로필을 든 머리일 때만 채운 프로필로 바꾼 스택, 아니면 null(그대로 둔다). */
    static ItemStack replaceIfStillTheSame(ItemStack current, ProfileValue original, ProfileValue found) {
        if (current == null || current.isEmpty() || MetaKind.of(current.type()) != MetaKind.SKULL) return null;
        if (!original.equals(profile(current))) return null;
        return current.withComponent(PROFILE, Snbt.write(found.toNbt()));
    }

    private static ProfileValue profile(ItemStack stack) {
        String raw = stack.components().get(PROFILE);
        if (raw == null) return null;
        try {
            return ProfileValue.fromNbt(Snbt.parse(raw));
        } catch (RuntimeException e) {   // 읽을 수 없는 프로필(다른 형식)은 건드리지 않는다
            return null;
        }
    }
}
