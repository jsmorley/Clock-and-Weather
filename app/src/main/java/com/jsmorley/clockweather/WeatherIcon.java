package com.jsmorley.clockweather;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

final class WeatherIcon {
    static Bitmap draw(int code, boolean isDay, int color, int size) {
        Bitmap image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.scale(size / 24f, size / 24f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        if (code == 0) {
            if (isDay) {
                canvas.drawCircle(12, 12, 4.5f, paint);
                for (int ray = 0; ray < 8; ray++) {
                    canvas.drawLine(12, 2, 12, 4.5f, paint);
                    canvas.rotate(45, 12, 12);
                }
            } else {
                Path moon = new Path();
                moon.moveTo(13, 3);
                moon.cubicTo(9, 6, 10, 15, 20, 15);
                moon.cubicTo(17, 25, 3, 22, 3, 12);
                moon.cubicTo(3, 7, 7, 3, 13, 3);
                canvas.drawPath(moon, paint);
            }
            return image;
        }

        if (code == 1 || code == 2) {
            canvas.drawArc(3, 2, 13, 12, 165, 180, false, paint);
            canvas.drawLine(8, 0.9f, 8, 1.5f, paint);
            canvas.drawLine(1.5f, 5, 2.5f, 5.5f, paint);
        }
        Path cloud = new Path();
        cloud.moveTo(6, 16);
        cloud.cubicTo(1, 16, 1, 9, 6.2f, 9.5f);
        cloud.cubicTo(7.5f, 3, 16.5f, 3, 18, 9.5f);
        cloud.cubicTo(23, 9, 23, 16, 18, 16);
        cloud.close();
        canvas.drawPath(cloud, paint);

        if (code == 45 || code == 48) {
            canvas.drawLine(4, 19, 20, 19, paint);
            canvas.drawLine(7, 22, 17, 22, paint);
        } else if (code >= 95) {
            Path bolt = new Path();
            bolt.moveTo(13, 16);
            bolt.lineTo(10, 20);
            bolt.lineTo(14, 20);
            bolt.lineTo(11, 23);
            canvas.drawPath(bolt, paint);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            for (int x = 6; x <= 18; x += 6) {
                canvas.drawLine(x - 1, 20, x + 1, 22, paint);
                canvas.drawLine(x + 1, 20, x - 1, 22, paint);
            }
        } else if (code >= 51) {
            for (int x = 7; x <= 19; x += 6) canvas.drawLine(x, 19, x - 2, 22.5f, paint);
        }
        return image;
    }
}
