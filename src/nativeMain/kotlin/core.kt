// NOTE: The origin is at the bottom left corner
//       x-axis increases left to right
//       y-axis increases bottom to top
//       velocity and accleration are positive if they point in the positive axis direction
//       motion equations are always positive form (we only add),
//         and the sign of velocity/acceleration determines the final value
@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.*

import raylib.*
import raylib.wrapper.*
import utils.*

object WindowParams {
    const val ASPECT_RATIO = 16f/9f
    const val WIDTH = 1600
    const val HEIGHT = (WIDTH / ASPECT_RATIO).toInt()
    const val WIN_TITLE = "icy-tower"
}

class AABB(
    val top:    Float,
    val left:   Float,
    val right:  Float,
    val bottom: Float,
)

/////////////////////////////////////////////////////////////
object GameState {
    var pause: Boolean = false
    var exit: Boolean = false
    var time: Float = 0f
    val mainPlayerID = 1
    val centerX = WindowParams.WIDTH/2f
    val centerY = WindowParams.HEIGHT/8f

    val playerPositionX = WindowParams.WIDTH/2f
    val playerPositionY = 10f

    var camera: NativeCamera2D = NativeCamera2D(
        initialOffsetX = centerX,
        initialOffsetY = centerY,
        initialTargetX = playerPositionX,
        initialTargetY = playerPositionY,
        initialRotation = 0.0f,
        initialZoom = 1.0f
    )

    var playerRef : GameObject? = null

    val entities:            ArrayList<GameObject>               = ArrayList()
    val inputComponents:     HashMap<Entity, InputComponent>     = HashMap()
    val physicsComponents:   HashMap<Entity, PhysicsComponent>   = HashMap()
    val collisionComponents: HashMap<Entity, CollisionComponent> = HashMap()
    val graphicsComponents:  HashMap<Entity, GraphicsComponent>  = HashMap()
}

/////////////////////////////////////////////////////////////

typealias Entity = Int

class GameObject (
    var id: Entity,
    var x:  Float = 0f,
    var y:  Float = 0f,
    var w:  Float = 0f,
    var h:  Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var ax: Float = 0f,
    var ay: Float = 0f,
) {
    val raylibX: Float get() = x
    val raylibY: Float get() = WindowParams.HEIGHT - y
}

data class CollisionResult (
    val cx: Float,
    val cy: Float,
    val nx: Float,
    val ny: Float,
)

/////////////////////////////////////////////////////////////
interface InputComponent {
    fun update(gobj: GameObject)
}

interface PhysicsComponent {
    fun update(gobj: GameObject, dt: Float)
}

interface GraphicsComponent {
    fun update(gobj: GameObject)
}

interface CollisionComponent {
    fun detectCollision(gobj: GameObject, other: GameObject): CollisionResult? {
        val aabb1 = aabb(gobj)
        val aabb2 = aabb(other)

        val overlapLeft   = aabb2.right  - aabb1.left
        val overlapRight  = aabb1.right  - aabb2.left
        val overlapBottom = aabb2.top    - aabb1.bottom
        val overlapTop    = aabb1.top    - aabb2.bottom

        val isColliding =
            overlapLeft   > 0 &&
            overlapRight  > 0 &&
            overlapBottom > 0 &&
            overlapTop    > 0

        if (!isColliding) return null

        val overlapX = minOf(overlapLeft, overlapRight)
        val overlapY = minOf(overlapBottom, overlapTop)

        val (nx, ny) = when {
            overlapX < overlapY -> (if (overlapLeft < overlapRight) -1f else 1f) to 0f
            else                -> 0f to (if (overlapBottom < overlapTop) -1f else 1f)
        }

        val cx = (maxOf(aabb1.left, aabb2.left) + minOf(aabb1.right, aabb2.right)) / 2f
        val cy = (maxOf(aabb1.bottom, aabb2.bottom) + minOf(aabb1.top, aabb2.top)) / 2f

        return CollisionResult(cx, cy, nx, ny)
    }

    fun aabb(gobj: GameObject) = AABB(
        left = gobj.x - gobj.w/2,
        right = gobj.x + gobj.w/2,
        top = gobj.y + gobj.h/2,
        bottom = gobj.y - gobj.h/2
    )

    fun resolveCollsion(gobj: GameObject, other: GameObject)
}

