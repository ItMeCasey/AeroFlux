package com.itmecasey.aeroflux.core.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuideBookTest {

    @Test
    void pageAndChapterIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (GuideBook.Chapter chapter : GuideBook.chapters()) {
            assertTrue(ids.add("chapter." + chapter.id()), chapter.id());
            assertTrue(!chapter.pages().isEmpty(), chapter.id() + " is empty");
        }
        for (GuideBook.Page page : GuideBook.pages()) {
            assertTrue(ids.add(page.id()), page.id());
        }
    }

    @Test
    void everyPageHasATitleAndABodyThatFits() {
        Map<String, String> text = GuideBook.translations();
        for (GuideBook.Page page : GuideBook.pages()) {
            assertTrue(text.containsKey(GuideBook.PREFIX + page.id() + ".title"), page.id());
            // A page holds about fifteen lines of 36 characters; stay well inside that.
            assertTrue(page.body().length() <= 520, page.id() + " is " + page.body().length() + " characters");
        }
    }

    @Test
    void wikiLinksAreWellFormed() {
        for (GuideBook.Page page : GuideBook.pages()) {
            if (page.wiki() != null) {
                assertTrue(page.wiki().matches("[a-z0-9-]+"), page.wiki());
                assertEquals(GuideBook.WIKI + "?p=" + page.wiki(), page.wikiUrl());
            }
        }
    }

    @Test
    void chapterOfFindsTheOwner() {
        GuideBook.Page page = GuideBook.chapters().get(1).pages().get(0);
        assertEquals(GuideBook.chapters().get(1), GuideBook.chapterOf(page));
    }
}
