package com.itmecasey.aeroflux.client.screen.widget;

import java.util.List;
import net.minecraft.network.chat.Component;

/** A widget that has a tooltip for the point the pointer is over. The screen shows it after everything else is drawn. */
public interface HoverInfo {
    /** The tooltip lines for a pointer position in screen pixels; empty for no tooltip. */
    List<Component> tooltip(int mouseX, int mouseY);
}
