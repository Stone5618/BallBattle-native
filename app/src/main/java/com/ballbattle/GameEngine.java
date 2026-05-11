package com.ballbattle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游戏逻辑引擎 - 管理所有游戏对象和逻辑
 */
public class GameEngine {

    // 世界参数 - 大地图
    public static final int WORLD_WIDTH = 10000;
    public static final int WORLD_HEIGHT = 10000;
    private static final int FOOD_COUNT = 500;
    private static final int AI_COUNT = 20;
    private static final float PLAYER_INITIAL_RADIUS = 30f;
    private static final float AI_MIN_RADIUS = 20f;
    private static final float AI_MAX_RADIUS = 50f;
    private static final float AI_DIRECTION_CHANGE_INTERVAL = 2f;
    private static final float AI_FOOD_DETECT_RANGE = 400f;
    private static final float AI_DANGER_DETECT_RANGE = 350f;
    private static final float FOOD_RESPAWN_INTERVAL = 0.5f;

    // AI 颜色池
    private static final int[] AI_COLORS = {
            0xFFFF4444, 0xFF4488FF, 0xFFAA44FF, 0xFFFF8844, 0xFFFF44AA,
            0xFF44AAFF, 0xFFFFAA44, 0xFFFF6688, 0xFF88AAFF, 0xFFAAFF44,
    };

    // AI 名字池
    private static final String[] AI_NAMES = {
            "小白", "大神", "菜鸟", "王者", "青铜", "钻石", "星耀", "传说",
            "勇者", "英雄", "萌新", "老司机", "大佬", "高手", "菜鸡",
    };

    private final Random random = new Random();

    // 游戏对象
    public Ball player;
    private final List<Ball> aiBalls = new ArrayList<>();
    private final List<Food> foods = new ArrayList<>();

    // 摄像机
    public float cameraX = 0f;
    public float cameraY = 0f;

    // 游戏状态
    public boolean gameRunning = false;
    public boolean gameOver = false;
    public int totalScore = 0;

    // 结算数据
    public int killCount = 0;
    public float maxRadius = 0f;
    public int eatFoodCount = 0;

    // 食物补充计时器
    private float foodRespawnTimer = 0f;

    // 游戏时间
    private float gameTime = 0f;
    private static final float GAME_DURATION = 300f; // 5分钟一局

    // 视野缩放
    private float viewScale = 1.0f;

    // 回调接口
    public interface GameCallback {
        void onGameOver(int score, int killCount, float maxRadius, int eatFoodCount, float gameTime);
    }

    private GameCallback callback;

    // 游戏事件回调（粒子特效用）
    public interface GameEventCallback {
        void onFoodEaten(float x, float y, int color);
        void onBallEaten(float x, float y, int color, float radius);
    }
    public GameEventCallback eventCallback;

    public void setGameCallback(GameCallback callback) {
        this.callback = callback;
    }

    public void initGame() {
        aiBalls.clear();
        foods.clear();
        totalScore = 0;
        killCount = 0;
        maxRadius = 0f;
        eatFoodCount = 0;
        gameOver = false;
        gameRunning = true;
        foodRespawnTimer = 0f;
        gameTime = 0f;
        viewScale = 1.0f;

        // 创建玩家 - 随机位置
        float px = 500 + random.nextFloat() * (WORLD_WIDTH - 1000);
        float py = 500 + random.nextFloat() * (WORLD_HEIGHT - 1000);
        player = new Ball(px, py, PLAYER_INITIAL_RADIUS, 0xFF44FF44, "我");
        player.isAI = false;

        // 创建AI球
        for (int i = 0; i < AI_COUNT; i++) {
            createAIBall();
        }

        // 创建食物
        for (int i = 0; i < FOOD_COUNT; i++) {
            createFood();
        }
    }

    private void createAIBall() {
        float minR = AI_MIN_RADIUS;
        float maxR = AI_MAX_RADIUS;
        // 时间压力：3分钟后AI初始半径增大
        if (gameTime > 240f) { // 4分钟后
            minR = 40f;
            maxR = 60f;
        } else if (gameTime > 180f) { // 3分钟后
            minR = 30f;
            maxR = 50f;
        }
        float radius = minR + random.nextFloat() * (maxR - minR);
        float x = radius + random.nextFloat() * (WORLD_WIDTH - 2 * radius);
        float y = radius + random.nextFloat() * (WORLD_HEIGHT - 2 * radius);
        int color = AI_COLORS[random.nextInt(AI_COLORS.length)];
        String name = AI_NAMES[random.nextInt(AI_NAMES.length)];

        Ball ai = new Ball(x, y, radius, color, name);
        ai.isAI = true;
        ai.aiDirectionTimer = random.nextFloat() * AI_DIRECTION_CHANGE_INTERVAL;
        randomizeAIDirection(ai);

        aiBalls.add(ai);
    }

