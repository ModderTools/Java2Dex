package com.java2dex.app;

import android.content.Context;

public class Theme {

    public final int bg, card, cardStroke, text, textSub, accent, accentDark,
            accentSoft, danger, dangerSoft, chipBg, inputBg;

    public Theme(boolean dark) {
        headerTop = 0xFF14532D;
        headerBottom = 0xFF16A34A;
        if (dark) {
            bg = 0xFF0B1220;
            card = 0xFF141E31;
            cardStroke = 0xFF22304A;
            text = 0xFFE2E8F0;
            textSub = 0xFF94A3B8;
            accent = 0xFF22C55E;
            accentDark = 0xFF16A34A;
            accentSoft = 0x3322C55E;
            danger = 0xFFEF4444;
            dangerSoft = 0x26EF4444;
            chipBg = 0xFF1D2A42;
            inputBg = 0xFF0F1A2E;
        } else {
            bg = 0xFFF0FDF4;
            card = 0xFFFFFFFF;
            cardStroke = 0xFFE2E8F0;
            text = 0xFF0F172A;
            textSub = 0xFF64748B;
            accent = 0xFF16A34A;
            accentDark = 0xFF15803D;
            accentSoft = 0x3322C55E;
            danger = 0xFFDC2626;
            dangerSoft = 0x26DC2626;
            chipBg = 0xFFF1F5F9;
            inputBg = 0xFFFFFFFF;
        }
    }

    public final int headerTop, headerBottom;

    public static Theme get(Context c) { return new Theme(Prefs.dark(c)); }
}
