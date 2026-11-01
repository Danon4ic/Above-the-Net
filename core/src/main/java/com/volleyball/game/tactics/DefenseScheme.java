package com.volleyball.game.tactics;

import com.volleyball.game.mechanics.BlockStyle;

/**
 * How the back row and the block defend against an attack. The defenders never know where the
 * ball will go: a scheme only says which shots the block takes away and where the defenders wait
 * for the rest (see {@code TeamAI.planDefense}).
 *
 * @param label       display name
 * @param blockStyle  the block's hands: square, closing the line or the diagonal
 * @param depthShift  extra depth of the back-row defenders (m, + = deeper)
 * @param crossShift  sideways shift of the back row towards the cross court (m, + = towards cross)
 * @param upLine      the line defender comes up behind the block (for tips), "rotational"
 * @param tipCover    the off-blocker stays close behind the block for tips and deflections
 */
public enum DefenseScheme {
    /** Line deep, middle back deep, cross; the off-blocker for sharp shots. The standard. */
    PERIMETER("Perimeter", BlockStyle.NORMAL, 0f, 0f, false, false),
    /** The line defender moves up for tips behind the block; the others rotate. */
    ROTATIONAL("Rotational (tips)", BlockStyle.NORMAL, 0f, 0f, true, false),
    /** The block takes the cross court; the defenders cover the line and the tip over the block. */
    CLOSE_CROSS("Close the diagonal", BlockStyle.CROSS, 0f, -1.5f, false, false),
    /** The block takes the line; the defenders shift to the cross court. */
    CLOSE_LINE("Close the line", BlockStyle.LINE, 0f, 1.5f, false, false),
    /** Everybody deeper: against hard hitters and deep shots. */
    DEEP("Deep defence", BlockStyle.NORMAL, 1.6f, 0f, false, false),
    /** Everybody closer to the net: against off-speed shots and tips. */
    UP("Up close", BlockStyle.NORMAL, -2.0f, 0f, false, true),
    /** Cover the block: a defender right behind it for deflections and tips. */
    COVER_BLOCK("Cover the block", BlockStyle.NORMAL, 0.3f, 0f, false, true);

    public final String label;
    public final BlockStyle blockStyle;
    public final float depthShift;
    public final float crossShift;
    public final boolean upLine;
    public final boolean tipCover;

    DefenseScheme(String label, BlockStyle blockStyle, float depthShift, float crossShift, boolean upLine, boolean tipCover) {
        this.label = label;
        this.blockStyle = blockStyle;
        this.depthShift = depthShift;
        this.crossShift = crossShift;
        this.upLine = upLine;
        this.tipCover = tipCover;
    }
}
