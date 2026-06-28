// NOTE: The origin is at the bottom left corner
//       x-axis increases left to right
//       y-axis increases bottom to top
//       velocity and accleration are positive if they point in the positive axis direction
//       motion equations are always positive form (we only add),
//         and the sign of velocity/acceleration determines the final value
@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*

import kotlin.math.sign
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.*

import raylib.*
import raylib.wrapper.*

object WindowParams {
    const val ASPECT_RATIO = 16f/9f
    const val WIDTH = 1600f
    const val HEIGHT = WIDTH / ASPECT_RATIO
    const val WIN_TITLE = "icy-tower"
}

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

    var cameraTrackOn = true
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


    fun reset() {
        cameraTrackOn = true

        // reset camera motion
        camera.offsetX = centerX
        camera.offsetY = centerY
        camera.targetX = playerPositionX
        camera.targetY = playerPositionY
        camera.rotation = 0.0f
        camera.zoom = 1.0f

        // reset player position and physical state
        val player = entities[mainPlayerID]
        player.x = playerPositionX
        player.y = playerPositionY
        player.vx = 0f
        player.vy = 0f
        player.ax = 0f
        player.ay = 0f

        val phyComp = physicsComponents[player.id]!! as PlayerPhysicsComponent
        phyComp.playerState = PlayerState.Stationary
    }

    fun frameForward() {
        @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
        assert(pause)
        // TODO
    }
}

/////////////////////////////////////////////////////////////

typealias Entity = Int