/////////////////////////////////////////////////////////////
class GameInputComponent : InputComponent {
    override fun update(gobj: GameObject) {
        when {
            isKeyPressed(KEY_Q)                        -> GameState.exit = true
            isKeyPressed(KEY_P) || isKeyPressed(KEY_K) -> GameState.pause = !GameState.pause
            isKeyPressed(KEY_R) && GameState.playerRef != null -> {
                val playerRef = GameState.playerRef!!
                playerRef.x = GameState.playerPositionX
                playerRef.y = GameState.playerPositionY
                playerRef.vx = 0f
                playerRef.vy = 0f
                playerRef.ax = 0f
                playerRef.ay = 0f

                val phyComp = GameState.physicsComponents[playerRef.id]!! as PlayerPhysicsComponent
                phyComp.playerState = PlayerState.Stationary
                phyComp.groundTile = null
            }
        }
    }
}

class GamePhysicsComponent : PhysicsComponent {
    override fun update(gobj: GameObject, dt: Float) {
        GameState.time += dt
        val playerGobj = GameState.entities[GameState.mainPlayerID]
        GameState.camera.focus(GameState.centerX, GameState.centerY,
                               playerGobj.x, playerGobj.y,
                               dt)
    }
}

class GameCollisionComponent : CollisionComponent {
    // TODO
    override fun resolveCollsion(gobj: GameObject, other: GameObject) {
        // TODO
    }
}

class GameGraphicsComponent : GraphicsComponent {
    override fun update(gobj: GameObject) {
        clearBackground(Color(0xFF181818u));
    }
}


/////////////////////////////////////////////////////////////
enum class Direction {
    Left, Right;
    operator fun not() = when (this) {
        Left  -> Right
        Right -> Left
    }
}

class TilePhysicsComponent(var dir: Direction = Direction.entries.random()) : PhysicsComponent {
    companion object {
        const val FLOATING_SPEED = 100f
    }

    override fun update(gobj: GameObject, dt: Float) {
        when (dir) {
            Direction.Left  -> {
                gobj.vx = -FLOATING_SPEED
                gobj.x += gobj.vx * dt
                // REFACTOR?: move out to a collision component
                if (gobj.x - gobj.w/2 - FLOATING_SPEED * dt < 0) dir = !dir
            }
            Direction.Right -> {
                gobj.vx = FLOATING_SPEED
                gobj.x += gobj.vx * dt
                // REFACTOR?: move out to a collision component
                if (gobj.x + gobj.w/2 + FLOATING_SPEED * dt > WindowParams.WIDTH) dir = !dir
            }
        }
    }
}

class TileGraphicsComponent : GraphicsComponent {
    override fun update(gobj: GameObject) {
        drawRectangleV(
            gobj.raylibX - gobj.w/2,
            gobj.raylibY - gobj.h/2,
            gobj.w,
            gobj.h,
            RED
        )
    }
}

/////////////////////////////////////////////////////////////
enum class PlayerState {
    Stationary,
    OnTile,
    Falling,
    Rising
}

class PlayerHumanInputComponent(val h_max: Float, val t_max: Float) : InputComponent {
    companion object {
        const val WALK_SPEED = 200f
    }
    override fun update(gobj: GameObject) {
        val phyComp = GameState.physicsComponents[gobj.id]!! as PlayerPhysicsComponent
        if (isKeyDown(KEY_SPACE) &&
            phyComp.playerState in listOf(PlayerState.Stationary, PlayerState.OnTile)) {
            gobj.ay = -(2*h_max)/(t_max * t_max)
            gobj.vy = 2*h_max/t_max
            phyComp.playerState = PlayerState.Rising
            phyComp.groundTile = null
        }

        when {
            isKeyDown(KEY_RIGHT) || isKeyDown(KEY_D) -> gobj.vx =  WALK_SPEED
            isKeyDown(KEY_LEFT)  || isKeyDown(KEY_A) -> gobj.vx = -WALK_SPEED
            phyComp.playerState == PlayerState.Stationary -> gobj.vx = 0f
            phyComp.playerState == PlayerState.OnTile -> {}
            else -> {}
        }
    }
}

class PlayerPhysicsComponent : PhysicsComponent {
    var playerState = PlayerState.Stationary
    var groundTile: GameObject? = null

    override fun update(gobj: GameObject, dt: Float) {
        // the x-axis motion is independent of the player state
        gobj.x += gobj.vx * dt
        when (playerState) {
            PlayerState.Stationary -> {}
            PlayerState.OnTile -> {
                // for smart casting to work
                val tile = groundTile
                if (tile != null && isOverlapping(gobj, tile)) gobj.vx = tile.vx
                else playerState = PlayerState.Falling
            }
            PlayerState.Rising     -> {
                gobj.vy += gobj.ay * dt
                gobj.y  += gobj.vy * dt
                if (gobj.vy < 0) {
                    gobj.ay *= 4f
                    playerState = PlayerState.Falling
                }
            }
            PlayerState.Falling    -> {
                gobj.vy += gobj.ay * dt
                gobj.y  += gobj.vy * dt
            }
        }
    }

