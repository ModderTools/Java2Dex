package com.java2dex.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.widget.EditText;

/** EditText with a pinned line-number gutter (works with word-wrap on or off). */
public class CodeEditText extends EditText {

    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterBg = new Paint();
    private final Paint divider = new Paint();
    private final int basePad;
    private boolean ready;   // TextView ctor calls onTextChanged before our fields exist
    private int digits = 0;
    private int gutterW = 0;

    public CodeEditText(Context c, int numColor, int gutterColor, int dividerColor) {
        super(c);
        basePad = Ui.dp(c, 10);
        numPaint.setColor(numColor);
        numPaint.setTypeface(Typeface.MONOSPACE);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        gutterBg.setColor(gutterColor);
        divider.setColor(dividerColor);
        divider.setStrokeWidth(Math.max(1, Ui.dp(c, 1)));
        ready = true;
        updateGutter();
    }

    /** vertical/right padding of the text area; left padding is managed by the gutter */
    public void setCodePadding(int top, int right, int bottom) {
        super.setPadding(gutterW + basePad, top, right, bottom);
    }

    private void updateGutter() {
        if (!ready) return;
        int lines = 1;
        CharSequence t = getText();
        for (int i = 0, n = t.length(); i < n; i++) if (t.charAt(i) == '\n') lines++;
        int d = Math.max(2, String.valueOf(lines).length());
        if (d == digits && gutterW > 0) return;
        digits = d;
        numPaint.setTextSize(getTextSize() * 0.82f);
        gutterW = (int) (numPaint.measureText("8") * d) + basePad;
        super.setPadding(gutterW + basePad, getPaddingTop(), getPaddingRight(), getPaddingBottom());
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        if (ready) updateGutter();
    }

    @Override
    public void setTextSize(float size) {
        super.setTextSize(size);
        if (!ready) return;
        digits = 0; // force gutter re-measure
        updateGutter();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Layout l = getLayout();
        if (l == null) return;
        if (!ready) return;
        if (gutterW == 0) updateGutter();

        int sx = getScrollX(), sy = getScrollY();
        int top = getExtendedPaddingTop();
        canvas.drawRect(sx, sy, sx + gutterW, sy + getHeight(), gutterBg);
        canvas.drawLine(sx + gutterW, sy, sx + gutterW, sy + getHeight(), divider);

        CharSequence t = getText();
        int first = l.getLineForVertical(sy - top);
        int last = l.getLineForVertical(sy - top + getHeight());
        if (first < 0) first = 0;

        // paragraph number of the first visible line
        int number = 1;
        int firstStart = l.getLineStart(first);
        for (int i = 0; i < firstStart && i < t.length(); i++) if (t.charAt(i) == '\n') number++;
        // first visible row is a wrapped continuation → next paragraph start is number + 1
        if (!(firstStart == 0 || t.charAt(firstStart - 1) == '\n')) number++;

        int caretLine = -1;
        int sel = getSelectionStart();
        if (sel >= 0) caretLine = l.getLineForOffset(sel);

        for (int i = first; i <= last && i < l.getLineCount(); i++) {
            int start = l.getLineStart(i);
            boolean paraStart = start == 0 || t.charAt(start - 1) == '\n';
            if (!paraStart) continue;
            float y = l.getLineBaseline(i) + top;
            boolean active = i == caretLine;
            numPaint.setFakeBoldText(active);
            canvas.drawText(String.valueOf(number), sx + gutterW - basePad * 0.6f, y, numPaint);
            number++;
        }
        // paragraph counter for wrapped continuation lines is intentionally skipped
    }
}
