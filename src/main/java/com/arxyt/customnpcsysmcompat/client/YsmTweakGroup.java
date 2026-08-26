package com.arxyt.customnpcsysmcompat.client;

import java.util.List;

/** A named group corresponding to one YSM extra_animation_button. */
public record YsmTweakGroup(String id, String title, String description, List<YsmTweakForm> forms) {
    public YsmTweakGroup {
        forms = List.copyOf(forms);
    }
}
