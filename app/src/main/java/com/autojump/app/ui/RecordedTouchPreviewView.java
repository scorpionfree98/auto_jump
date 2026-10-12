package com.autojump.app.ui;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.autojump.app.domain.TouchPoint;
import java.util.List;

/** Non-interactive miniature of replay geometry, never dispatches a gesture. */
final class RecordedTouchPreviewView extends View {
    private final List<TouchPoint> original,mapped;
    private final int width,height;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    RecordedTouchPreviewView(Context context,List<TouchPoint> original,List<TouchPoint> mapped,int w,int h) {
        super(context);this.original=original;this.mapped=mapped;width=w;height=h;
        setContentDescription("原始落点与校准后落点示意图");
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale=Math.min((getWidth()-30f)/width,(getHeight()-30f)/height);
        float h=height*scale,w=width*scale,left=(getWidth()-w)/2,top=(getHeight()-h)/2;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(246,247,242));
        canvas.drawRoundRect(left,top,left+w,top+h,12,12,paint);
        drawTrace(canvas,original,left,top,w,h,Color.GRAY);
        drawTrace(canvas,mapped,left,top,w,h,Color.rgb(20,91,76));
    }
    private void drawTrace(Canvas c,List<TouchPoint> trace,float left,float top,float w,float h,int color) {
        if(trace.isEmpty())return;
        paint.setColor(color);paint.setStrokeWidth(3);paint.setStyle(Paint.Style.STROKE);
        Path path=new Path();
        for(int i=0;i<trace.size();i++) { TouchPoint p=trace.get(i);float x=left+(float)p.getRelativeX()*w,y=top+(float)p.getRelativeY()*h;
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y); }
        c.drawPath(path,paint);TouchPoint first=trace.get(0);
        float x=left+(float)first.getRelativeX()*w,y=top+(float)first.getRelativeY()*h;
        c.drawCircle(x,y,8,paint);c.drawLine(x-12,y,x+12,y,paint);c.drawLine(x,y-12,x,y+12,paint);
    }
}
