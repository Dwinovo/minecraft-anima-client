package com.dwinovo.anima.client.feed;

import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

public final class SessionFeedScreen {

    private static final ScreenCallback FEED_SCREEN_CALLBACK = new ScreenCallback() {
        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldBlurBackground() {
            return false;
        }
    };

    private SessionFeedScreen() {
    }

    public static Screen create(String sessionId, @Nullable Screen previousScreen) {
        SessionFeedFragment fragment = new SessionFeedFragment();
        DataSet args = new DataSet();
        args.putString("sessionId", sessionId);
        fragment.setArguments(args);
        return MuiModApi.get().createScreen(fragment, FEED_SCREEN_CALLBACK, previousScreen, "Anima Feed");
    }

    public static boolean isOpenOn(@Nullable Screen screen) {
        if (!(screen instanceof MuiScreen muiScreen)) {
            return false;
        }
        return muiScreen.getFragment() instanceof SessionFeedFragment;
    }
}
