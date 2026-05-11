package com.ballbattle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 游戏逻辑引擎 - 管理所有游戏对象和逻辑
 */
public class GameEngine {

    // 游戏模式枚举
    public enum GameMode {
        FREE,           // 自由模式
        SURVIVAL,       // 生存模式
        BATTLE_ROYALE   // 大逃杀模式
    }

    public GameMode currentMode = GameMode.FREE;

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
    private static final int VIRUS_COUNT = 30;

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
    public final List<Ball> playerBalls = new ArrayList<>();  // 玩家所有球（包括分身）
    private final List<Ball> aiBalls = new ArrayList<>();
    private final List<Food> foods = new ArrayList<>();
    private final List<Virus> viruses = new ArrayList<>();

    // 分身相关常量
    private static final int MAX_SPLIT_COUNT = 16;
    private static final float SPLIT_COOLDOWN = 0.5f;
    private float splitCooldownTimer = 0f;

    // 吐球相关常量
    private static final float SPIT_RADIUS = 5f;
    private static final float SPIT_SPEED = 400f;
    private float spitCooldownTimer = 0f;
    private static final float SPIT_COOLDOWN = 0.1f;

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

    // 生存模式
    public int lives = 3;
    private static final int MAX_LIVES = 3;
    private float survivalScore = 0f;  // 存活时间得分

    // 大逃杀模式 - 安全区
    public float safeZoneCenterX = WORLD_WIDTH / 2f;
    public float safeZoneCenterY = WORLD_HEIGHT / 2f;
    public float safeZoneRadius = 4500f;  // 初始安全区半径
    public float targetSafeZoneRadius = 4500f;
    private static final float SAFE_ZONE_SHRINK_INTERVAL = 30f;  // 每30秒缩小
    public float safeZoneTimer = 0f;
    private static final float SAFE_ZONE_SHRINK_RATIO = 0.85f;  // 每次缩小15%
    private static final float MIN_SAFE_ZONE_RADIUS = 200f;
    public float safeZoneDamage = 5f;  // 每秒伤害（扣体积）

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

    // 皮肤系统
    public SkinSystem skinSystem;

    public void setSkinSystem(SkinSystem ss) {
        this.skinSystem = ss;
    }

    public void setGameCallback(GameCallback callback) {
        this.callback = callback;
    }

    public void initGame() {
        initGame(GameMode.FREE);
    }

