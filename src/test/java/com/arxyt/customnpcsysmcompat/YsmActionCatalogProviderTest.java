package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YsmActionCatalogProviderTest {
    @Test void preservesArbitraryIdsAndFiltersConfigurationRoutes() {
        LinkedHashMap<String, String> root = new LinkedHashMap<>();
        root.put("extra0", "Wave");
        root.put("config", "#配置页面");
        root.put("#poses", "Poses");
        LinkedHashMap<String, String> poses = new LinkedHashMap<>();
        poses.put("custom.pose.19", "Salute"); poses.put("more", "#advanced"); poses.put("return", "#return");
        var catalog = YsmActionCatalogProvider.fromMaps("Pack/Model", root,
                Map.of("poses", poses, "advanced", Map.of("pose.deep", "Deep pose")));
        assertTrue(catalog.valid());
        assertTrue(catalog.contains("extra0"));
        assertTrue(catalog.contains("custom.pose.19"));
        assertFalse(catalog.contains("config"));
        assertEquals(List.of("poses"), catalog.rootGroups());
        assertEquals(List.of("advanced"), catalog.groups().stream().filter(group -> group.id().equals("poses")).findFirst().orElseThrow().childGroups());
    }
}