    private fun isOverlapping(player: GameObject, tile: GameObject): Boolean {
        val playerLeft  = player.x - player.w / 2
        val playerRight = player.x + player.w / 2
        val tileLeft    = tile.x - tile.w / 2
        val tileRight   = tile.x + tile.w / 2

        // Standard AABB horizontal overlap check
        return playerRight > tileLeft && playerLeft < tileRight
    }
}

class PlayerCollisionComponent : CollisionComponent {
    override fun resolveCollsion(gobj: GameObject, other: GameObject) {
        val phyComp = GameState.physicsComponents[gobj.id]!! as PlayerPhysicsComponent

        if (phyComp.playerState != PlayerState.Falling) return

        val (_, _, _, ny) = detectCollision(gobj, other) ?: return
        if (ny > 0f) {
            gobj.vx = other.vx
            gobj.vy = 0f

            gobj.y = other.y + other.h/2 + gobj.h/2
            phyComp.playerState = PlayerState.OnTile
            phyComp.groundTile = other
        }
    }
}

class PlayerGraphicsComponent : GraphicsComponent {
    override fun update(gobj: GameObject) {
        drawRectangleV(
            gobj.raylibX - gobj.w/2,
            gobj.raylibY - gobj.h/2,
            gobj.w,
            gobj.h,
            GREEN
        )
    }
}

/////////////////////////////////////////////////////////////
fun main() {
    setTraceLogLevel(LOG_ALL)
    setConfigFlags(FLAG_WINDOW_ALWAYS_RUN)
    setTargetFPS(60)
    initWindow(WindowParams.WIDTH, WindowParams.HEIGHT, WindowParams.WIN_TITLE)

    val wrange = IntRange(0, WindowParams.WIDTH)
    val hrange = IntRange(0, WindowParams.HEIGHT)

    val game = GameObject(
        id = 0,
        x = WindowParams.WIDTH/2f,
        y = WindowParams.HEIGHT/2f,
        w = WindowParams.WIDTH.toFloat(),
        h = WindowParams.HEIGHT.toFloat(),
    )
    GameState.entities.add(GameObject(id = 0));
    GameState.inputComponents[0]    = GameInputComponent()
    GameState.physicsComponents[0]  = GamePhysicsComponent()
    GameState.graphicsComponents[0] = GameGraphicsComponent()

    val player = GameObject(
        id = 1,
        x = GameState.playerPositionX,
        y = GameState.playerPositionY,
        w = 30f,
        h = 30f
    )
    GameState.entities.add(player);
    GameState.inputComponents[1]     = PlayerHumanInputComponent(h_max = 400f, t_max = 1f)
    GameState.physicsComponents[1]   = PlayerPhysicsComponent()
    GameState.collisionComponents[1] = PlayerCollisionComponent()
    GameState.graphicsComponents[1]  = PlayerGraphicsComponent()
    GameState.playerRef = player

    for (id in 2 until 10) {
        val tile = GameObject(
            id = id,
            x = (id - 1) * 300f,
            y = (id - 1) * 100f,
            w = 400f,
            h = 20f,
        )
        GameState.entities.add(tile);
        GameState.physicsComponents[id]  = TilePhysicsComponent()
        GameState.graphicsComponents[id] = TileGraphicsComponent()

    }

    fun shouldClose(): Boolean = GameState.exit || windowShouldClose()

    while (!shouldClose()) {
        // Input System
        for (entity in GameState.entities) {
            GameState.inputComponents[entity.id]?.update(entity)
        }

        // Physics System
        val dt = getFrameTime();
        if (!GameState.pause) {
            for (entity in GameState.entities) {
                GameState.physicsComponents[entity.id]?.update(entity, dt)
            }
        }
                // Collision System
        for (i in 0 until GameState.entities.size) {
            for (j in i+1 until GameState.entities.size) {
                val a = GameState.entities[i]
                val b = GameState.entities[j]
                GameState.collisionComponents[a.id]?.resolveCollsion(a, b)
            }
        }

        // Graphics System
        GameState.camera.use {
            for (entity in GameState.entities) {
                GameState.graphicsComponents[entity.id]?.update(entity)
            }
        }
    }

    closeWindow()
}
// TODO: collision detection
// Use either:
// - collision component
// - direct collision system
