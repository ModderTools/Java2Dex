package com.java2dex.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class InfoActivity extends Activity {

    private Theme t;

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

    private LinearLayout.LayoutParams w() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.outline(t.card, t.cardStroke, 16, 1, this));
        c.setElevation(Ui.dp(2));
        int pd = Ui.dp(16);
        c.setPadding(pd, pd, pd, pd);
        Ui.riseIn(c, 100);
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
        logoBox.setBackground(Ui.fill(0x30FFFFFF, 24, this));
        logoBox.setElevation(Ui.dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        int lpad = Ui.dp(14);
        logo.setPadding(lpad, lpad, lpad, lpad);
        logoBox.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        hero.addView(logoBox, new LinearLayout.LayoutParams(Ui.dp(96), Ui.dp(96)));
        Ui.popIn(logoBox, 100);

        TextView name = Ui.text(this, "Java2Dex", 24, Color.WHITE, true);
        name.setGravity(Gravity.CENTER);
        hero.addView(name);
        Ui.riseIn(name, 250);

        TextView ver = Ui.text(this, "Version 2.0 • Code IDE", 12.5f, 0xCCFFFFFF, false);
        ver.setGravity(Gravity.CENTER);
        hero.addView(ver);
        Ui.riseIn(ver, 320);

        TextView tag = Ui.text(this,
                "Compile Java to DEX on your phone.\nBuilt for the modding community",
                12, 0xB3FFFFFF, false);
        tag.setGravity(Gravity.CENTER);
        hero.addView(tag);
        Ui.riseIn(tag, 380);

        col.addView(hero, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(16), Ui.dp(16), Ui.dp(16), Ui.dp(30));
        col.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // stats
        LinearLayout stats = card();
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.addView(stat("25+", "Features"), w());
        statRow.addView(stat("100%", "Offline"), w());
        statRow.addView(stat("3", "Build Stages"), w());
        stats.addView(statRow, new LinearLayout.LayoutParams(-1, -2));
        body.addView(stats, margin());

        // tech
        LinearLayout tech = card();
        tech.addView(Ui.text(this, "TECH STACK", 12, t.textSub, true));
        LinearLayout chipRow1 = new LinearLayout(this);
        chipRow1.setOrientation(LinearLayout.HORIZONTAL);
        chipRow1.addView(chip("ECJ 4.6.1"));
        chipRow1.addView(chip("Google D8"));
        chipRow1.addView(chip("Custom DEX Parser"));
        tech.addView(chipRow1);
        LinearLayout chipRow2 = new LinearLayout(this);
        chipRow2.setOrientation(LinearLayout.HORIZONTAL);
        chipRow2.addView(chip("GitHub Actions"));
        chipRow2.addView(chip("100% Java UI"));
        tech.addView(chipRow2);
        body.addView(tech, margin());

        // features
        LinearLayout feat = card();
        feat.addView(Ui.text(this, "FEATURE HIGHLIGHTS", 12, t.textSub, true));
        addFeature(feat, "One-tap Java to DEX pipeline");
        addFeature(feat, "AIDE-style Code IDE with file explorer");
        addFeature(feat, "Syntax highlighting and snippets");
        addFeature(feat, "DEX Explorer with real smali output");
        addFeature(feat, "Save all smali files");
        addFeature(feat, "Auto-save to Java2Dex folder");
        addFeature(feat, "Dark and light themes");
        addFeature(feat, "Custom output folder");
        addFeature(feat, "Animated project dashboard");
        addFeature(feat, "Full build logs with copy");
        addFeature(feat, "Share real dex files");
        addFeature(feat, "Reset folder and wipe data");
        body.addView(feat, margin());

        // paths
        LinearLayout paths = card();
        paths.addView(Ui.text(this, "OUTPUT PATH", 12, t.textSub, true));
        TextView pathTv = Ui.text(this,
                Project.java2dexRoot(this).getAbsolutePath() + "/<project>/classes.dex",
                11.5f, t.text, false);
        pathTv.setTypeface(Typeface.MONOSPACE);
        pathTv.setTextIsSelectable(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = Ui.dp(6);
        paths.addView(pathTv, pp);
        body.addView(paths, margin());

        // developer
        LinearLayout dev = card();
        dev.addView(Ui.text(this, "DEVELOPER", 12, t.textSub, true));
        TextView devBtn = Ui.button(this, "Open Developer Profile", Ui.GREEN);
        devBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        dev.addView(devBtn);
        devBtn.setOnClickListener(v -> Dialogs.html(this, "👨‍💻 Developer", "developer.html"));
        body.addView(dev, margin());

        TextView foot = Ui.text(this, "Made with love for modders", 11, t.textSub, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, Ui.dp(6), 0, Ui.dp(10));
        body.addView(foot);
    }

    private LinearLayout stat(String num, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        TextView n = Ui.text(this, num, 19, t.accentDark, true);
        n.setGravity(Gravity.CENTER);
        c.addView(n);
        TextView l = Ui.text(this, label, 10, t.textSub, false);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        return c;
    }

    private void addFeature(LinearLayout parent, String s) {
        TextView f = Ui.text(this, "•  " + s, 12.5f, t.text, false);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = Ui.dp(8);
        parent.addView(f, fp);
    }
}
