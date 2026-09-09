package com.java2dex.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.AlertDialog;

public class InfoActivity extends Activity {

    private Theme t;
    private long animDelay = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = Theme.get(this);
        getWindow().setStatusBarColor(Ui.GREEN_DEEP);
        buildUi();
    }

    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(14);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.outline(t.card, t.cardStroke, 16, 1, this));
        c.setElevation(Ui.dp(2));
        int pd = Ui.dp(16);
        c.setPadding(pd, pd, pd, pd);
        Ui.riseIn(c, animDelay);
        animDelay += 90;
        return c;
    }

    private TextView chip(String s) {
        TextView c = Ui.text(this, s, 11, t.accentDark, true);
        c.setBackground(Ui.fill(t.accentSoft, 20, this));
        c.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(6);
        lp.topMargin = Ui.dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(t.bg);
        setContentView(scroll);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));

        // hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(Ui.gradient(Ui.GREEN_DEEP, Ui.GREEN, 0, this));
        hero.setPadding(Ui.dp(18), Ui.dp(34), Ui.dp(18), Ui.dp(26));

        FrameLayout logoBox = new FrameLayout(this);
        logoBox.setBackground(Ui.ripple(this, Ui.fill(0x30FFFFFF, 24, this)));
        logoBox.setElevation(Ui.dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        int lp = Ui.dp(14);
        logo.setPadding(lp, lp, lp, lp);
        logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(logoBox, new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(96)));
        Ui.popIn(logoBox, 100);

        TextView name = Ui.text(this, "Java2Dex", 24, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = Ui.dp(14);
        hero.addView(name, np);
        Ui.riseIn(name, 250);

        TextView ver = Ui.text(this, "Version 2.0  •  \"Code IDE\"", 12.5f, 0xCCFFFFFF, false);
        ver.setGravity(Gravity.CENTER);
        hero.addView(ver);
        Ui.riseIn(ver, 320);

        TextView tag = Ui.text(this,
                "Compile Java → DEX on your phone.\nBuilt for the modding community ❤",
                12, 0xB3FFFFFF, false);
        tag.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Ui.dp(6);
        hero.addView(tag, tp);
        Ui.riseIn(tag, 380);

        col.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(30));
        col.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // stats
        LinearLayout stats = card();
        LinearLayout row = new LinearLayout(this);
        row.addView(stat("25+", "Features"), w());
        row.addView(stat("100%", "Offline"), w());
        row.addView(stat("3", "Build Stages"), w());
        stats.addView(row, new LinearLayout.LayoutParams(-1, -2));
        body.addView(stats, margin());

        // tech stack
        LinearLayout tech = card();
        tech.addView(Ui.text(this, "🛠  TECH STACK", 12, t.textSub, true));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this);
        r1.addView(chip("Eclipse ECJ 4.6.1"));
        r1.addView(chip("Google D8 (R8 2.1.75)"));
        r1.addView(chip("dexlib2 2.5.2"));
        chips.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(chip("GitHub Actions CI"));
        r2.addView(chip("100% Java UI"));
        chips.addView(r2);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(-1, -2);
        cblp.topMargin = Ui.dp(4);
        tech.addView(chips, cblp);
        body.addView(tech, margin());

        // features
        LinearLayout feat = card();
        feat.addView(Ui.text(this, "✨  FEATURE HIGHLIGHTS", 12, t.textSub, true));
        feature(feat, "⚡ One-tap Java → .class → .dex pipeline");
        feature(feat, "🧠 Built-in Code IDE (files, folders, import)");
        feature(feat, "🎨 Syntax highlighting, find, undo, snippets");
        feature(feat, "🧬 DEX Explorer — browse & read smali");
        feature(feat, "💾 Save all smali files with one tap");
        feature(feat, "📂 Auto-save to /storage/emulated/0/Java2Dex/");
        feature(feat, "🌙 Dark & light themes");
        feature(feat, "📁 Custom output folder support");
        feature(feat, "📊 Project dashboard with animated stats");
        feature(feat, "📄 Full build logs with copy-error");
        feature(feat, "↗ Share real .dex files to any app");
        feature(feat, "♻ Reset output folder / wipe projects");
        body.addView(feat, margin());

        // paths
        LinearLayout paths = card();
        paths.addView(Ui.text(this, "📂  PATHS", 12, t.textSub, true));
        TextView p1 = Ui.text(this,
                Project.java2dexRoot(this).getAbsolutePath()
                        + "/<project>/classes.dex", 11.5f, t.text, false);
        p1.setTypeface(Typeface.MONOSPACE);
        p1.setTextIsSelectable(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Ui.dp(6);
        paths.addView(p1, pp);
        body.addView(paths, margin());

        // developer
        LinearLayout dev = card();
        dev.addView(Ui.text(this, "👨‍💻  DEVELOPER", 12, t.textSub, true));
        TextView devBtn = Ui.button(this, "Open Developer Profile", Ui.GREEN);
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-1, -2);
        dbp.topMargin = Ui.dp(10);
        dev.addView(devBtn, dbp);
        devBtn.setOnClickListener(v -> showDeveloper());
        body.addView(dev, margin());

        TextView foot = Ui.text(this, "Made with ❤ for modders  •  ECJ & D8 by their owners",
                11, t.textSub, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(6), 0, Ui.dp(10));
        body.addView(foot);
    }

    private TextView stat(String num, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView n = Ui.text(this, num, 19, t.accentDark, true);
        n.setGravity(Gravity.CENTER);
        c.addView(n);
        TextView l = Ui.text(this, label, 10, t.textSub, false);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        c.setLayoutParams(lp);
        return c;
    }

    private void feature(LinearLayout parent, String s) {
        TextView f = Ui.text(this, s, 12.5f, t.text, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = Ui.dp(8);
        parent.addView(f, fp);
    }

    private void showDeveloper() {
        FrameLayout wrap = new FrameLayout(this);
        boolean hasFile = false;
        try {
            for (String s : getAssets().list("")) if (s.equals("developer.html")) hasFile = true;
        } catch (Exception ignored) { }
        if (hasFile) {
            WebView wv = new WebView(this);
            wv.getSettings().setJavaScriptEnabled(true);
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.loadUrl("file:///android_asset/developer.html");
            wrap.addView(wv, new FrameLayout.LayoutParams(-1, Ui.dp(380)));
        } else {
            TextView tv = Ui.text(this, "developer.html not found in assets.", 14, t.text, false);
            int p = Ui.dp(20);
            tv.setPadding(p, p, p, p);
            wrap.addView(tv);
        }
        new AlertDialog.Builder(this)
                .setTitle("👨‍💻  Developer")
                .setView(wrap)
                .setPositiveButton("Close", null)
                .show();
    }
}
