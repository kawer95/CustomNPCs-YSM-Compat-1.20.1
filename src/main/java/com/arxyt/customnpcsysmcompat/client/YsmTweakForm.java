package com.arxyt.customnpcsysmcompat.client;

import com.arxyt.customnpcsysmcompat.data.YsmTweakKind;

import java.util.List;

/** Deobfuscated view of one model-provided YSM config form. */
public sealed interface YsmTweakForm permits YsmTweakForm.Checkbox, YsmTweakForm.Range, YsmTweakForm.Radio {
    int index();

    String title();

    String description();

    String variable();

    YsmTweakKind kind();

    record Checkbox(int index, String title, String description, String variable) implements YsmTweakForm {
        @Override
        public YsmTweakKind kind() {
            return YsmTweakKind.CHECKBOX;
        }
    }

    record Range(int index, String title, String description, String variable,
                 double step, double min, double max) implements YsmTweakForm {
        @Override
        public YsmTweakKind kind() {
            return YsmTweakKind.RANGE;
        }
    }

    record Radio(int index, String title, String description, String variable,
                 List<String> choices) implements YsmTweakForm {
        public Radio {
            choices = List.copyOf(choices);
        }

        @Override
        public YsmTweakKind kind() {
            return YsmTweakKind.RADIO;
        }
    }
}
