package com.ballbattle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游戏逻辑引擎 - 管理所有游戏对象和逻辑
 */
public class GameEngine {

    // 世界参数
    public static final int WORLD_WIDTH = 3000;
    public static final int WORLD_HEIGHT = 3000;
    private static final int FOOD_COUNT = 150;
    private static final int AI_COUNT = 10;
    private static final float PLAYER_INITIAL_RADIUS = 30f;
    private static final float AI_MIN_RADIUS = 20f;
    private static final float AI_MAX_RADIUS = 50f;
    private static final float AI_DIRECTION_CHANGE_INTERVAL = 2f;
    private static final float AI_FOOD_DETECT_RANGE = 300f;
    private static final float AI_DANGER_DETECT_RANGE = 250f;
    private static final float FOOD_RESPAWN_INTERVAL = 0.5f; // 每0.5秒检查是否需要补充食物

    // AI 颜色池
    private static final int[] AI_COLORS = {
            0xFFFF4444, // 红
            0xFF4488FF, // 蓝
            0xFFAA44FF, // 紫
            0xFFFF8844, // 橙
            0xFFFF44AA, // 粉
            0xFF44AAFF, // 天蓝
            0xFFFFAA44, // 金
            0xFFFF6688, // 玫红
            0xFF88AAFF, // 淡蓝
            0xFFAAFF44, // 黄绿
    };

    // AI 名字池
    private static final String[] AI_NAMES = {
            "小白", "大神", "菜鸟", "王者", "青铜",
            "钻石", "星耀", "传说", "勇者", "英雄",
            "萌新", "老司机", "大佬", "高手", "菜鸡",
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

    // 食物补充计时器
    private float foodRespawnTimer = 0f;

    // 回调接口
    public interface GameCallback {
        void onGameOver(int score);
    }

    private GameCallback callback;

    public void setGameCallback(GameCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化游戏
     */
    public void initGame() {
        aiBalls.clear();
        foods.clear();
        totalScore = 0;
        gameOver = false;
        gameRunning = true;
        foodRespawnTimer = 0f;

        // 创建玩家 - 放在世界中心
        player = new Ball(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f,
                PLAYER_INITIAL_RADIUS, 0xFF44FF44, "我");
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

    /**
     * 创建一个AI球
     */
    private void createAIBall() {
        float radius = AI_MIN_RADIUS + random.nextFloat() * (AI_MAX_RADIUS - AI_MIN_RADIUS);
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

    /**
     * 随机设置AI方向
     */
    private void randomizeAIDirection(Ball ai) {
        float angle = random.nextFloat() * (float) (2 * Math.PI);
        ai.setDirection((float) Math.cos(angle), (float) Math.sin(angle));
        ai.aiDirectionTimer = AI_DIRECTION_CHANGE_INTERVAL;
    }

    /**
     * 创建一个食物
     */
    private void createFood() {
        float x = 20f + random.nextFloat() * (WORLD_WIDTH - 40f);
        float y = 20f + random.nextFloat() * (WORLD_HEIGHT - 40f);
        foods.add(new Food(x, y));
    }

    /**
     * 更新游戏逻辑
     * @param deltaTime 帧间隔时间（秒）
     */
    public void update(float deltaTime) {
        if (!gameRunning || gameOver) return;

        // 更新玩家
        player.update(deltaTime, WORLD_WIDTH, WORLD_HEIGHT);

        // 更新AI
        updateAI(deltaTime);

        // 碰撞检测 - 玩家吃食物
        checkPlayerEatFood();

        // 碰撞检测 - AI吃食物
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;
            checkBallEatFood(ai);
        }

        // 碰撞检测 - 球吃球
        checkBallCollisions();

        // 检查玩家是否被吃
        if (!player.alive) {
            gameOver = true;
            gameRunning = false;
            if (callback != null) {
                callback.onGameOver(totalScore);
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

        // 更新摄像机 - 跟随玩家
        updateCamera();

        // 更新总分
        totalScore = player.score;
    }

    /**
     * 更新AI行为
     */
    private void updateAI(float deltaTime) {
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;

            // 方向改变计时器
            ai.aiDirectionTimer -= deltaTime;

            boolean hasTarget = false;

            // 检测附近危险（比自己大的球）
            Ball dangerBall = findNearestDanger(ai);
            if (dangerBall != null) {
                // 逃跑 - 反方向
                float dx = ai.x - dangerBall.x;
                float dy = ai.y - dangerBall.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > 0.001f) {
                    ai.setDirection(dx / dist, dy / dist);
                    hasTarget = true;
                }
            }

            // 如果没有危险，寻找食物
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

            // 如果没有目标，定时随机改变方向
            if (!hasTarget && ai.aiDirectionTimer <= 0) {
                randomizeAIDirection(ai);
            }

            // 更新位置
            ai.update(deltaTime, WORLD_WIDTH, WORLD_HEIGHT);
        }
    }

    /**
     * 寻找附近最近的危险球
     */
    private Ball findNearestDanger(Ball ai) {
        Ball nearest = null;
        float nearestDist = AI_DANGER_DETECT_RANGE;

        // 检查玩家
        if (player.alive && player.radius > ai.radius + 5f) {
            float dx = player.x - ai.x;
            float dy = player.y - ai.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }

        // 检查其他AI
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

    /**
     * 寻找附近最近的食物
     */
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

    /**
     * 检测玩家吃食物
     */
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
            }
        }
    }

    /**
     * 检测球吃食物
     */
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

    /**
     * 检测球与球之间的碰撞
     */
    private void checkBallCollisions() {
        // 玩家 vs AI
        if (player.alive) {
            for (Ball ai : aiBalls) {
                if (!ai.alive) continue;

                // 玩家吃AI
                if (player.canEat(ai)) {
                    ai.alive = false;
                    player.grow(ai.radius * ai.radius * 0.8f);
                    player.score += ai.score + 50;
                }
                // AI吃玩家
                else if (ai.canEat(player)) {
                    player.alive = false;
                    ai.grow(player.radius * player.radius * 0.8f);
                    ai.score += player.score + 50;
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

    /**
     * 更新摄像机位置 - 跟随玩家
     */
    private void updateCamera() {
        if (!player.alive) return;

        // 摄像机中心对准玩家
        cameraX = player.x;
        cameraY = player.y;
    }

    /**
     * 获取所有存活的AI球
     */
    public List<Ball> getAliveAIBalls() {
        List<Ball> alive = new ArrayList<>();
        for (Ball ai : aiBalls) {
            if (ai.alive) alive.add(ai);
        }
        return alive;
    }

    /**
     * 获取所有存活的食物
     */
    public List<Food> getAliveFoods() {
        List<Food> alive = new ArrayList<>();
        for (Food food : foods) {
            if (food.alive) alive.add(food);
        }
        return alive;
    }

    /**
     * 设置玩家移动方向（由触摸事件调用）
     * @param touchX 触摸点X坐标（屏幕坐标）
     * @param touchY 触摸点Y坐标（屏幕坐标）
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void setPlayerDirection(float touchX, float touchY,
                                   int screenWidth, int screenHeight) {
        if (!gameRunning || !player.alive) return;

        // 计算触摸点相对于屏幕中心的方向
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float dirX = touchX - centerX;
        float dirY = touchY - centerY;

        player.setDirection(dirX, dirY);
    }

    /**
     * 停止玩家移动
     */
    public void stopPlayer() {
        if (player != null) {
            player.stop();
        }
    }
}
