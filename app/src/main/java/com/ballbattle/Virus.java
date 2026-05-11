package com.ballbattle;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * 刺球（病毒）类 - 绿色带尖刺的障碍物
 * 大球触碰会被强制分裂，小球可以穿过
 */
public class Virus {
    public float x, y;
    public float radius = 40f;
    public boolean alive = true;
    public float respawnTimer = 0f;
    public static final float RESPAWN_TIME = 60f;

    // 尖刺数量
    public static final int SPIKE_COUNT = 12;
    public static final float SPIKE_LENGTH = 15f;

    public Virus(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void draw(Canvas canvas, Paint paint,
                     float cameraX, float cameraY, int viewWidth, int viewHeight) {
        if (!alive) return;

        float screenX = x - cameraX;
        float screenY = y - cameraY;

        // 视口剔除
        if (screenX < -radius * 2 || screenX > viewWidth + radius * 2 ||
            screenY < -radius * 2 || screenY > viewHeight + radius * 2) return;

        // 绘制绿色主体
        paint.setColor(0xFF22AA22);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(screenX, screenY, radius, paint);

        // 绘制尖刺
        paint.setColor(0xFF44DD44);
        for (int i = 0; i < SPIKE_COUNT; i++) {
            float angle = (float)(i * 2 * Math.PI / SPIKE_COUNT);
            float outerX = screenX + (float)Math.cos(angle) * (radius + SPIKE_LENGTH);
            float outerY = screenY + (float)Math.sin(angle) * (radius + SPIKE_LENGTH);

            // 绘制三角形尖刺
            float leftAngle = angle - 0.15f;
            float rightAngle = angle + 0.15f;
            float leftX = screenX + (float)Math.cos(leftAngle) * radius;
            float leftY = screenY + (float)Math.sin(leftAngle) * radius;
            float rightX = screenX + (float)Math.cos(rightAngle) * radius;
            float rightY = screenY + (float)Math.sin(rightAngle) * radius;

            paint.setStyle(Paint.Style.FILL);
            Path path = new Path();
            path.moveTo(leftX, leftY);
            path.lineTo(outerX, outerY);
            path.lineTo(rightX, rightY);
            path.close();
            canvas.drawPath(path, paint);
        }

        // 绘制高光
        paint.setColor(0x40FFFFFF);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(screenX - radius * 0.25f, screenY - radius * 0.25f, radius * 0.3f, paint);
    }
}
