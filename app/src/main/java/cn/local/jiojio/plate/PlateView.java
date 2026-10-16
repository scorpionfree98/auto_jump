package cn.local.jiojio.plate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

public final class PlateView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PlateGlyphs glyphs;
    private String number = "皖A12345";
    private int style = PlateSpec.BLUE;
    private float scaleFraction = 1f;
    public PlateView(Context context) { super(context); glyphs=PlateGlyphs.get(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); updateDescription(); }
    public void setPlate(String number, int style) {
        this.number = PlateSpec.normalize(number); this.style = style; updateDescription(); invalidate();
    }
    public void setScaleFraction(float value) { scaleFraction = Math.max(.5f, Math.min(1f, value)); invalidate(); }
    private void updateDescription() { setContentDescription((style == PlateSpec.GREEN ? "新能源绿牌 " : "普通蓝牌 ") + number); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = PlateSpec.width(style), h = 140;
        float factor = Math.min((getWidth()-getPaddingLeft()-getPaddingRight()) / w,
                (getHeight()-getPaddingTop()-getPaddingBottom()) / h) * scaleFraction;
        if (factor <= 0) return;
        float usableWidth=getWidth()-getPaddingLeft()-getPaddingRight();
        float usableHeight=getHeight()-getPaddingTop()-getPaddingBottom();
        c.save(); c.translate(getPaddingLeft()+(usableWidth-w*factor)/2,
                getPaddingTop()+(usableHeight-h*factor)/2); c.scale(factor,factor);
        paint.setStyle(Paint.Style.FILL);
        if (style == PlateSpec.GREEN) paint.setShader(new LinearGradient(0,0,0,h,
                new int[]{Color.rgb(240,251,231),Color.rgb(119,218,103),Color.rgb(49,188,70)},null,Shader.TileMode.CLAMP));
        else paint.setColor(Color.rgb(17,71,177));
        c.drawRoundRect(new RectF(0,0,w,h),6,6,paint); paint.setShader(null);
        int ink = style == PlateSpec.GREEN ? Color.rgb(18,29,21) : Color.WHITE;
        paint.setColor(ink); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.6f);
        c.drawRoundRect(new RectF(4,4,w-4,h-4),4,4,paint); paint.setStyle(Paint.Style.FILL);
        // GA 36-2018 figures 1 and 7 use different cell widths and spacing.
        // Outlines already contain their bearings; never crop and stretch them.
        int count = PlateSpec.length(style);
        for (int i=0; i<Math.min(number.length(),count); i++) {
            Path path=glyphs.path(number.substring(i,i+1),style);
            if(path==null)continue; // Invalid editor drafts are not font-substituted.
            c.save(); c.translate(PlateSpec.characterX(i,style),25);
            c.drawPath(path,paint); c.restore();
        }
        if(style==PlateSpec.BLUE)c.drawCircle(134,70,5,paint);
        c.restore();
    }
}
