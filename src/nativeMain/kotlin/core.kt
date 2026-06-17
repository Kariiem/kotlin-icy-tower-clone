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

// Camera
class Camera2D (
    var offsetX: Float,
    var offsetY: Float,
    var targetX: Float,
    var targetY: Float,
    var rotation: Float,
    var zoom: Float
) {
    val left   get() = targetX - offsetX
    val right  get() = targetX + (WindowParams.WIDTH  - offsetX)
    val bottom get() = targetY - offsetY
    val top    get() = targetY + (WindowParams.HEIGHT - offsetY)

    val raylibOffsetX: Float get() = offsetX
    val raylibOffsetY: Float get() = WindowParams.HEIGHT - offsetY
    val raylibTargetX: Float get() = targetX
    val raylibTargetY: Float get() = WindowParams.HEIGHT - targetY


    val scrollSpeed = 200.0f
    val followSpeed = 10.0f
    val followThreshold = WindowParams.HEIGHT * 1/3

    companion object {
        fun beginMode2D(camera: Camera2D) = memScoped { BeginMode2D(camera.toCValue(this)) }
        fun endMode2D() =  EndMode2D()
    }

    fun toCValue(memScope: MemScope): CValue<raylib.Camera2D> {
        val cam = memScope.alloc<raylib.Camera2D>()

        cam.offset.x = raylibOffsetX
        cam.offset.y = raylibOffsetY
        cam.target.x = raylibTargetX
        cam.target.y = raylibTargetY

        cam.rotation = rotation
        cam.zoom = zoom
        return cam.readValue()
    }

    fun focus(offsetX: Float, offsetY: Float,
              targetX: Float, targetY: Float,
              dt: Float) {
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.targetY += scrollSpeed * dt

        val dist = targetY - this.targetY
        if (dist > followThreshold) {
            val dy = targetY - followThreshold
            this.targetY += (dy - this.targetY) * followSpeed * dt
        }

    }
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
    val centerY = WindowParams.HEIGHT/2f

    val playerPositionX = WindowParams.WIDTH/2f
    val playerPositionY = 10f

    var camera: Camera2D = Camera2D(
        offsetX = centerX,
        offsetY = centerY,
        targetX = playerPositionX,
        targetY = playerPositionY,
        rotation = 0.0f,
        zoom = 1.0f
    )
    val entities:           ArrayList<GameObject>              = ArrayList()
    val inputComponents:    HashMap<Entity, InputComponent>    = HashMap()
    val physicsComponents:  HashMap<Entity, PhysicsComponent>  = HashMap()
    val graphicsComponents: HashMap<Entity, GraphicsComponent> = HashMap()
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
    var isGrounded: Boolean = true

    val raylibX: Float get() = x
    val raylibY: Float get() = WindowParams.HEIGHT - y
}


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
    fun detectCollision(gobj: GameObject, other: GameObject): Boolean

    fun aabb(gobj: GameObject) = AABB(
        left = gobj.x - gobj.w/2,
        right = gobj.x + gobj.w/2,
        top = gobj.y + gobj.h/2,
        bottom = gobj.y - gobj.h/2
    )
}

/////////////////////////////////////////////////////////////
class GameInputComponent : InputComponent {
    override fun update(gobj: GameObject) {
        when {
            isKeyPressed(KEY_Q)                        -> GameState.exit = true
            isKeyPressed(KEY_P) || isKeyPressed(KEY_K) -> GameState.pause = !GameState.pause
            isKeyPressed(KEY_R)                        -> {}
        }
    }
}

class GamePhysicsComponent : PhysicsComponent {
    override fun update(gobj: GameObject, dt: Float) {
        if (GameState.pause) return;
        GameState.time += dt
        val playerGobj = GameState.entities[GameState.mainPlayerID]
        GameState.camera.focus(GameState.centerX, GameState.centerY,
                               playerGobj.x, playerGobj.y,
                               dt)
    }
}

class GameGraphicsComponent : GraphicsComponent {
    override fun update(gobj: GameObject) {
        clearBackground(Color(0xFF181818u));
    }
}

class GameCollisionComponent : CollisionComponent {
    override fun detectCollision(gobj: GameObject, other: GameObject): Boolean {
        //cannot be null, if an entity collides, it has physics component
        // getValue(id) is supposedly better, but !! is more clear
        val gobjPhysicsComp = GameState.physicsComponents[gobj.id]!!
        val otherPhysicsComp = GameState.physicsComponents[other.id]!!

        return false
    }
}

