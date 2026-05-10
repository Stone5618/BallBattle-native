package com.ballbattle;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 食物类 - 散布在地图上的小圆点
 */
public class Food {

    public float x;
    public float y;
    public float radius;
    public int color;
    public boolean alive = true;

    // 食物颜色池
    private static final int[] FOOD_COLORS = {
            0xFFFF4444, // 红
            0xFF44FF44, // 绿
            0xFF4444FF, // 蓝
            0xFFFFFF44, // 黄
            0xFFFF44FF, // 粉
            0xFF44FFFF, // 青
            0xFFFF8844, // 橙
            0xFF8844FF, // 紫
            0xFF44FF88, // 薄荷
            0xFFFF4488, // 玫红
    };

    public Food(float x, float y) {
        this.x = x;
        this.y = y;
        this.radius = 5f + (float) (Math.random() * 3f); // 半径 5-8
        this.color = FOOD_COLORS[(int) (Math.random() * FOOD_COLORS.length)];
    }

    /**
     * 绘制食物
     * @param canvas 画布
     * @param paint 画笔
     * @param cameraX 摄像机X偏移
     * @param cameraY 摄像机Y偏移
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void draw(Canvas canvas, Paint paint, float cameraX, float cameraY,
                     int screenWidth, int screenHeight) {
        if (!alive) return;

        float screenX = x - cameraX;
        float screenY = y - cameraY;

        // 视口剔除
        if (screenX + radius < -10 || screenX - radius > screenWidth + 10
                || screenY + radius < -10 || screenY - radius > screenHeight + 10) {
            return;
        }

        // 绘制食物光晕
        paint.setColor((color & 0x00FFFFFF) | 0x40000000);
        canvas.drawCircle(screenX, screenY, radius * 1.5f, paint);

        // 绘制食物本体
        paint.setColor(color);
        canvas.drawCircle(screenX, screenY, radius, paint);
    }
}
