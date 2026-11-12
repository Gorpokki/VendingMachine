package com.cubefury.vendingmachine.client;

/**
 * The scroll bar arithmetic, kept free of rendering so a test can check it. {@code height} is the visible length of
 * the list and the track, {@code content} the full length of the list, and positions are measured from the track top.
 */
public final class ScrollThumb {

    private static final int MIN_LENGTH = 8;

    private ScrollThumb() {}

    public static int length(int height, int content) {
        return Math.max(MIN_LENGTH, height * height / content);
    }

    public static int top(int height, int content, int scroll) {
        return (height - length(height, content)) * scroll / (content - height);
    }

    /**
     * Where a press at {@code pointer} grabs the thumb: the offset into the thumb when it lands on the thumb, or the
     * thumb centre when it lands on the track, so the thumb jumps there as in the original.
     */
    public static int grab(int height, int content, int scroll, int pointer) {
        int offset = pointer - top(height, content, scroll);
        int length = length(height, content);
        return offset >= 0 && offset < length ? offset : length / 2;
    }

    /** The scroll that puts the grabbed point of the thumb under {@code pointer}, clamped to the list. */
    public static int scrollFor(int height, int content, int pointer, int grab) {
        int range = content - height;
        if (range <= 0) return 0;
        int travel = height - length(height, content);
        int scroll = travel <= 0 ? 0 : (pointer - grab) * range / travel;
        return Math.clamp(scroll, 0, range);
    }
}
