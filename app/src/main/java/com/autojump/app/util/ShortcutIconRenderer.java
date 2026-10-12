package com.autojump.app.util;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import androidx.core.graphics.drawable.IconCompat;
import com.autojump.app.domain.ShortcutEmoji;

/** Renders a selected emoji into a launcher-safe bitmap icon. */
final class ShortcutIconRenderer {
    private static final int[] BACKGROUNDS = {
            Color.rgb(61, 90, 254), Color.rgb(0, 137, 123), Color.rgb(245, 124, 0),
            Color.rgb(216, 27, 96), Color.rgb(94, 53, 177), Color.rgb(0, 121, 107)
    };

    private ShortcutIconRenderer() {}

    static IconCompat render(Context context, String selectedEmoji) {
        String emoji = ShortcutEmoji.normalize(selectedEmoji);
        int size = preferredSize(context);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(BACKGROUNDS[Math.floorMod(emoji.hashCode(), BACKGROUNDS.length)]);
        float inset = size * 0.06f;
        canvas.drawRoundRect(new RectF(inset, inset, size - inset, size - inset),
                size * 0.24f, size * 0.24f, background);

        Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        glyph.setTextAlign(Paint.Align.CENTER);
        glyph.setTextSize(size * 0.54f);
        Paint.FontMetrics metrics = glyph.getFontMetrics();
        float baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(emoji, size / 2f, baseline, glyph);
        return IconCompat.createWithBitmap(bitmap);
    }

    private static int preferredSize(Context context) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return 128;
        return Math.max(96, Math.min(256,
                Math.max(manager.getIconMaxWidth(), manager.getIconMaxHeight())));
    }
}