    public void initGame(GameMode mode) {
        this.currentMode = mode;
        aiBalls.clear();
        foods.clear();
        playerBalls.clear();
        viruses.clear();
        totalScore = 0;
        killCount = 0;
        maxRadius = 0f;
        eatFoodCount = 0;
        gameOver = false;
        gameRunning = true;
        foodRespawnTimer = 0f;
        gameTime = 0f;
        viewScale = 1.0f;
        splitCooldownTimer = 0f;
        spitCooldownTimer = 0f;

        // 生存模式初始化
        if (mode == GameMode.SURVIVAL) {
            lives = MAX_LIVES;
            survivalScore = 0f;
        }

        // 大逃杀模式初始化
        if (mode == GameMode.BATTLE_ROYALE) {
            safeZoneCenterX = WORLD_WIDTH / 2f;
            safeZoneCenterY = WORLD_HEIGHT / 2f;
            safeZoneRadius = 4500f;
            targetSafeZoneRadius = 4500f;
            safeZoneTimer = 0f;
        }

        // 创建玩家 - 随机位置
        float px = 500 + random.nextFloat() * (WORLD_WIDTH - 1000);
        float py = 500 + random.nextFloat() * (WORLD_HEIGHT - 1000);
        int playerColor = (skinSystem != null) ? skinSystem.getSelectedColor() : 0xFF44FF44;
        player = new Ball(px, py, PLAYER_INITIAL_RADIUS, playerColor, "我");
        player.isAI = false;
        player.isMainBall = true;
        player.ballIndex = 0;
        playerBalls.add(player);

        // 创建AI球
        for (int i = 0; i < AI_COUNT; i++) {
            createAIBall();
        }

        // 创建食物
        for (int i = 0; i < FOOD_COUNT; i++) {
            createFood();
        }

        // 创建刺球
        for (int i = 0; i < VIRUS_COUNT; i++) {
            float vx = 200f + random.nextFloat() * (WORLD_WIDTH - 400f);
            float vy = 200f + random.nextFloat() * (WORLD_HEIGHT - 400f);
            viruses.add(new Virus(vx, vy));
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

        // 更新冷却计时器
        splitCooldownTimer = Math.max(0, splitCooldownTimer - deltaTime);
        spitCooldownTimer = Math.max(0, spitCooldownTimer - deltaTime);

        // 更新视野缩放（根据玩家大小）
        updateViewScale();

        // 更新所有玩家球（包括分身）
        for (Ball b : playerBalls) {
            if (b.alive) {
                b.update(deltaTime, WORLD_WIDTH, WORLD_HEIGHT);
                b.updateMerge(deltaTime);
            }
        }

        // 更新AI
        updateAI(deltaTime);

        // 更新吐出球的位置
        updateSpitBalls(deltaTime);

        // 碰撞检测
        checkPlayerEatFood();
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;
            checkBallEatFood(ai);
        }
        checkBallCollisions();

        // 刺球碰撞检测
        checkVirusCollisions();

        // 更新刺球重生
        for (Virus v : viruses) {
            if (!v.alive) {
                v.respawnTimer += deltaTime;
                if (v.respawnTimer >= Virus.RESPAWN_TIME) {
                    v.alive = true;
                    v.respawnTimer = 0f;
                    v.x = 200f + random.nextFloat() * (WORLD_WIDTH - 400f);
                    v.y = 200f + random.nextFloat() * (WORLD_HEIGHT - 400f);
                }
            }
        }

        // 检查分身合并
        checkMerge();

        // 检查玩家是否被吃（所有分身都死亡）
        boolean anyAlive = false;
        for (Ball b : playerBalls) {
            if (b.alive) {
                anyAlive = true;
                break;
            }
        }
        if (!anyAlive) {
            if (currentMode == GameMode.SURVIVAL) {
                lives--;
                if (lives <= 0) {
                    // 所有命用完，游戏结束
                    gameOver = true;
                    gameRunning = false;
                    if (callback != null) {
                        callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
                    }
                    return;
                } else {
                    // 重生玩家
                    respawnPlayer();
                }
            } else {
                // 自由模式/大逃杀：直接死亡
                gameOver = true;
                gameRunning = false;
                if (callback != null) {
                    callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
                }
                return;
            }
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

        // 更新总分（所有分身分数之和）
        totalScore = 0;
        for (Ball b : playerBalls) {
            if (b.alive) totalScore += b.score;
        }

        // 追踪最大体积
        float currentMaxRadius = 0f;
        for (Ball b : playerBalls) {
            if (b.alive && b.radius > currentMaxRadius) {
                currentMaxRadius = b.radius;
            }
        }
        if (currentMaxRadius > maxRadius) {
            maxRadius = currentMaxRadius;
        }

        // 5分钟倒计时结束（仅自由模式）
        if (currentMode == GameMode.FREE && gameTime >= GAME_DURATION) {
            gameOver = true;
            gameRunning = false;
            if (callback != null) {
                callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
            }
            return;
        }

        // 大逃杀模式 - 安全区逻辑
        if (currentMode == GameMode.BATTLE_ROYALE) {
            updateSafeZone(deltaTime);
        }
    }

    private void updateViewScale() {
        // 玩家越大，视野越广（缩放越小）
        // 计算所有玩家球的最大半径
        float maxPlayerRadius = 0f;
        for (Ball b : playerBalls) {
            if (b.alive && b.radius > maxPlayerRadius) {
                maxPlayerRadius = b.radius;
            }
        }
        if (maxPlayerRadius > 0) {
            float baseRadius = 30f;
            float scale = (float) Math.sqrt(baseRadius / maxPlayerRadius);
            // 限制缩放范围
            viewScale = Math.max(0.5f, Math.min(1.0f, scale));
        }
    }

    private void updateAI(float deltaTime) {
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;

            ai.aiDirectionTimer -= deltaTime;

            boolean hasTarget = false;

            // AI动态难度 - 找最近的玩家球
            Ball nearestPlayerBall = null;
            float nearestPlayerDist = Float.MAX_VALUE;
            for (Ball pb : playerBalls) {
                if (!pb.alive) continue;
                float dx = pb.x - ai.x;
                float dy = pb.y - ai.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < nearestPlayerDist) {
                    nearestPlayerDist = dist;
                    nearestPlayerBall = pb;
                }
            }

            if (nearestPlayerBall != null) {
                float dx = nearestPlayerBall.x - ai.x;
                float dy = nearestPlayerBall.y - ai.y;
                float distToPlayer = nearestPlayerDist;

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
                    if (distToPlayer < chaseRange && ai.radius > nearestPlayerBall.radius + 5f) {
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
                // AI躲避刺球
                if (isNearVirus(ai)) {
                    // 找到最近的刺球并反方向逃跑
                    Virus nearestVirus = null;
                    float nearestVirusDist = Float.MAX_VALUE;
                    for (Virus v : viruses) {
                        if (!v.alive || ai.radius <= v.radius) continue;
                        float dx = ai.x - v.x;
                        float dy = ai.y - v.y;
                        float dist = (float)Math.sqrt(dx*dx + dy*dy);
                        if (dist < nearestVirusDist) {
                            nearestVirusDist = dist;
                            nearestVirus = v;
                        }
                    }
                    if (nearestVirus != null && nearestVirusDist > 0.001f) {
                        float dx = ai.x - nearestVirus.x;
                        float dy = ai.y - nearestVirus.y;
                        float dist = (float)Math.sqrt(dx*dx + dy*dy);
                        ai.setDirection(dx / dist, dy / dist);
                        hasTarget = true;
                    }
                }
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

        // 检查所有玩家球
        for (Ball pb : playerBalls) {
            if (!pb.alive || pb.radius <= ai.radius + 5f) continue;
            float dx = pb.x - ai.x;
            float dy = pb.y - ai.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pb;
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
        for (Ball playerBall : playerBalls) {
            if (!playerBall.alive) continue;

            for (Food food : foods) {
                if (!food.alive) continue;

                float dx = playerBall.x - food.x;
                float dy = playerBall.y - food.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if (dist < playerBall.radius) {
                    food.alive = false;
                    playerBall.grow(food.radius * food.radius);
                    playerBall.score += (int) (food.radius * 2);
                    eatFoodCount++;
                    // 触发食物被吃事件
                    if (eventCallback != null) {
                        eventCallback.onFoodEaten(food.x, food.y, food.color);
                    }
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
        // 玩家球（包括分身） vs AI
        for (Ball playerBall : playerBalls) {
            if (!playerBall.alive) continue;
            
            for (Ball ai : aiBalls) {
                if (!ai.alive) continue;

                if (playerBall.canEat(ai)) {
                    ai.alive = false;
                    playerBall.grow(ai.radius * ai.radius * 0.8f);
                    playerBall.score += ai.score + 50;
                    killCount++;
                    // 触发球被吃事件
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(ai.x, ai.y, ai.color, ai.radius);
                    }
                } else if (ai.canEat(playerBall)) {
                    // 无敌状态下不会被吃
                    if (playerBall.invincible) continue;
                    playerBall.alive = false;
                    ai.grow(playerBall.radius * playerBall.radius * 0.8f);
                    ai.score += playerBall.score + 50;
                    // 触发球被吃事件
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(playerBall.x, playerBall.y, playerBall.color, playerBall.radius);
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

    /**
     * 刺球碰撞检测
     */
    private void checkVirusCollisions() {
        // 玩家球 vs 刺球
        for (Ball pb : playerBalls) {
            if (!pb.alive) continue;
            for (Virus v : viruses) {
                if (!v.alive) continue;
                float dx = pb.x - v.x;
                float dy = pb.y - v.y;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);

                if (pb.radius > v.radius && dist < pb.radius) {
                    // 大球触碰刺球 -> 强制分裂
                    v.alive = false;
                    v.respawnTimer = 0f;
                    // 玩家分裂成多个小球
                    forceSplit(pb);
                    // 产生粒子效果
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(v.x, v.y, 0xFF22AA22, v.radius);
                    }
                }
                // 小球可以穿过刺球（不做处理）
            }
        }

        // AI球 vs 刺球
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;
            for (Virus v : viruses) {
                if (!v.alive) continue;
                float dx = ai.x - v.x;
                float dy = ai.y - v.y;
                float dist = (float)Math.sqrt(dx*dx + dy*dy);

                if (ai.radius > v.radius && dist < ai.radius) {
                    v.alive = false;
                    v.respawnTimer = 0f;
                    // AI被刺球分裂后死亡（简化处理）
                    ai.alive = false;
                    if (eventCallback != null) {
                        eventCallback.onBallEaten(v.x, v.y, 0xFF22AA22, v.radius);
                    }
                }
            }
        }
    }

    /**
     * 强制分裂方法（被刺球触发）
     */
    private void forceSplit(Ball ball) {
        int splitCount = Math.min(4, MAX_SPLIT_COUNT - playerBalls.size());
        if (splitCount <= 0) {
            // 没有分身名额，直接缩小
            ball.radius = (float)(ball.radius / Math.sqrt(2));
            return;
        }

        float newRadius = (float)(ball.radius / Math.sqrt(splitCount + 1));
        ball.radius = newRadius;

        for (int i = 0; i < splitCount; i++) {
            Ball newBall = new Ball(ball.x, ball.y, newRadius, ball.color, ball.name);
            newBall.isMainBall = false;
            newBall.ballIndex = playerBalls.size();
            newBall.mergeTimer = 0f;
            newBall.isAI = false;
            newBall.score = ball.score / (splitCount + 1);

            float angle = (float)(i * 2 * Math.PI / splitCount);
            float speed = 350f;
            newBall.vx = (float)Math.cos(angle) * speed;
            newBall.vy = (float)Math.sin(angle) * speed;
            newBall.directionX = (float)Math.cos(angle);
            newBall.directionY = (float)Math.sin(angle);

            playerBalls.add(newBall);
        }
        ball.score = ball.score / (splitCount + 1);
    }

    /**
     * 检查AI是否靠近刺球
     */
    private boolean isNearVirus(Ball ai) {
        for (Virus v : viruses) {
            if (!v.alive) continue;
            float dx = ai.x - v.x;
            float dy = ai.y - v.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist < ai.radius + v.radius + 50f && ai.radius > v.radius) {
                return true;
            }
        }
        return false;
    }

    private void updateCamera() {
        // 计算所有玩家球的中心点
        float totalX = 0f;
        float totalY = 0f;
        int count = 0;
        for (Ball b : playerBalls) {
            if (b.alive) {
                totalX += b.x;
                totalY += b.y;
                count++;
            }
        }
        if (count > 0) {
            cameraX = totalX / count;
            cameraY = totalY / count;
        }
    }

    public List<Ball> getAliveAIBalls() {
        List<Ball> alive = new ArrayList<>();
        for (Ball ai : aiBalls) {
            if (ai.alive) alive.add(ai);
        }
        return alive;
    }

    public List<Ball> getAlivePlayerBalls() {
        List<Ball> alive = new ArrayList<>();
        for (Ball b : playerBalls) {
            if (b.alive) alive.add(b);
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

    public List<Virus> getAliveViruses() {
        List<Virus> alive = new ArrayList<>();
        for (Virus v : viruses) {
            if (v.alive) alive.add(v);
        }
        return alive;
    }

    public void setPlayerDirection(float dirX, float dirY) {
        if (!gameRunning) return;
        // 所有分身都朝同一方向移动
        for (Ball b : playerBalls) {
            if (b.alive) {
                b.setDirection(dirX, dirY);
            }
        }
    }

    public void stopPlayer() {
        for (Ball b : playerBalls) {
            if (b.alive) {
                b.stop();
            }
        }
    }

    /**
     * 检查是否可以分裂
     */
    public boolean canSplit() {
        if (splitCooldownTimer > 0) return false;
        if (playerBalls.size() >= MAX_SPLIT_COUNT) return false;
        // 检查是否有足够大的球可以分裂
        for (Ball b : playerBalls) {
            if (b.alive && b.radius >= 30f) return true;
        }
        return false;
    }

    /**
     * 执行分裂
     */
    public void split() {
        if (!canSplit()) return;

        splitCooldownTimer = SPLIT_COOLDOWN;
        List<Ball> newBalls = new ArrayList<>();

        for (Ball b : playerBalls) {
            if (!b.alive || b.radius < 30f) continue;
            if (playerBalls.size() + newBalls.size() >= MAX_SPLIT_COUNT) break;

            // 分裂
            float newRadius = (float)(b.radius / Math.sqrt(2));
            b.radius = newRadius;

            Ball newBall = new Ball(b.x, b.y, newRadius, b.color, b.name);
            newBall.isMainBall = false;
            newBall.ballIndex = playerBalls.size() + newBalls.size();
            newBall.mergeTimer = 0f;
            newBall.isAI = false;
            newBall.score = b.score / 2;
            b.score = b.score / 2;

            // 分裂方向：当前移动方向
            float speed = 300f;
            newBall.vx = b.directionX * speed;
            newBall.vy = b.directionY * speed;
            newBall.directionX = b.directionX;
            newBall.directionY = b.directionY;

            newBalls.add(newBall);
        }

        playerBalls.addAll(newBalls);
    }

    /**
     * 检查是否可以吐球
     */
    public boolean canSpit() {
        if (spitCooldownTimer > 0) return false;
        // 检查主球是否有足够体积
        if (player != null && player.alive && player.radius > SPIT_RADIUS * 2) {
            return true;
        }
        return false;
    }

    /**
     * 执行吐球
     */
    public void spit() {
        if (!canSpit()) return;

        spitCooldownTimer = SPIT_COOLDOWN;

        // 主球吐球
        if (player != null && player.alive && player.radius > SPIT_RADIUS * 2) {
            // 减少体积
            float oldArea = (float)(Math.PI * player.radius * player.radius);
            float spitArea = (float)(Math.PI * SPIT_RADIUS * SPIT_RADIUS);
            float newArea = oldArea - spitArea;
            if (newArea > 0) {
                player.radius = (float)Math.sqrt(newArea / Math.PI);

                // 创建吐出的球（作为特殊食物）
                float spitX = player.x + player.directionX * (player.radius + SPIT_RADIUS + 5);
                float spitY = player.y + player.directionY * (player.radius + SPIT_RADIUS + 5);

                Food spitFood = new Food(spitX, spitY);
                spitFood.radius = SPIT_RADIUS;
                spitFood.color = player.color;
                spitFood.isSpitBall = true;
                spitFood.vx = player.directionX * SPIT_SPEED;
                spitFood.vy = player.directionY * SPIT_SPEED;
                spitFood.spitLifeTime = 2f;  // 2秒后变成普通食物

                foods.add(spitFood);
            }
        }
    }

    /**
     * 更新吐出球的位置
     */
    private void updateSpitBalls(float deltaTime) {
        for (Food f : foods) {
            if (f.isSpitBall && f.spitLifeTime > 0) {
                f.x += f.vx * deltaTime;
                f.y += f.vy * deltaTime;
                f.vx *= 0.95f;  // 减速
                f.vy *= 0.95f;
                f.spitLifeTime -= deltaTime;
                if (f.spitLifeTime <= 0) {
                    f.isSpitBall = false;
                    f.vx = 0;
                    f.vy = 0;
                }
            }
        }
    }

    /**
     * 检查分身合并
     */
    private void checkMerge() {
        for (int i = playerBalls.size() - 1; i >= 0; i--) {
            Ball b = playerBalls.get(i);
            if (b.shouldMerge() && !b.isMainBall) {
                // 找到最近的主球或其他分身合并
                Ball target = findNearestPlayerBall(b);
                if (target != null) {
                    target.mergeWith(b);
                    b.alive = false;
                    playerBalls.remove(i);
                }
            }
        }
    }

    /**
     * 找到最近的玩家球
     */
    private Ball findNearestPlayerBall(Ball source) {
        Ball nearest = null;
        float minDist = Float.MAX_VALUE;
        for (Ball b : playerBalls) {
            if (b == source || !b.alive) continue;
            float dx = b.x - source.x;
            float dy = b.y - source.y;
            float dist = (float)Math.sqrt(dx*dx + dy*dy);
            if (dist < minDist) {
                minDist = dist;
                nearest = b;
            }
        }
        return nearest;
    }

    /**
     * 获取排行榜数据
     */
    public List<RankEntry> getLeaderboard() {
        List<RankEntry> entries = new ArrayList<>();

        // 添加玩家（所有分身半径之和）
        float playerTotalRadius = 0;
        for (Ball b : playerBalls) {
            if (b.alive) playerTotalRadius += b.radius;
        }
        if (playerTotalRadius > 0) {
            entries.add(new RankEntry("我", playerTotalRadius, true));
        }

        // 添加AI
        for (Ball ai : aiBalls) {
            if (ai.alive) {
                entries.add(new RankEntry(ai.name, ai.radius, false));
            }
        }

        // 按半径排序
        entries.sort((a, b) -> Float.compare(b.radius, a.radius));

        // 只返回前10
        return entries.subList(0, Math.min(10, entries.size()));
    }

    /**
     * 获取玩家排名
     */
    public int getPlayerRank() {
        List<RankEntry> board = getLeaderboard();
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).isPlayer) return i + 1;
        }
        return -1;
    }

    /**
     * 排行榜条目类
     */
    public static class RankEntry {
        public String name;
        public float radius;
        public boolean isPlayer;

        public RankEntry(String name, float radius, boolean isPlayer) {
            this.name = name;
            this.radius = radius;
            this.isPlayer = isPlayer;
        }
    }

    public float getViewScale() {
        return viewScale;
    }

    public float getGameTime() {
        return gameTime;
    }

    /**
     * 重生玩家（生存模式）
     */
    private void respawnPlayer() {
        float px = 500 + random.nextFloat() * (WORLD_WIDTH - 1000);
        float py = 500 + random.nextFloat() * (WORLD_HEIGHT - 1000);

        playerBalls.clear();
        int respawnColor = (skinSystem != null) ? skinSystem.getSelectedColor() : 0xFF44FF44;
        player = new Ball(px, py, PLAYER_INITIAL_RADIUS, respawnColor, "我");
        player.isMainBall = true;
        player.alive = true;
        player.isAI = false;
        player.ballIndex = 0;
        playerBalls.add(player);

        // 重生无敌时间（3秒）
        player.invincible = true;
        player.invincibleTimer = 3f;
    }

    /**
     * 更新大逃杀安全区
     */
    private void updateSafeZone(float deltaTime) {
        // 缩圈计时
        safeZoneTimer += deltaTime;
        if (safeZoneTimer >= SAFE_ZONE_SHRINK_INTERVAL) {
            safeZoneTimer = 0f;
            targetSafeZoneRadius *= SAFE_ZONE_SHRINK_RATIO;
            if (targetSafeZoneRadius < MIN_SAFE_ZONE_RADIUS) {
                targetSafeZoneRadius = MIN_SAFE_ZONE_RADIUS;
            }
            // 随机偏移安全区中心
            safeZoneCenterX += (random.nextFloat() - 0.5f) * 200f;
            safeZoneCenterY += (random.nextFloat() - 0.5f) * 200f;
            // 限制在地图内
            safeZoneCenterX = Math.max(targetSafeZoneRadius, Math.min(WORLD_WIDTH - targetSafeZoneRadius, safeZoneCenterX));
            safeZoneCenterY = Math.max(targetSafeZoneRadius, Math.min(WORLD_HEIGHT - targetSafeZoneRadius, safeZoneCenterY));
        }

        // 平滑缩圈
        if (safeZoneRadius > targetSafeZoneRadius) {
            safeZoneRadius -= (safeZoneRadius - targetSafeZoneRadius) * deltaTime * 0.5f;
        }

        // 安全区外伤害 - 玩家
        for (Ball pb : playerBalls) {
            if (!pb.alive) continue;
            float dx = pb.x - safeZoneCenterX;
            float dy = pb.y - safeZoneCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > safeZoneRadius) {
                // 在安全区外，持续扣体积
                pb.radius -= safeZoneDamage * deltaTime;
                if (pb.radius < 10f) {
                    pb.alive = false;
                }
            }
        }

        // 检查玩家是否全部死亡（安全区外伤害导致）
        boolean anyPlayerAlive = false;
        for (Ball b : playerBalls) {
            if (b.alive) { anyPlayerAlive = true; break; }
        }
        if (!anyPlayerAlive) {
            gameOver = true;
            gameRunning = false;
            if (callback != null) {
                callback.onGameOver(totalScore, killCount, maxRadius, eatFoodCount, gameTime);
            }
            return;
        }

        // 安全区外伤害 - AI
        for (Ball ai : aiBalls) {
            if (!ai.alive) continue;
            float dx = ai.x - safeZoneCenterX;
            float dy = ai.y - safeZoneCenterY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > safeZoneRadius) {
                ai.radius -= safeZoneDamage * deltaTime;
                if (ai.radius < 10f) {
                    ai.alive = false;
                }
            }
        }
    }
}
