package com.arxyt.customnpcsysmcompat;

import com.arxyt.dominionsword.api.DominionActionCatalog;
import com.arxyt.dominionsword.api.DominionActionCatalogProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads formal extra_animation metadata on either logical side without exposing YSM symbols. */
public final class YsmActionCatalogProvider implements DominionActionCatalogProvider {
    @Override public DominionActionCatalog catalog(String actionSetId) {
        String model = normalize(actionSetId);
        if (model.isBlank()) return DominionActionCatalog.EMPTY;
        try {
            Object properties = properties(model);
            if (properties == null) return DominionActionCatalog.EMPTY;
            return fromMaps(model, map(invoke(properties, "OOOOo0O0oO0OOo0O0O0Oo0O0")),
                    map(invoke(properties, "oo0OoO00oOoo000O0000o0oo")));
        } catch (Throwable error) {
            CustomNpcsYsmCompat.LOGGER.warn("Unable to read YSM action catalogue for {}", model, error);
            return DominionActionCatalog.EMPTY;
        }
    }

    static DominionActionCatalog fromMaps(String model, Map<?, ?> root, Map<?, ?> classes) {
            List<DominionActionCatalog.ActionEntry> actions = entries(root == null ? Map.of() : root);
            List<Route> rootRoutes = routes(root == null ? Map.of() : root);
            ArrayList<DominionActionCatalog.ActionGroup> groups = new ArrayList<>();
            for (Map.Entry<?, ?> entry : (classes == null ? Map.of() : classes).entrySet()) {
                String id = clean(entry.getKey());
                if (route(id) || id.isBlank()) continue;
                Map<?, ?> contents = map(entry.getValue());
                List<DominionActionCatalog.ActionEntry> children = entries(contents);
                List<String> childGroups = routes(contents).stream().map(Route::id).toList();
                String label = rootRoutes.stream().filter(route -> route.id.equals(id)).map(Route::label).findFirst().orElse(id);
                if (!children.isEmpty() || !childGroups.isEmpty()) groups.add(new DominionActionCatalog.ActionGroup(id, label, "", children, childGroups));
            }
            List<String> roots = rootRoutes.stream().map(Route::id).filter(id -> groups.stream().anyMatch(group -> group.id().equals(id))).toList();
            if (roots.isEmpty()) roots = groups.stream().map(DominionActionCatalog.ActionGroup::id).toList();
            return new DominionActionCatalog(model, actions, groups, roots);
    }

    private static Object properties(String model) throws ReflectiveOperationException {
        // The complete client resource registry includes ordinary player models.
        try {
            Class<?> registry = Class.forName("com.elfmcys.yesstevemodel.o0OooO00ooo0OO000O0OoOoO");
            Object raw = registry.getMethod("o0OOooo0o0OO00OoOOOo0o0O").invoke(null);
            if (raw instanceof Map<?, ?> models && models.get(model) != null) {
                Object metadata = invoke(models.get(model), "Ooooo0oooO0oooOOOoO0000O");
                return invoke(metadata, "o0OOooo0o0OO00OoOOOo0o0O");
            }
        } catch (Throwable ignored) { }
        // Dedicated server registry stores the same properties one layer deeper.
        Class<?> registry = Class.forName("com.elfmcys.yesstevemodel.OoOoOoooO0O00oOoO00OOo00");
        Object optional = registry.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", String.class).invoke(null, model);
        if (!(optional instanceof Optional<?> found) || found.isEmpty()) return null;
        Object details = invoke(found.get(), "OOOOo0O0oO0OOo0O0O0Oo0O0");
        return invoke(details, "o0OOooo0o0OO00OoOOOo0o0O");
    }

    private static List<DominionActionCatalog.ActionEntry> entries(Map<?, ?> map) {
        LinkedHashMap<String, DominionActionCatalog.ActionEntry> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String id = clean(entry.getKey()), label = clean(entry.getValue());
            if (id.isBlank() || route(id) || route(label) || "empty".equalsIgnoreCase(id)) continue;
            out.putIfAbsent(id, new DominionActionCatalog.ActionEntry(id, label.isBlank() ? id : label, ""));
        }
        return List.copyOf(out.values());
    }
    private static boolean route(String value) { return value != null && value.trim().startsWith("#"); }
    private static List<Route> routes(Map<?, ?> map) {
        ArrayList<Route> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = clean(entry.getKey()), value = clean(entry.getValue());
            if (route(key) && key.length() > 1 && !"#return".equalsIgnoreCase(key)) out.add(new Route(key.substring(1), route(value) ? key.substring(1) : value));
            else if (route(value) && value.length() > 1 && !"#return".equalsIgnoreCase(value)) out.add(new Route(value.substring(1), key));
        }
        return List.copyOf(out);
    }
    private static String clean(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().replace('\\', '/'); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        if (target == null) return null;
        Method found = target.getClass().getMethod(method); return found.invoke(target);
    }
    private record Route(String id, String label) {}
}
