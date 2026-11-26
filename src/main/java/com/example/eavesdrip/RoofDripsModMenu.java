package com.example.eavesdrip;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Makes the "Configure" button appear in Mod Menu. */
public class RoofDripsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                return RoofDripsConfigScreen.create(parent);
            } catch (NoClassDefFoundError e) {
                // YetAnotherConfigLib isn't installed: just stay on the current screen.
                return parent;
            }
        };
    }
}
