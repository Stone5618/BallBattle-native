package com.ballbattle;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 球类 - 表示玩家或AI球
 */
public class Ball {

    public float x;
    public float y;
    public float radius;
    public float vx;
    public float vy;
    public int color;
    public float speed;
    public String name;
    public boolean alive = true;
    public int score = 0;

    // AI 相关
    public float aiDirectionTimer = 0f;
    public float aiTargetX;
    public float aiTargetY;
    public boolean isAI = false;

    private static final float MIN_RADIUS = 15f;
    private static final float MAX_RADIUS = 200f;

    public Ball(float x, float y, float radius, int color, String name) {
        this.x = x;
        this.y = y;
        this.radius = Math.max(MIN_RADIUS, radius);
        this.color = color;
        this.name = name;
        this.speed = 200f;
        this.vx = 0f;
        this.vy = 0f;
    }

    /**
     * 更新球的位置
     * @param deltaTime 帧间隔时间（秒）
     * @param worldWidth 世界宽度
     * @param worldHeight 世界高度
     */
    public void update(float deltaTime, float worldWidth, float worldHeight) {
        x += vx * deltaTime;
        y += vy * deltaTime;

        // 边界检测 - 限制在世界范围内
        if (x - radius < 0) {
            x = radius;
            vx = Math.abs(vx);
        }
        if (x + radius > worldWidth) {
            x = worldWidth - radius;
            vx = -Math.abs(vx);
        }
        if (y - radius < 0) {
            y = radius;
            vy = Math.abs(vy);
        }
        if (y + radius > worldHeight) {
            y = worldHeight - radius;
            vy = -Math.abs(vy);
        }
    }

    /**
     * 设置速度方向
     * @param dirX 方向X分量（归一化）
     * @param dirY 方向Y分量（归一化）
     */
    public void setDirection(float dirX, float dirY) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len > 0.001f) {
            vx = (dirX / len) * speed;
            vy = (dirY / len) * speed;
        } else {
            vx = 0f;
            vy = 0f;
        }
    }

    /**
     * 停止移动
     */
    public void stop() {
        vx = 0f;
        vy = 0f;
    }

    /**
     * 增长（吃掉食物或其他球）
     * @param amount 增长的面积
     */
    public void grow(float amount) {
        float area = radius * radius + amount;
        radius = (float) Math.sqrt(area);
        if (radius > MAX_RADIUS) {
            radius = MAX_RADIUS;
        }
        // 球越大速度越慢
        speed = Math.max(80f, 200f - (radius - 30f) * 0.5f);
    }

    /**
     * 绘制球
     * @param canvas 画布
     * @param paint 画笔
     * @param cameraX 摄像机X偏移
     * @param cameraY 摄像机Y偏移
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void draw(Canvas canvas, Paint paint, float cameraX, float cameraY,
                     int screenWidth, int screenHeight) {
        // 计算屏幕坐标
        float screenX = x - cameraX;
        float screenY = y - cameraY;

        // 视口剔除 - 不在屏幕范围内的球不绘制
        if (screenX + radius < 0 || screenX - radius > screenWidth
                || screenY + radius < 0 || screenY - radius > screenHeight) {
            return;
        }

        // 绘制球的阴影
        paint.setColor(0x33000000);
        canvas.drawCircle(screenX + 3, screenY + 3, radius, paint);

        // 绘制球体
        paint.setColor(color);
        canvas.drawCircle(screenX, screenY, radius, paint);

        // 绘制高光效果
        paint.setColor(0x40ffffff);
        float highlightRadius = radius * 0.35f;
        canvas.drawCircle(screenX - radius * 0.25f, screenY - radius * 0.25f,
                highlightRadius, paint);

        // 绘制名字
        if (name != null && !name.isEmpty()) {
            paint.setColor(0xFFFFFFFF);
            float textSize = Math.max(18f, radius * 0.5f);
            paint.setTextSize(textSize);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            canvas.drawText(name, screenX, screenY - radius * 0.1f, paint);

            // 绘制分数（在名字下方）
            paint.setTextSize(textSize * 0.7f);
            paint.setColor(0xCCFFFFFF);
            canvas.drawText(String.valueOf(score), screenX, screenY + textSize * 0.8f, paint);
            paint.setFakeBoldText(false);
        }
    }

    /**
     * 检测两个球是否碰撞（大球吃小球条件）
     * @param other 另一个球
     * @return true 如果当前球可以吃掉另一个球
     */
    public boolean canEat(Ball other) {
        if (!alive || !other.alive) return false;
        if (radius - other.radius <= 5f) return false;

        float dx = x - other.x;
        float dy = y - other.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < radius;
    }
}
