package org.example.javakyrsach2.ui;

import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

public final class AppTheme {
    public static final String BACKGROUND = "#f0f4f8";

    private AppTheme() {
    }

    public static DropShadow cardShadow() {
        DropShadow shadow = new DropShadow();
        shadow.setRadius(18);
        shadow.setOffsetY(4);
        shadow.setColor(Color.color(0, 0, 0, 0.10));
        return shadow;
    }
}
