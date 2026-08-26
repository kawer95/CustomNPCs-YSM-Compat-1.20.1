package com.arxyt.customnpcsysmcompat.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YsmDisplayDataTest {
    @Test
    void disabledWhenModelIdIsBlank() {
        assertFalse(new YsmDisplayData(true, "  ").enabled());
        assertEquals("", new YsmDisplayData(true, null).modelId());
    }

    @Test
    void trimsAndLimitsModelId() {
        assertEquals("pack/model", new YsmDisplayData(true, " pack/model ").modelId());
        String value = "x".repeat(YsmDisplayData.MAX_MODEL_ID_LENGTH + 20);
        assertEquals(YsmDisplayData.MAX_MODEL_ID_LENGTH, new YsmDisplayData(true, value).modelId().length());
    }

    @Test
    void searchMatchesNameAndIdIgnoringCase() {
        assertTrue(YsmDisplayData.matches("MAID", "pack/maid", "Example"));
        assertTrue(YsmDisplayData.matches("fox", "pack/model", "Wine Fox"));
        assertFalse(YsmDisplayData.matches("cat", "pack/model", "Wine Fox"));
    }

    @Test
    void nbtDefaultsAndRoundTrips() {
        assertEquals(YsmDisplayData.DISABLED, YsmNbtCodec.read(new CompoundTag()));

        CompoundTag root = new CompoundTag();
        YsmDisplayData selected = new YsmDisplayData(true, "pack/static_maid");
        YsmNbtCodec.write(root, selected);
        assertEquals(selected, YsmNbtCodec.read(root));
    }

    @Test
    void tweakProfilesRoundTripAndPreserveReplayOrder() {
        YsmTweakProfile profile = new YsmTweakProfile(List.of(
                YsmTweakEntry.range("body", 3, "v.size", 1.2D, 5L),
                YsmTweakEntry.checkbox("clothes", 0, "v.hat", true, 2L),
                YsmTweakEntry.radio("body", 4, "", "wide", 8L)
        ));
        YsmDisplayData selected = new YsmDisplayData(true, "pack/maid",
                Map.of("pack/maid", profile, "pack/fox", YsmTweakProfile.EMPTY));

        CompoundTag root = new CompoundTag();
        YsmNbtCodec.write(root, selected);
        YsmDisplayData loaded = YsmNbtCodec.read(root);

        assertEquals(selected, loaded);
        assertEquals(List.of(2L, 5L, 8L), loaded.tweaksFor("pack/maid").entries().stream()
                .map(YsmTweakEntry::order).toList());
    }

    @Test
    void latestEditMovesAnOverlappingControlToEndOfReplayOrder() {
        YsmDisplayData initial = new YsmDisplayData(true, "pack/maid");
        YsmDisplayData updated = initial
                .withTweak("pack/maid", YsmTweakEntry.checkbox("config", 0, "v.hat", true, 0))
                .withTweak("pack/maid", YsmTweakEntry.radio("config", 1, "v.preset", "coat", 0))
                .withTweak("pack/maid", YsmTweakEntry.checkbox("config", 0, "v.hat", false, 0));

        List<YsmTweakEntry> entries = updated.tweaksFor("pack/maid").entries();
        assertEquals(2, entries.size());
        assertEquals("config#1", entries.get(0).identity());
        assertEquals("config#0", entries.get(1).identity());
        assertFalse(entries.get(1).booleanValue());
    }

    @Test
    void malformedValuesAreNotPersisted() {
        YsmTweakProfile profile = new YsmTweakProfile(List.of(
                YsmTweakEntry.range("config", 0, "v.size", Double.NaN, 0),
                YsmTweakEntry.range("config", 1, "v.size", Double.POSITIVE_INFINITY, 1),
                YsmTweakEntry.checkbox("", 2, "v.hat", true, 2)
        ));
        assertTrue(profile.isEmpty());
        assertEquals(YsmDisplayData.DISABLED,
                new YsmDisplayData(false, "", Map.of("bad", profile)));
    }

    @Test
    void disablingCurrentModelKeepsProfilesForLaterReuse() {
        YsmDisplayData configured = new YsmDisplayData(true, "pack/maid")
                .withTweak("pack/maid", YsmTweakEntry.checkbox("clothes", 0, "v.hat", true, 0));
        YsmDisplayData restoredVanilla = new YsmDisplayData(false, "", configured.tweakProfiles());

        CompoundTag root = new CompoundTag();
        YsmNbtCodec.write(root, restoredVanilla);
        YsmDisplayData loaded = YsmNbtCodec.read(root);

        assertFalse(loaded.enabled());
        assertEquals("", loaded.modelId());
        assertEquals(1, loaded.tweaksFor("pack/maid").entries().size());
    }
}
