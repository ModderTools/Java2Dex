package com.java2dex.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Reusable beautiful dialogs (HTML viewer etc.) */
public final class Dialogs {

    private Dialogs() {}

    /**
     * Shows a scrollable, rounded, animated HTML dialog.
     * Height = 68% of screen, width = screen - 32dp — always fits, always scrollable.
     */
    public static void html(final Activity a, String title, String assetFile) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int screenH = a.getResources().getDisplayMetrics().heightPixels;

        Theme t = Theme.get(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.outline(t.card, t.cardStroke, 22, 1, a));
        int pad = Ui.dp(16);
        root.setPadding(pad, pad, pad, pad);

        // ---- title row ----
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView tt = Ui.text(a, title, 17, t.text, true);
        head.addView(tt, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView x = Ui.text(a, "✕", 15, t.textSub, true);
        x.setGravity(Gravity.CENTER);
        x.setBackground(Ui.ripple(a, Ui.fill(t.chipBg, 20, a)));
        x.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(6));
        x.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        head.addView(x, new LinearLayout.LayoutParams(-2, -2));
        root.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // ---- web area (scrollable inside WebView) ----
        FrameLayout webWrap = new FrameLayout(a);
        webWrap.setBackground(Ui.outline(t.inputBg, t.cardStroke, 14, 1, a));
        int wp = Ui.dp(3);
        webWrap.setPadding(wp, wp, wp, wp);

        boolean has = false;
        try {
            for (String s : a.getAssets().list("")) if (s.equals(assetFile)) has = true;
        } catch (Throwable ignored) { }

        if (has) {
            WebView wv = new WebView(a);
            wv.getSettings().setJavaScriptEnabled(true);
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            wv.loadUrl("file:///android_asset/" + assetFile);
            webWrap.addView(wv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView tv = Ui.text(a, assetFile + " not found in app assets.", 13, t.textSub, false);
            int p2 = Ui.dp(16);
            tv.setPadding(p2, p2, p2, p2);
            webWrap.addView(tv, new FrameLayout.LayoutParams(-1, -2));
        }

        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1,
                (int) (screenH * 0.68f));
        wlp.topMargin = Ui.dp(12);
        root.addView(webWrap, wlp);

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams wl = w.getAttributes();
            wl.width = screenW - Ui.dp(30);
            w.setAttributes(wl);
        }
        d.show();
        Ui.popIn(root, 40);
    }
}
