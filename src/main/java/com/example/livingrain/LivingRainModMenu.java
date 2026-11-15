package com.example.livingrain;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Makes the "Configure" button appear in Mod Menu. */
public class LivingRainModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            try {
                return LivingRainConfigScreen.create(parent);
            } catch (NoClassDefFoundError e) {
                // YetAnotherConfigLib isn't installed: just stay on the current screen.
                return parent;
            }
        };
    }
}
