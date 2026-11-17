package com.mikoalopex.createfirefightingadd.client;

/**
 * Row math shared by the vanilla nine-column creative grid and Slate UI's
 * wider, reflowed creative grid. Kept free of Minecraft types so the section
 * placement rules can be checked without a client.
 */
public final class CreativeSectionLayout {
    public static final int VANILLA_COLUMNS = 9;
    public static final int VANILLA_VISIBLE_ROWS = 5;
    public static final int CELL = 18;
    public static final int BANNER_WIDTH = 162;
    public static final int BANNER_HEIGHT = 18;
    public static final int SLATE_GRID_TOP = 29;

    private CreativeSectionLayout() {}

    /** Display row of a section heading in the vanilla grid after scrolling. */
    public static int vanillaRow(int sourceRow, int firstRow) {
        return sourceRow - firstRow;
    }

    /**
     * Display row of a section heading when a layout reflows the same item
     * sequence into {@code columns} columns. Each section adds one heading row;
     * its items occupy the rows that follow it. The padding inserted by
     * {@code CreativeSections} therefore never adds a second heading row.
     */
    public static int slateRow(int sectionIndex, int[] itemCounts, int columns) {
        if (columns < VANILLA_COLUMNS)
            throw new IllegalArgumentException("columns must be at least " + VANILLA_COLUMNS);
        if (sectionIndex < 0 || sectionIndex >= itemCounts.length)
            throw new IllegalArgumentException("section index out of range: " + sectionIndex);
        int row = sectionIndex;
        for (int i = 0; i < sectionIndex; i++)
            row += rowsFor(itemCounts[i], columns);
        return row;
    }

    /** Rows occupied by {@code itemCount} items in a grid of {@code columns}. */
    public static int rowsFor(int itemCount, int columns) {
        if (columns < 1)
            throw new IllegalArgumentException("columns must be positive");
        if (itemCount <= 0)
            return 0;
        return (itemCount + columns - 1) / columns;
    }

    /** Whether a laid-out heading row survives scrolling in a visible window. */
    public static boolean isVisible(int row, int scrollRow, int visibleRows) {
        int display = row - scrollRow;
        return display >= 0 && display < visibleRows;
    }
}
