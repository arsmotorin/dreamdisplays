package com.dreamdisplays;

import net.minecraft.client.Minecraft;
import com.dreamdisplays.screen.Screen;
import com.dreamdisplays.screen.Manager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class Focuser extends Thread {
    public static Focuser instance = new Focuser();

    public Focuser() {
        setDaemon(true);
        instance = this;
        setName("window-focus-mute-thread");
    }

    @Override
    public void run() {
        while (true) {
            Minecraft client = Minecraft.getInstance();

            boolean focused = client.isWindowActive();

            if (Initializer.getConfig().muteOnAltTab) for (Screen screen : Manager.getScreens()) {
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
