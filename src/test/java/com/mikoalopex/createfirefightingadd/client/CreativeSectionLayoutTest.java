package com.mikoalopex.createfirefightingadd.client;

/** Standalone checks for the row math used by the creative section banners. */
public final class CreativeSectionLayoutTest {
    public static void main(String[] args) {
        expectRows(0, 9, 0);
        expectRows(1, 9, 1);
        expectRows(9, 9, 1);
        expectRows(10, 9, 2);
        expectRows(21, 12, 2);
        expectRows(0, 12, 0);

        expectRow("vanilla first heading", 0, CreativeSectionLayout.vanillaRow(0, 0));
        expectRow("vanilla scrolled heading", 2, CreativeSectionLayout.vanillaRow(4, 2));
        expectRow("vanilla heading above window", -2, CreativeSectionLayout.vanillaRow(0, 2));

        int[] threeSections = {21, 9, 14};
        expectRow("nine-column heading 0", 0, CreativeSectionLayout.slateRow(0, threeSections, 9));
        expectRow("nine-column heading 1", 4, CreativeSectionLayout.slateRow(1, threeSections, 9));
        expectRow("nine-column heading 2", 6, CreativeSectionLayout.slateRow(2, threeSections, 9));

        expectRow("Slate twelve-column heading 0", 0, CreativeSectionLayout.slateRow(0, threeSections, 12));
        expectRow("Slate twelve-column heading 1", 3, CreativeSectionLayout.slateRow(1, threeSections, 12));
        expectRow("Slate twelve-column heading 2", 5, CreativeSectionLayout.slateRow(2, threeSections, 12));

        expectRow("wide-grid single heading", 0,
            CreativeSectionLayout.slateRow(0, new int[] {57}, 24));

        expect("visible at top", CreativeSectionLayout.isVisible(0, 0, 4));
        expect("visible after scroll", CreativeSectionLayout.isVisible(3, 2, 4));
        expect("hidden above scroll", !CreativeSectionLayout.isVisible(0, 1, 4));
        expect("hidden below window", !CreativeSectionLayout.isVisible(6, 2, 4));
        expect("hidden on last visible row plus one", !CreativeSectionLayout.isVisible(4, 0, 4));

        expectThrows(() -> CreativeSectionLayout.rowsFor(1, 0));
        expectThrows(() -> CreativeSectionLayout.slateRow(-1, threeSections, 12));
        expectThrows(() -> CreativeSectionLayout.slateRow(3, threeSections, 12));
        expectThrows(() -> CreativeSectionLayout.slateRow(0, threeSections, 8));

        System.out.println("Creative section layout checks passed");
    }

    private static void expectRows(int itemCount, int columns, int expected) {
        expectRow("rows for " + itemCount + " items in " + columns + " columns",
            expected, CreativeSectionLayout.rowsFor(itemCount, columns));
    }

    private static void expectRow(String what, int expected, int actual) {
        if (expected != actual)
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
    }

    private static void expect(String what, boolean condition) {
        if (!condition)
            throw new AssertionError(what);
    }

    private static void expectThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private CreativeSectionLayoutTest() {}
}
