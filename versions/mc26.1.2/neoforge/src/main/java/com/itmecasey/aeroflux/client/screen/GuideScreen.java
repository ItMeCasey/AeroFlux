package com.itmecasey.aeroflux.client.screen;

import com.itmecasey.aeroflux.client.screen.widget.Panel;
import com.itmecasey.aeroflux.core.guide.GuideBook;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The AeroFlux Guide: a book in chapters. Page one is the contents, whose entries jump to the
 * first page of their chapter; every other page has its chapter's name on top, a button back to
 * the contents and, where the wiki has more, a button that opens it. What the book says lives in
 * {@link GuideBook}, and its text comes from the language file ({@code guide.aeroflux.<page>.title}
 * and {@code .body}), so it can be translated like everything else. A {@code \n} in a body starts
 * a new paragraph.
 */
public class GuideScreen extends Screen {
    private static final int WIDTH = 230;
    private static final int HEIGHT = 200;
    private static final int MARGIN = 12;
    /** The page index that stands for the contents. */
    private static final int CONTENTS = -1;

    /** Every page in reading order, across chapters. */
    private static final List<GuideBook.Page> PAGES = GuideBook.pages();

    /** Where the reader left off, kept while the game runs. */
    private static int lastPage = CONTENTS;

    private int page;
    private int left;
    private int top;

    public GuideScreen() {
        super(Component.translatable("item.aeroflux.guidebook"));
        this.page = lastPage < PAGES.size() ? lastPage : CONTENTS;
    }

    /** Opens the guide; handed to the item by the client setup. */
    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        layoutWidgets();
    }

    /** Rebuilds the buttons for the page being shown; the contents and the pages differ. */
    private void layoutWidgets() {
        clearWidgets();
        if (page == CONTENTS) {
            int y = top + MARGIN + 34;
            for (GuideBook.Chapter chapter : GuideBook.chapters()) {
                int first = PAGES.indexOf(chapter.pages().get(0));
                Component label = Component.literal((GuideBook.chapters().indexOf(chapter) + 1) + ". ")
                        .append(Component.translatable(GuideBook.PREFIX + "chapter." + chapter.id()));
                addRenderableWidget(Button.builder(label, button -> show(first))
                        .bounds(left + MARGIN, y, WIDTH - 2 * MARGIN, 18).build());
                y += 22;
            }
        } else {
            addRenderableWidget(Button.builder(Component.literal("≡"), button -> show(CONTENTS))
                    .bounds(left + WIDTH - MARGIN - 16, top + 8, 16, 14)
                    .tooltip(Tooltip.create(Component.translatable(GuideBook.PREFIX + "home.tooltip"))).build());
            String url = PAGES.get(page).wikiUrl();
            if (url != null) {
                addRenderableWidget(Button.builder(Component.translatable(GuideBook.PREFIX + "wiki"),
                                button -> ConfirmLinkScreen.confirmLinkNow(this, url))
                        .bounds(left + (WIDTH - 44) / 2, top + HEIGHT - 26, 44, 16)
                        .tooltip(Tooltip.create(Component.translatable(GuideBook.PREFIX + "wiki.tooltip"))).build());
            }
        }
        // Back from the first page leads to the contents, and next from the contents to the first page.
        addRenderableWidget(Button.builder(Component.literal("<"), button -> show(page - 1))
                .bounds(left + MARGIN, top + HEIGHT - 26, 20, 16).build()).active = page != CONTENTS;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> show(page + 1))
                .bounds(left + WIDTH - MARGIN - 20, top + HEIGHT - 26, 20, 16).build()).active = page < PAGES.size() - 1;
    }

    private void show(int index) {
        page = Math.clamp(index, CONTENTS, PAGES.size() - 1);
        lastPage = page;
        layoutWidgets();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        Panel.raised(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (page == CONTENTS) {
            graphics.text(font, Component.translatable(GuideBook.PREFIX + "contents.title"), left + MARGIN, top + MARGIN, Panel.TEXT, false);
            graphics.text(font, Component.translatable(GuideBook.PREFIX + "contents.hint"), left + MARGIN, top + MARGIN + 14, Panel.TEXT_MUTED, false);
            return;
        }
        GuideBook.Page current = PAGES.get(page);
        GuideBook.Chapter chapter = GuideBook.chapterOf(current);
        String key = GuideBook.PREFIX + current.id();
        graphics.text(font, Component.translatable(GuideBook.PREFIX + "chapter." + chapter.id()), left + MARGIN, top + 8, Panel.TEXT_MUTED, false);
        graphics.text(font, Component.translatable(key + ".title"), left + MARGIN, top + MARGIN + 6, Panel.TEXT, false);

        int y = top + MARGIN + 22;
        for (String paragraph : Component.translatable(key + ".body").getString().split("\n")) {
            for (FormattedCharSequence line : font.split(Component.literal(paragraph), WIDTH - 2 * MARGIN)) {
                graphics.text(font, line, left + MARGIN, y, Panel.TEXT, false);
                y += font.lineHeight + 1;
            }
            y += 4;
        }
        Component number = Component.literal((page + 1) + " / " + PAGES.size());
        graphics.text(font, number, left + WIDTH - MARGIN - 20 - font.width(number) - 4, top + HEIGHT - 22, Panel.TEXT_MUTED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
