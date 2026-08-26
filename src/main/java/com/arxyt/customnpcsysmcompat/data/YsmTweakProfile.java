package com.arxyt.customnpcsysmcompat.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, ordered configuration overrides for one YSM model. */
public record YsmTweakProfile(List<YsmTweakEntry> entries) {
    public static final int MAX_ENTRIES = 256;
    public static final YsmTweakProfile EMPTY = new YsmTweakProfile(List.of());

    public YsmTweakProfile {
        Map<String, YsmTweakEntry> unique = new LinkedHashMap<>();
        if (entries != null) {
            for (YsmTweakEntry entry : entries) {
                if (entry == null || !entry.valid()) continue;
                // The newest value for the same form wins before serialization.
                YsmTweakEntry previous = unique.get(entry.identity());
                if (previous == null || previous.order() <= entry.order()) {
                    unique.put(entry.identity(), entry);
                }
                if (unique.size() >= MAX_ENTRIES) break;
            }
        }
        entries = unique.values().stream()
                .sorted(Comparator.comparingLong(YsmTweakEntry::order)
                        .thenComparing(YsmTweakEntry::identity))
                .toList();
    }

    public YsmTweakEntry find(String buttonId, int formIndex) {
        String identity = Objects.requireNonNullElse(buttonId, "").trim() + '#' + Math.max(0, formIndex);
        return entries.stream().filter(entry -> entry.identity().equals(identity)).findFirst().orElse(null);
    }

    public YsmTweakProfile with(YsmTweakEntry value) {
        if (value == null || !value.valid()) return this;
        List<YsmTweakEntry> updated = new ArrayList<>(entries.size() + 1);
        for (YsmTweakEntry entry : entries) {
            if (!entry.identity().equals(value.identity())) updated.add(entry);
        }
        if (updated.size() >= MAX_ENTRIES) return this;
        long nextOrder = updated.stream().mapToLong(YsmTweakEntry::order).max().orElse(-1L) + 1L;
        updated.add(value.withOrder(nextOrder));
        return new YsmTweakProfile(updated);
    }

    public YsmTweakProfile without(String buttonId, int formIndex) {
        String identity = Objects.requireNonNullElse(buttonId, "").trim() + '#' + Math.max(0, formIndex);
        List<YsmTweakEntry> updated = entries.stream()
                .filter(entry -> !entry.identity().equals(identity)).toList();
        return updated.size() == entries.size() ? this : new YsmTweakProfile(updated);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
