package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.data.YsmTweakEntry;
import com.arxyt.customnpcsysmcompat.data.YsmTweakProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YsmPreviewSessionTest {
    @Test
    void changingExistingValueCanBeAppliedWithoutReplacingProxy() {
        YsmTweakProfile before = new YsmTweakProfile(java.util.List.of(
                YsmTweakEntry.range("body", 0, "v.scale", 1.0D, 0)));
        YsmTweakProfile after = new YsmTweakProfile(java.util.List.of(
                YsmTweakEntry.range("body", 0, "v.scale", 1.5D, 1)));
        assertFalse(YsmPreviewSession.removedIdentity(before, after));
    }

    @Test
    void restoringDefaultRequiresCleanReplacementProxy() {
        YsmTweakProfile before = new YsmTweakProfile(java.util.List.of(
                YsmTweakEntry.checkbox("hat", 0, "v.hat", true, 0)));
        assertTrue(YsmPreviewSession.removedIdentity(before, YsmTweakProfile.EMPTY));
    }
}