    private void randomizeAIDirection(Ball ai) {
        float angle = random.nextFloat() * (float) (2 * Math.PI);
        ai.setDirection((float) Math.cos(angle), (float) Math.sin(angle));
        ai.aiDirectionTimer = AI_DIRECTION_CHANGE_INTERVAL;
    }

    private void createFood() {
        float x = 50f + random.nextFloat() * (WORLD_WIDTH - 100f);
        float y = 50f + random.nextFloat() * (WORLD_HEIGHT - 100f);
        foods.add(new Food(x, y));
    }

    public void update(float deltaTime) {
        if (!gameRunning || gameOver) return;

        gameTime += deltaTime;

        // 更新视野缩放（根据玩家大小）
        updateViewScale();

        // 更新玩家
        player.update(deltaTime, WORLD_WIDTH, WORLD_HEIGHT);

        // 更新AI
        updateAI(deltaTime);

        // 碰撞检测
        checkPlayerEatFood();
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;
            checkBallEatFood(ai);
        }
        checkBallCollisions();

        // 检查玩家是否被吃
        if (!player.alive) {
            gameOver = true;
            gameRunning = false;
            if (callback != null) {
                callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
            }
            return;
        }

        // 补充食物
        foodRespawnTimer += deltaTime;
        if (foodRespawnTimer >= FOOD_RESPAWN_INTERVAL) {
            foodRespawnTimer = 0f;
            int aliveFoodCount = 0;
            for (Food f : foods) {
                if (f.alive) aliveFoodCount++;
            }
            while (aliveFoodCount < FOOD_COUNT) {
                createFood();
                aliveFoodCount++;
            }
        }

        // 补充AI
        int aliveAICount = 0;
        for (Ball ai : aiBalls) {
            if (ai.alive) aliveAICount++;
        }
        while (aliveAICount < AI_COUNT) {
            createAIBall();
            aliveAICount++;
        }

        // 更新摄像机
        updateCamera();

        // 更新总分
        totalScore = player.score;

        // 追踪最大体积
        if (player.radius > maxRadius) {
            maxRadius = player.radius;
        }

