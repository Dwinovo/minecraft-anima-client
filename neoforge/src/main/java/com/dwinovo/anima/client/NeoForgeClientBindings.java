package com.dwinovo.anima.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class NeoForgeClientBindings {

    public static final KeyMapping OPEN_FEED_KEY = new KeyMapping(
        "key.anima.open_feed",
        GLFW.GLFW_KEY_I,
        "key.categories.anima"
    );

    private NeoForgeClientBindings() {
    }
}
