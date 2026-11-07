// Maven pom 한 개를 읽는다(라이브러리 자동 로딩의 전이 의존 해석용). 부모 pom·BOM 가져오기는 LibraryResolver 가 받아서 합친다.
// 지원 범위: parent, properties, dependencyManagement(import 포함), dependencies(scope·optional·classifier·type·exclusions).
// 지원하지 않는 것: 프로필, 플러그인, 버전 범위의 정확한 해석(범위는 아래 하한을 쓴다).
package org.domi.furnace.server.plugin.library;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class MavenPom {

    /** 의존 항목(값은 아직 ${} 가 풀리지 않았을 수 있다). */
    record Dep(String group, String artifact, String version, String scope, boolean optional, String classifier, String type,
               List<String> exclusions) {}

    final String group;
    final String artifact;
    final String version;
    final String packaging;
    final Coord parent;
    final Map<String, String> properties = new LinkedHashMap<>();
    final List<Dep> managed = new ArrayList<>();
    final List<Dep> dependencies = new ArrayList<>();

    record Coord(String group, String artifact, String version) {}

    private MavenPom(String group, String artifact, String version, String packaging, Coord parent) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.packaging = packaging;
        this.parent = parent;
    }

    static MavenPom parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        Element root = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
        Element pe = child(root, "parent");
        Coord parent = pe == null ? null : new Coord(text(pe, "groupId"), text(pe, "artifactId"), text(pe, "version"));
        String g = text(root, "groupId");
        String v = text(root, "version");
        if (g == null && parent != null) g = parent.group();
        if (v == null && parent != null) v = parent.version();
        String packaging = text(root, "packaging");
        MavenPom p = new MavenPom(g, text(root, "artifactId"), v, packaging == null ? "jar" : packaging, parent);
        Element props = child(root, "properties");
        if (props != null) {
            for (Node n = props.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) p.properties.put(e.getTagName(), e.getTextContent().strip());
            }
        }
        Element dm = child(root, "dependencyManagement");
        if (dm != null) readDeps(child(dm, "dependencies"), p.managed);
        readDeps(child(root, "dependencies"), p.dependencies);
        return p;
    }

    private static void readDeps(Element deps, List<Dep> out) {
        if (deps == null) return;
        for (Node n = deps.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e) || !e.getTagName().equals("dependency")) continue;
            List<String> ex = new ArrayList<>();
            Element exs = child(e, "exclusions");
            if (exs != null) {
                for (Node m = exs.getFirstChild(); m != null; m = m.getNextSibling()) {
                    if (m instanceof Element x && x.getTagName().equals("exclusion")) {
                        String xg = text(x, "groupId");
                        String xa = text(x, "artifactId");
                        ex.add((xg == null ? "*" : xg) + ":" + (xa == null ? "*" : xa));
                    }
                }
            }
            out.add(new Dep(text(e, "groupId"), text(e, "artifactId"), text(e, "version"), text(e, "scope"),
                    "true".equalsIgnoreCase(text(e, "optional")), text(e, "classifier"), text(e, "type"), ex));
        }
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(name)) return e;
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        if (e == null) return null;
        String s = e.getTextContent().strip();
        return s.isEmpty() ? null : s;
    }
}