data class GameObject (
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

data class AABB(
    val t: Float,
    val b: Float,
    val l: Float,
    val r: Float,
) {
    constructor(gobj: GameObject) :
        this(t = gobj.y + gobj.h/2,
             b = gobj.y - gobj.h/2,
             l = gobj.x - gobj.w/2,
             r = gobj.x + gobj.w/2)
}

data class CollisionResult (
    val cx: Float,
    val cy: Float,
    val nx: Float,
    val ny: Float,
)

/////////////////////////////////////////////////////////////
interface InputComponent {
    fun handle(gobj: GameObject)
}

interface PhysicsComponent {
    fun update(gobj: GameObject, dt: Float)
}

interface GraphicsComponent {
    fun render(gobj: GameObject)
}

interface CollisionComponent {
    fun resolveCollsion(gobj: GameObject, other: GameObject)
}

/////////////////////////////////////////////////////////////
class GameInputComponent : InputComponent {
    override fun handle(gobj: GameObject) {
        when {
            isKeyPressed(KEY_Q)                        -> GameState.exit = true
            isKeyPressed(KEY_P) || isKeyPressed(KEY_K) -> GameState.pause = !GameState.pause
            isKeyPressed(KEY_R)                        -> GameState.reset()
            isKeyPressed(KEY_S)                        -> GameState.cameraTrackOn = false
            isKeyPressed(KEY_F)                        -> GameState.frameForward()
        }
    }
}

class GamePhysicsComponent : PhysicsComponent {
    override fun update(gobj: GameObject, dt: Float) {
        traceLog(LOG_DEBUG, "PhysicsComponent.update: Game:   $gobj, dt: $dt")

        GameState.time += dt
        val playerGobj = GameState.entities[GameState.mainPlayerID]
        if (GameState.cameraTrackOn) {
            GameState.camera.focus(GameState.centerX, GameState.centerY,
                                   playerGobj.x,
                                   playerGobj.y,
                                   dt)
        }
    }
}

class GameGraphicsComponent : GraphicsComponent {
    override fun render(gobj: GameObject) {
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
        const val FLOATING_SPEED = 0f
    }

    override fun update(gobj: GameObject, dt: Float) {
        traceLog(LOG_DEBUG, "PhysicsComponent.update: Tile:   $gobj, dt: $dt")

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
    override fun render(gobj: GameObject) {
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
    override fun handle(gobj: GameObject) {
        val phyComp = GameState.physicsComponents[gobj.id]!! as PlayerPhysicsComponent
        if (isKeyDown(KEY_SPACE) &&
            phyComp.playerState in listOf(PlayerState.Stationary, PlayerState.OnTile)) {
            gobj.ay = -(2*h_max)/(t_max * t_max)
            gobj.vy = 2*h_max/t_max
            phyComp.playerState = PlayerState.Rising
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

    override fun update(gobj: GameObject, dt: Float) {
        traceLog(LOG_DEBUG, "PhysicsComponent.update: Player: $gobj, dt: $dt, playerState: $playerState")

        // the x-axis motion is independent of the player state
        gobj.x += gobj.vx * dt
        when (playerState) {
            PlayerState.Stationary -> {}
            PlayerState.OnTile     -> {
                playerState = PlayerState.Falling

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
}


class PlayerCollisionComponent : CollisionComponent {
    override fun resolveCollsion(gobj: GameObject, other: GameObject) {
        if (other.id == 0) return
        val phyComp = GameState.physicsComponents[gobj.id]!! as PlayerPhysicsComponent
        if (phyComp.playerState != PlayerState.Falling) return

        val playerAABB = AABB(gobj)
        val tileAABB   = AABB(other)

        val verticalPenetration   = tileAABB.t - playerAABB.b
        val horizontalPenetration = minOf(playerAABB.r, tileAABB.r) - maxOf(playerAABB.l, tileAABB.l)

        if (0f <= verticalPenetration && verticalPenetration  <= horizontalPenetration) {
            gobj.vy = 0f
            gobj.y  = tileAABB.t + gobj.h / 2
            gobj.vx = other.vx
            phyComp.playerState = PlayerState.OnTile
        }
    }
}

class PlayerGraphicsComponent : GraphicsComponent {
    override fun render(gobj: GameObject) {
        drawRectangleV(
            gobj.raylibX - gobj.w/2,
            gobj.raylibY - gobj.h/2,
            gobj.w,
            gobj.h,
            GREEN
        )
    }
}

////////////////////////////////////////////////////////////
fun shouldClose(): Boolean = GameState.exit || windowShouldClose()

////////////////////////////////////////////////////////////
fun main() {
    // No logs
    setTraceLogLevel(LOG_NONE)
    setConfigFlags(FLAG_WINDOW_ALWAYS_RUN)
    setTargetFPS(60)
    initWindow(WindowParams.WIDTH.toInt(), WindowParams.HEIGHT.toInt(), WindowParams.WIN_TITLE)

    val wrange = IntRange(0, WindowParams.WIDTH.toInt())
    val hrange = IntRange(0, WindowParams.HEIGHT.toInt())

    run {
        val game = GameObject(
            id = 0,
            x = WindowParams.WIDTH/2f,
            y = WindowParams.HEIGHT/2f,
            w = WindowParams.WIDTH,
            h = WindowParams.HEIGHT,
        )
        GameState.entities.add(game);
        GameState.inputComponents[game.id]    = GameInputComponent()
        GameState.physicsComponents[game.id]  = GamePhysicsComponent()
        GameState.graphicsComponents[game.id] = GameGraphicsComponent()
    }
    run {
        val player = GameObject(
            id = GameState.mainPlayerID,
            x = GameState.playerPositionX,
            y = GameState.playerPositionY,
            w = 30f,
            h = 30f
        )
        GameState.entities.add(player);
        GameState.inputComponents[player.id]     = PlayerHumanInputComponent(h_max = 400f, t_max = 0.7f)
        GameState.physicsComponents[player.id]   = PlayerPhysicsComponent()
        GameState.collisionComponents[player.id] = PlayerCollisionComponent()
        GameState.graphicsComponents[player.id]  = PlayerGraphicsComponent()
    }
    run {
        for (id in 2 until 100) {
            val tile = GameObject(
                id = id,
                x = (id * 1000) % WindowParams.WIDTH,
                y = (id - 1) * 200f,
                w = 400f,
                h = 20f,
            )
            GameState.entities.add(tile);
            GameState.physicsComponents[id]  = TilePhysicsComponent()
            GameState.graphicsComponents[id] = TileGraphicsComponent()
        }
    }

    // Debug logs
    setTraceLogLevel(LOG_DEBUG)
    traceLog(LOG_NONE, "\n////////////////////////////////////////////////////////////")

    traceLog(LOG_DEBUG, "Entity count: ${GameState.entities.size}")
    setTraceLogLevel(LOG_NONE)
    while (!shouldClose()) {
        // Input System
        for (entity in GameState.entities) {
            GameState.inputComponents[entity.id]?.handle(entity)
        }

        // Physics System
        val dt = getFrameTime();
        if (!GameState.pause) {
            for (entity in GameState.entities) {
                GameState.physicsComponents[entity.id]?.update(entity, dt)
            }
        }

        // Collision System
        for (mainEntity in GameState.entities) {
            for (otherEntity in GameState.entities) {
                if (otherEntity.id <= mainEntity.id) continue
                GameState.collisionComponents[mainEntity.id]?.resolveCollsion(mainEntity, otherEntity)
            }
        }

        // Graphics System
        GameState.camera.use {
            for (entity in GameState.entities) {
                GameState.graphicsComponents[entity.id]?.render(entity)
            }
        }
    }

    // No logs
    setTraceLogLevel(LOG_NONE)
    closeWindow()
}

// DONE: collision detection
// Use either:
// - [X] collision component
// - [ ] direct collision system
