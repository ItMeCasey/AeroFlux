package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.Panel;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The AeroFlux Guide: a short book with one page per topic. The text lives in the language file
 * ({@code guide.aeroflux.<page>.title} and {@code .body}), so it can be translated like
 * everything else; a {@code \n} in the body starts a new line.
 */
public class GuideScreen extends Screen {
    /** The pages, in reading order. */
    private static final List<String> PAGES = List.of(
            "start", "flux", "collectors", "pipes", "machines", "more_machines", "upgrades", "chunk_loader", "dimensions");
    private static final int WIDTH = 230;
    private static final int HEIGHT = 200;
    private static final int MARGIN = 12;

    /** Where the reader left off, kept while the game runs. */
    private static int lastPage;

    private int page;
    private int left;
    private int top;
    private Button previous;
    private Button next;

    public GuideScreen() {
        super(Component.translatable("item.aeroflux.guidebook"));
        this.page = lastPage;
    }

    /** Opens the guide; handed to the item by the client setup. */
    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> turn(-1))
                .bounds(left + MARGIN, top + HEIGHT - 26, 20, 16).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> turn(1))
                .bounds(left + WIDTH - MARGIN - 20, top + HEIGHT - 26, 20, 16).build());
        updateButtons();
    }

    private void turn(int by) {
        page = Math.clamp(page + by, 0, PAGES.size() - 1);
        lastPage = page;
        updateButtons();
    }

    private void updateButtons() {
        previous.active = page > 0;
        next.active = page < PAGES.size() - 1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String key = "guide.aeroflux." + PAGES.get(page);
        graphics.text(font, Component.translatable(key + ".title"), left + MARGIN, top + MARGIN, Panel.TEXT, false);

        int y = top + MARGIN + 16;
        for (String paragraph : Component.translatable(key + ".body").getString().split("\n")) {
            for (FormattedCharSequence line : font.split(Component.literal(paragraph), WIDTH - 2 * MARGIN)) {
                graphics.text(font, line, left + MARGIN, y, Panel.TEXT, false);
                y += font.lineHeight + 1;
            }
            y += 4;
        }
        Component number = Component.literal((page + 1) + " / " + PAGES.size());
        graphics.text(font, number, left + (WIDTH - font.width(number)) / 2, top + HEIGHT - 22, Panel.TEXT_MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