        // 5分钟倒计时结束
        if (gameTime >= GAME_DURATION) {
            gameOver = true;
            gameRunning = false;
            if (callback != null) {
                callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
            }
            return;
        }
    }

    private void updateViewScale() {
        // 玩家越大，视野越广（缩放越小）
        if (player != null && player.alive) {
            float baseRadius = 30f;
            float scale = (float) Math.sqrt(baseRadius / player.radius);
            // 限制缩放范围
            viewScale = Math.max(0.5f, Math.min(1.0f, scale));
        }
    }

    private void updateAI(float deltaTime) {
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;

            ai.aiDirectionTimer -= deltaTime;

            boolean hasTarget = false;

            // AI动态难度
            if (player.alive) {
                float dx = player.x - ai.x;
                float dy = player.y - ai.y;
                float distToPlayer = (float) Math.sqrt(dx * dx + dy * dy);

                if (ai.radius < 30f) {
                    // 小AI：看到玩家就跑，逃跑速度+30%
                    float detectRange = AI_DANGER_DETECT_RANGE * 1.5f;
                    if (distToPlayer < detectRange) {
                        // 逃跑方向：远离玩家
                        if (distToPlayer > 0.001f) {
                            ai.setDirection(-dx / distToPlayer, -dy / distToPlayer);
                            // 逃跑速度+30%
                            float len = (float) Math.sqrt(ai.vx * ai.vx + ai.vy * ai.vy);
                            if (len > 0.001f) {
                                ai.vx *= 1.3f;
                                ai.vy *= 1.3f;
                            }
                            hasTarget = true;
                        }
                    }
                } else if (ai.radius > 40f) {
                    // 大AI：主动追玩家，追击速度+20%
                    float chaseRange = AI_FOOD_DETECT_RANGE * 1.5f;
                    if (distToPlayer < chaseRange && ai.radius > player.radius + 5f) {
                        if (distToPlayer > 0.001f) {
                            ai.setDirection(dx / distToPlayer, dy / distToPlayer);
                            // 追击速度+20%
                            float len = (float) Math.sqrt(ai.vx * ai.vx + ai.vy * ai.vy);
                            if (len > 0.001f) {
                                ai.vx *= 1.2f;
                                ai.vy *= 1.2f;
                            }
                            hasTarget = true;
                        }
                    }
                }
                // 中等AI保持现有行为（走下面的通用逻辑）
            }

            // 检测危险（比自己大的球）
            if (!hasTarget) {
                Ball dangerBall = findNearestDanger(ai);
                if (dangerBall != null) {
                    float dx = ai.x - dangerBall.x;
                    float dy = ai.y - dangerBall.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > 0.001f) {
                        ai.setDirection(dx / dist, dy / dist);
                        hasTarget = true;
                    }
                }
            }

            // 寻找食物
            if (!hasTarget) {
                Food nearestFood = findNearestFood(ai);
                if (nearestFood != null) {
                    float dx = nearestFood.x - ai.x;
                    float dy = nearestFood.y - ai.y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > 0.001f) {
                        ai.setDirection(dx / dist, dy / dist);
                        hasTarget = true;
                    }
                }
            }

            // 随机改变方向
            if (!hasTarget && ai.aiDirectionTimer <= 0) {
                randomizeAIDirection(ai);
            }

            ai.update(deltaTime, WORLD_WIDTH, WORLD_HEIGHT);
        }
    }

    private Ball findNearestDanger(Ball ai) {
        Ball nearest = null;
        float nearestDist = AI_DANGER_DETECT_RANGE;

        if (player.alive && player.radius > ai.radius + 5f) {
            float dx = player.x - ai.x;
            float dy = player.y - ai.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        for (Ball other : aiBalls) {
            if (other == ai || !other.alive) continue;
            if (other.radius > ai.radius + 5f) {
                float dx = other.x - ai.x;
                float dy = other.y - ai.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = other;
                }
            }
        }

        return nearest;
    }

    private Food findNearestFood(Ball ai) {
        Food nearest = null;
        float nearestDist = AI_FOOD_DETECT_RANGE;

        for (Food food : foods) {
            if (!food.alive) continue;
            float dx = food.x - ai.x;
            float dy = food.y - ai.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = food;
            }
        }

        return nearest;
    }

    private void checkPlayerEatFood() {
        if (!player.alive) return;

        for (Food food : foods) {
            if (!food.alive) continue;

            float dx = player.x - food.x;
            float dy = player.y - food.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < player.radius) {
                food.alive = false;
                player.grow(food.radius * food.radius);
                player.score += (int) (food.radius * 2);
                eatFoodCount++;
                // 触发食物被吃事件
                if (eventCallback != null) {
                    eventCallback.onFoodEaten(food.x, food.y, food.color);
                }
            }
        }
    }

    private void checkBallEatFood(Ball ball) {
        for (Food food : foods) {
            if (!food.alive) continue;

            float dx = ball.x - food.x;
            float dy = ball.y - food.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if (dist < ball.radius) {
                food.alive = false;
                ball.grow(food.radius * food.radius);
                ball.score += (int) (food.radius * 2);
            }
        }
    }

    private void checkBallCollisions() {
        // 玩家 vs AI
        if (player.alive) {
            for (Ball ai : aiBalls) {
                if (!ai.alive) continue;

                if (player.canEat(ai)) {
                    ai.alive = false;
                    player.grow(ai.radius * ai.radius * 0.8f);
                    player.score += ai.score + 50;
                    killCount++;
                    // 触发球被吃事件
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(ai.x, ai.y, ai.color, ai.radius);
                    }
                } else if (ai.canEat(player)) {
                    player.alive = false;
                    ai.grow(player.radius * player.radius * 0.8f);
                    ai.score += player.score + 50;
                    // 触发球被吃事件
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(player.x, player.y, player.color, player.radius);
                    }
                }
            }
        }

        // AI vs AI
        for (int i = 0; i < aiBalls.size(); i++) {
            Ball a = aiBalls.get(i);
            if (!a.alive) continue;

            for (int j = i + 1; j < aiBalls.size(); j++) {
                Ball b = aiBalls.get(j);
                if (!b.alive) continue;

                if (a.canEat(b)) {
                    b.alive = false;
                    a.grow(b.radius * b.radius * 0.8f);
                    a.score += b.score + 50;
                } else if (b.canEat(a)) {
                    a.alive = false;
                    b.grow(a.radius * a.radius * 0.8f);
                    b.score += a.score + 50;
                }
            }
        }
    }

    private void updateCamera() {
        if (!player.alive) return;
        cameraX = player.x;
        cameraY = player.y;
    }

    public List<Ball> getAliveAIBalls() {
        List<Ball> alive = new ArrayList<>();
        for (Ball ai : aiBalls) {
            if (ai.alive) alive.add(ai);
        }
        return alive;
    }

    public List<Food> getAliveFoods() {
        List<Food> alive = new ArrayList<>();
        for (Food food : foods) {
            if (food.alive) alive.add(food);
        }
        return alive;
    }

    public void setPlayerDirection(float dirX, float dirY) {
        if (!gameRunning || !player.alive) return;
        player.setDirection(dirX, dirY);
    }

    public void stopPlayer() {
        if (player != null) {
            player.stop();
        }
    }

    public float getViewScale() {
        return viewScale;
    }

    public float getGameTime() {
        return gameTime;
    }
}
