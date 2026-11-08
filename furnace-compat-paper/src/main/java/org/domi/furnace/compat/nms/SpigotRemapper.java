package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

/**
 * Spigot 이름 플러그인 바이트코드의 NMS 참조를 Mojang 이름으로 바꾸는 ASM {@link Remapper}(H7d). 변환기는 이것을 CraftBukkit 접미사 풀기와
 * 한 번에 건다: 클래스 이름은 먼저 Spigot → Mojang, 그 다음 접미사 풀기. 멤버 이름은 ASM 이 원래(Spigot) 소유자·서술자로 묻는다.
 *
 * <p>상속된 멤버를 찾는 위 클래스 사슬은 이 서버의 shim 클래스 파일({@link NmsSymbols}, Mojang 이름)에서 얻는다 — shim 이 없는 클래스의
 * 멤버는 어차피 덫에 걸리므로 그 사슬이면 충분하다. 플러그인 자기 클래스({@code own})는 바꾸지 않는다.
 */
final class SpigotRemapper extends Remapper {

    private final SpigotMappings mappings;
    private final NmsSymbols symbols;
    private final Set<String> own;

    SpigotRemapper(SpigotMappings mappings, NmsSymbols symbols, Set<String> own) {
        this.mappings = mappings;
        this.symbols = symbols;
        this.own = own;
    }

    @Override
    public String map(String internalName) {
        if (own.contains(internalName) || !internalName.startsWith("net/minecraft/") && !internalName.startsWith("com/mojang/")) return internalName;
        String m = mappings.mapClass(internalName);
        return m != null ? m : internalName;
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (skip(owner)) return name;
        return mappings.mapMethod(owner, name, descriptor, this::supers);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (skip(owner)) return name;
        return mappings.mapField(owner, name, descriptor, this::supers);
    }

    /** 람다·메서드 참조가 NMS 함수형 인터페이스를 구현하면 그 인터페이스 메서드 이름도 바꾼다(ASM 9.7 은 SAM 서술자를 주지 않아 이름만으로). */
    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        Type itf = Type.getReturnType(descriptor);
        if (itf.getSort() == Type.OBJECT && !skip(itf.getInternalName())) {
            return mappings.mapMethodAnyDesc(itf.getInternalName(), name, this::supers);
        }
        return name;
    }

    private boolean skip(String owner) {
        String e = NmsSymbols.element(owner);
        return e == null || own.contains(e) || !e.startsWith("net/minecraft/") && !e.startsWith("com/mojang/");
    }

    /** 난독화 클래스의 바로 위 클래스·인터페이스(난독화 이름) — shim(Mojang 이름) 사슬을 거꾸로 옮긴다. */
    private List<String> supers(String obf) {
        String mojang = mappings.mojangOf(obf);
        if (mojang == null) return List.of();
        List<String> out = new ArrayList<>();
        String sup = symbols.superOf(mojang);
        if (sup != null) add(out, sup);
        for (String i : symbols.interfacesOf(mojang)) add(out, i);
        return out;
    }

    private void add(List<String> out, String mojang) {
        String o = mappings.obfOfMojang(mojang);
        if (o != null) out.add(o);
    }

    /** 이 이름이 이 표가 바꿀 수 있는 Spigot NMS 클래스인가(보고용). */
    boolean isSpigotClass(String internalName) { return mappings.mapClass(internalName) != null; }

}
