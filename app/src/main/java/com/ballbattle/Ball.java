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

    // 质量系统
    public float mass = 10f;

    // AI 相关
    public float aiDirectionTimer = 0f;
    public float aiTargetX;
    public float aiTargetY;
    public boolean isAI = false;

    // AI 平滑转向
    public float targetDirectionX = 0f;
    public float targetDirectionY = 0f;
    private static final float AI_TURN_SPEED = 5f;  // 转向速度（越大越快）

    // 分身相关
    public boolean isMainBall = true;      // 是否是主球
    public int ballIndex = 0;               // 分身索引
    public float mergeTimer = 0f;           // 合并计时器
    public static final float MERGE_TIME = 30f;  // 30秒后合并
    public float directionX = 0f;           // 当前移动方向X
    public float directionY = 0f;           // 当前移动方向Y

    // 无敌状态（生存模式重生后）
    public boolean invincible = false;
    public float invincibleTimer = 0f;

    private static final float MIN_RADIUS = 15f;
    private static final float MAX_RADIUS = 1500f;  // 对应mass=22500

    public Ball(float x, float y, float radius, int color, String name) {
        this.x = x;
        this.y = y;
        this.radius = Math.max(MIN_RADIUS, radius);
        this.color = color;
        this.name = name;
        this.mass = radius * radius / 100f;  // mass = (radius/10)^2
        this.speed = calculateSpeed();
        this.vx = 0f;
        this.vy = 0f;
    }

    /**
     * 计算速度（指数衰减）
     */
    public float calculateSpeed() {
        float baseSpeed = 250f;
        float minMass = 10f;
        float effectiveMass = Math.max(minMass, mass);
        // 指数衰减：speed = baseSpeed * 0.98^(mass/100)
        return baseSpeed * (float)Math.pow(0.98, effectiveMass / 100f);
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

        // 更新无敌计时器
        if (invincible) {
            invincibleTimer -= deltaTime;
            if (invincibleTimer <= 0) {
                invincible = false;
                invincibleTimer = 0f;
            }
        }

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
            directionX = dirX / len;
            directionY = dirY / len;
            vx = directionX * speed;
            vy = directionY * speed;
        } else {
            directionX = 0f;
            directionY = 0f;
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
     * 增长（吃掉食物或其他球）- 基于质量
     * @param massGain 获得的质量
     */
    public void grow(float massGain) {
        mass += massGain;
        radius = 10f * (float)Math.sqrt(mass);
        if (radius > MAX_RADIUS) {
            radius = MAX_RADIUS;
            mass = radius * radius / 100f;
        }
        speed = calculateSpeed();
    }

    /**
     * 质量衰减（每秒损失0.2%质量）
     * @param deltaTime 帧间隔时间（秒）
     */
    public void decayMass(float deltaTime) {
        mass *= Math.pow(0.998, deltaTime);
        if (mass < 10f) mass = 10f;  // 最小质量保护
        radius = 10f * (float)Math.sqrt(mass);
        speed = calculateSpeed();
    }

    /**
     * AI 平滑转向（仅AI使用）
     * @param deltaTime 帧间隔时间（秒）
     */
    public void updateAIDirection(float deltaTime) {
        if (!isAI) return;
        float dx = targetDirectionX - directionX;
        float dy = targetDirectionY - directionY;
        float dist = (float)Math.sqrt(dx*dx + dy*dy);
        if (dist > 0.01f) {
            float step = AI_TURN_SPEED * deltaTime;
            if (step > dist) step = dist;
            directionX += (dx / dist) * step;
            directionY += (dy / dist) * step;
            // 重新归一化
            float len = (float)Math.sqrt(directionX*directionX + directionY*directionY);
            if (len > 0.001f) {
                directionX /= len;
                directionY /= len;
            }
            vx = directionX * speed;
            vy = directionY * speed;
        }
    }

    /**
     * 更新合并计时器
     * @param deltaTime 帧间隔时间（秒）
     */
    public void updateMerge(float deltaTime) {
        if (!isMainBall) {
            mergeTimer += deltaTime;
        }
    }

    /**
     * 检查是否应该合并
     * @return true 如果分身应该合并
     */
    public boolean shouldMerge() {
        return !isMainBall && mergeTimer >= MERGE_TIME;
    }

    /**
     * 与另一个球合并
     * @param other 被合并的球
     */
    public void mergeWith(Ball other) {
        if (other == null) return;
        mass += other.mass;
        radius = 10f * (float)Math.sqrt(mass);
        if (radius > MAX_RADIUS) {
            radius = MAX_RADIUS;
            mass = radius * radius / 100f;
        }
        this.score += other.score;
        speed = calculateSpeed();
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
     * 检测两个球是否碰撞（基于质量判断）
     * @param other 另一个球
     * @return true 如果当前球可以吃掉另一个球
     */
    public boolean canEat(Ball other) {
        if (!other.alive) return false;
        // 需要比对方大25%质量才能吃
        if (mass < other.mass * 1.25f) return false;
        // 距离检测
        float dx = x - other.x;
        float dy = y - other.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist < radius;
    }
}
