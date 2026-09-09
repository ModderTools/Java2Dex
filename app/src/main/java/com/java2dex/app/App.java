package com.java2dex.app;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Ui.init(this);
    }
}
