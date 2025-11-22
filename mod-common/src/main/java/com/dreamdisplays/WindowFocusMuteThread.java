package com.dreamdisplays;

import net.minecraft.client.Minecraft;
import com.dreamdisplays.screen.Screen;
import com.dreamdisplays.screen.ScreenManager;

public class WindowFocusMuteThread extends Thread {
    public static WindowFocusMuteThread instance;

    public WindowFocusMuteThread() {
        setDaemon(true);
        instance = this;
        setName("window-focus-mute-thread");
    }

    @Override
    public void run() {
        while (true) {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                break;
            }

            boolean focused = client.isWindowActive();

            if (PlatformlessInitializer.getConfig().muteOnAltTab) for (Screen screen : ScreenManager.getScreens()) {
                screen.mute(!focused);
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}