/////////////////////////////////////////////////////////////
class TilePhysicsComponent : PhysicsComponent {
    companion object {
        const val FLOATING_SPEED = 10f
    }

    enum class Direction {
        Left, Right;
        operator fun not() = when (this) {
            Left  -> Right
            Right -> Left
        }
    }

    var dir = Direction.entries.random()

    override fun update(gobj: GameObject, dt: Float) {
        if (GameState.pause) return;
        when (dir) {
            Direction.Left  -> {
                gobj.x -= FLOATING_SPEED
                if (gobj.x - gobj.w/2 - FLOATING_SPEED < 0) dir = !dir // REFACTOR?: move out to a collision component
            }
            Direction.Right -> {
                gobj.x += FLOATING_SPEED
                if (gobj.x + gobj.w/2 + FLOATING_SPEED > WindowParams.WIDTH) dir = !dir // REFACTOR?: move out to a collision component
            }
        }
    }
}

class TileGraphicsComponent : GraphicsComponent {
    override fun update(gobj: GameObject) {
        drawRectangleV(
            gobj.x - gobj.w/2,
            gobj.y - gobj.h/2,
            gobj.w,
            gobj.h,
            RED
        )
    }
}

/////////////////////////////////////////////////////////////
class PlayerHumanInputComponent(val h_max: Float, val t_max: Float) : InputComponent {
    companion object {
        const val WALK_SPEED = 200f
    }

    override fun update(gobj: GameObject) {
        if (isKeyDown(KEY_SPACE) && gobj.isGrounded) {
            gobj.ay = -(2*h_max)/(t_max * t_max)
            gobj.vy = 2*h_max/t_max
            gobj.isGrounded = false
        }

        when {
            isKeyDown(KEY_RIGHT) || isKeyDown(KEY_D) -> gobj.vx =  WALK_SPEED
            isKeyDown(KEY_LEFT)  || isKeyDown(KEY_A) -> gobj.vx = -WALK_SPEED
            else -> {
                gobj.vx = 0f
            }
        }
    }
}

class PlayerPhysicsComponent : PhysicsComponent {
    companion object {
        val GROUND_Y = GameState.playerPositionY // TODO: refactor
    }
    var isFalling: Boolean = false

    override fun update(gobj: GameObject, dt: Float) {
        if (GameState.pause) return;

        gobj.vy += gobj.ay * dt
        gobj.x  += gobj.vx * dt
        gobj.y  += gobj.vy * dt

        if (gobj.vy < 0 && !isFalling) {
            gobj.ay *= 4f
            isFalling = true
        }

        // TODO: this should be handled in collision component/system
        if (gobj.y <= GROUND_Y) {
            gobj.y  = GROUND_Y
            gobj.vy = 0f
            gobj.ay = 0f
            gobj.isGrounded = true
            isFalling = false
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
    GameState.inputComponents[1]    = PlayerHumanInputComponent(h_max = 400f, t_max = 1f)
    GameState.physicsComponents[1]  = PlayerPhysicsComponent()
    GameState.graphicsComponents[1] = PlayerGraphicsComponent()


    val tile = GameObject(
        id = 2,
        x = Random.nextInt(wrange).toFloat(),
        y = Random.nextInt(hrange).toFloat(),
        w = Random.nextInt(wrange).toFloat(), // TODO: fix the case where the tile is wider than the window
        h = 20f,
    )
    GameState.entities.add(tile);
    GameState.physicsComponents[2]  = TilePhysicsComponent()
    GameState.graphicsComponents[2] = TileGraphicsComponent()

    fun shouldClose(): Boolean = GameState.exit || windowShouldClose()

    while (!shouldClose()) {
        for (entity in GameState.entities) {
            GameState.inputComponents[entity.id]?.update(entity)
        }
        val dt = getFrameTime();
        for (entity in GameState.entities) {
            GameState.physicsComponents[entity.id]?.update(entity, dt)
        }
        beginDrawing()
        drawText("-------------------------", 0, WindowParams.HEIGHT - 400 - 30/2, 16, BLUE)
        for (entity in GameState.entities) {
            GameState.graphicsComponents[entity.id]?.update(entity)
        }
        endDrawing()
    }

    closeWindow()
}
// TODO: collision detection
// Use either:
// - collision component
// - direct collision system
