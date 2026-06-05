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

/////////////////////////////////////////////////////////////
// Geometry
data class Vec2(var x: Float, var y: Float) {
    val raylibCoords get() = Vec2(x , Config.HEIGHT - y)
    val length get() = sqrt(x*x + y*y)

    operator fun plus(i: Float)  = Vec2(x + i, y + i)
    operator fun minus(d: Float) = Vec2(x - d, y - d)
    operator fun times(m: Float) = Vec2(x * m, y * m)
    operator fun plus(p: Vec2)   = Vec2(x + p.x, y + p.y)
    operator fun minus(p: Vec2)  = Vec2(x - p.x, y - p.y)
    operator fun times(p: Vec2)  = Vec2(x * p.x, y * p.y)

    fun dot(p: Vec2) = this * p
}

data class Rect(var x1: Float, var y1: Float, var w: Float, var h: Float) {
    val x2   get() = x1 + w
    val y2   get() = y1 + h
    val area get() = w * h

    fun draw(c: Color) = drawRectangleV(x1, Config.HEIGHT - y1 - h, w, h, c)
    fun overlapArea(b: Rect) : Float {
        // Invariant: x1, y1 is the the bottom-left corner of the rectangle
        // Invariant: x1 < x2, y1 < y2
        val x_overlap = intervalOverlap(x1, x2, b.x1, b.x2)
        val y_overlap = intervalOverlap(y1, y2, b.y1, b.y2)
        return x_overlap * y_overlap
    }
    fun overlapIntervals(b: Rect) : Vec2 {
        // Invariant: x1, y1 is the the bottom-left corner of the rectangle
        // Invariant: x1 < x2, y1 < y2
        val x_overlap = intervalOverlap(x1, x2, b.x1, b.x2)
        val y_overlap = intervalOverlap(y1, y2, b.y1, b.y2)
        return Vec2(x_overlap, y_overlap)
    }

    fun compare(b: Rect) : Pair<Boolean, Boolean> {
        return Pair(x1 < b.x1, y1 < b.y1)
    }

    companion object {
        private fun intervalOverlap(a1: Float, a2: Float, b1: Float, b2: Float) : Float {
            // the above invariant ensure that these are always positive
            return max(min(a2, b2) - max(a1, b1), 0.0f)
        }
    }
}

/////////////////////////////////////////////////////////////
// Camera
data class Camera2D(var offset: Vec2, var target: Vec2, var rotation: Float, var zoom: Float) {

    val left   get() = target.x - offset.x
    val right  get() = target.x + (Config.WIDTH  - offset.x)
    val bottom get() = target.y - offset.y
    val top    get() = target.y + (Config.HEIGHT - offset.y)

    val scrollSpeed = 200.0f
    val followSpeed = 10.0f
    val followThreshold = Config.HEIGHT * 1/3

    fun toCValue(memScope: MemScope): CValue<raylib.Camera2D> {
        val cam = memScope.alloc<raylib.Camera2D>()

        cam.offset.x = offset.raylibCoords.x
        cam.offset.y = offset.raylibCoords.y
        cam.target.x = target.raylibCoords.x
        cam.target.y = target.raylibCoords.y

        cam.rotation = rotation
        cam.zoom = zoom
        return cam.readValue()
    }

    fun focus(offset:Vec2, target: Vec2, dt: Float) {
        this.offset = offset
        this.target.y += scrollSpeed * dt

        val dist = target.y - this.target.y
        if (dist > followThreshold) {
            val dy = target.y - followThreshold
            this.target.y += (dy - this.target.y) * followSpeed * dt
        }

    }
}

fun beginMode2D(camera: Camera2D) = memScoped { BeginMode2D(camera.toCValue(this)) }
fun endMode2D() =  EndMode2D()
/////////////////////////////////////////////////////////////

object Config {
    const val ASPECT_RATIO = 16.0f/9.0f
    const val WIDTH = 1600
    const val HEIGHT = (WIDTH / ASPECT_RATIO).toInt()
    const val WIN_TITLE = "kotlin"
    const val BELL_FILEPATH = "./assets/bell.wav"

    const val TILE_HEIGHT = 25.0f
}

object ScrollSpeed {
    private val values: Array<Float> = Array (3) { i -> i * 5.0f}

    private const val default = 1.0f

    operator fun get(level: Int): Float = values[level] ?: default

    val all get() = values
}

interface Entity {
    fun update(dt: Float)
    fun handleKey(key: UInt)
    fun draw()
}

class Surface(val muS: Float, val muK: Float) {

}

class Tile(val rect: Rect) : Entity {
    override fun update(dt: Float) {}
    override fun handleKey(key: UInt) {}
    override fun draw() { rect.draw(RED) }
}

class TileManager(var scrollSpeed: Float) : Entity {
    private var tiles : Array<Tile> = run {
        val count: Int = Config.HEIGHT/Config.TILE_HEIGHT.toInt()
        val repeat: Int = count * 2
        val initValue: Array<Tile> = Array(count*10) { i ->
            val x1 :Float =
                if (i % repeat > 0) Random.nextInt(IntRange(Config.WIDTH *1/5,
                                                            Config.WIDTH *4/5)).toFloat()
                else 0.0f
            val y1: Float = i * 200.0f
            val w: Float =
                if (i % repeat > 0) Random.nextInt(IntRange(Config.WIDTH *1/5,
                                                            Config.WIDTH *4/5)).toFloat()
                else Config.WIDTH.toFloat()
            val h: Float = Config.TILE_HEIGHT

            Tile(Rect(x1, y1, max(w, 200.0f), h))
        }
        initValue
    }

    var ground_rect: Rect = tiles[0].rect

    override fun update(dt: Float) {
        // TODO(investigate)
        // for (tile in tiles) tile.rect.y1 -= scrollSpeed
    }
    override fun handleKey(key: UInt) {}
    override fun draw() {
        for (tile in  tiles) tile.draw()
    }
    fun detectCollision(p: Player) {
        val tile_ys : List<Float> = tiles.asList().map { t :Tile -> t.rect.y2 }
        val lower_bound_cmp: (Float, Float) -> Int = { a, b ->
            if (a <= b) -1 else 1
        }
        val i: Int = -(tile_ys.binarySearch(p.rect.y1, lower_bound_cmp) + 1) - 1

        if (i < 0) {
            println("ERROR")
            return
        }
        if (i >= tile_ys.count()) {
            println("Array out of bounds: $i")
            return
        }

        ground_rect = tiles[i].rect
        if (p.falling && p.rect.y1 >= ground_rect.y2 &&
                p.rect.overlapIntervals(ground_rect).x > 0) {
            p.ground_rect = ground_rect
        } else if (p.falling) {
            p.ground_rect = Rect(0.0f, 0.0f, 0.0f, 0.0f)
            // p.ground_rect = tiles[0].rect
        }
    }
}

class Player(var rect: Rect, var ground_rect: Rect) : Entity {

    var grounded = true
    var v = Vec2(0.0f, 0.0f)
    val falling: Boolean get() = v.y < 0
    val pos: Vec2        get() = Vec2(rect.x1, rect.y1)

    private val h_max = 250.0f
    private val t_max = 0.3f

    private val a     = Vec2(0.0f, -1 * computeGravityAccleration(h_max, t_max))
    private val v0    = Vec2(0.0f, computeInitialVeloctiy(h_max, t_max))

    companion object {
        fun computeGravityAccleration(h_max: Float, t_max: Float) =
            (2*h_max)/(t_max * t_max)
        fun computeInitialVeloctiy(h_max: Float, t_max: Float) =
            2*h_max/t_max
    }

    override fun update(dt: Float) {
        if (grounded) {
            rect.y1 = ground_rect.y2
            return
        }
        v += a * dt
        rect.y1 += v.y*dt + a.y * dt*dt * 0.5f
        rect.x1 += v.x*dt + a.x * dt*dt * 0.5f

        if (falling && rect.y1 - ground_rect.y2 <= 1.0f ) {
            v.y = 0.0f
            rect.y1 = ground_rect.y2
            grounded = true
        }
    }

    override fun handleKey(key: UInt) {
        if (isKeyDown(KEY_RIGHT) || isKeyDown(KEY_D)) {
            rect.x1 += 5
        }
        if (isKeyDown(KEY_LEFT) || isKeyDown(KEY_A)) {
            rect.x1 -= 5
        }
        if (isKeyDown(KEY_SPACE)) {
            v.y = v0.y
            grounded = false
        }
        if (rect.overlapIntervals(ground_rect).x == 0.0f) {
            grounded = false
            ground_rect = Rect(0.0f, 0.0f, 0.0f, 0.0f)
        }
    }

    override fun draw() { rect.draw(GREEN) }
}

class Game {
    var exit: Boolean  = false
    var pause: Boolean = false
    var time: Float    = 0.0f
    var gameLevel: Int = 1
    var scroe: Int = 0

    val center = Vec2(Config.WIDTH.toFloat()/2, Config.HEIGHT.toFloat()/2)

    var tilemgr = TileManager(ScrollSpeed[gameLevel])
    var player = Player(Rect(Config.WIDTH/2 - 25.0f, Config.TILE_HEIGHT,
                             50.0f, 50.0f),
                        tilemgr.ground_rect)

    var camera: Camera2D =
        Camera2D(offset = center,
                 target = player.pos,
                 rotation = 0.0f,
                 zoom = 1.0f)

    var entities: Array<Entity> = arrayOf(
        tilemgr,
        player,
    )

    fun shouldClose(): Boolean = exit || windowShouldClose()

    fun over(): Boolean = player.rect.y2 < camera.bottom

    fun update(dt: Float) {
        if (!pause) {
            time += dt
            camera.focus(center, player.pos, dt)
            for (entity in entities) entity.update(dt)
            tilemgr.detectCollision(player)
        }
    }

    fun handleKey(key: UInt) {
        when (key) {
            KEY_Q        -> exit = true
            KEY_P, KEY_K -> pause = !pause
            KEY_R        -> {}
        }
        for (entity in entities) entity.handleKey(key)
    }

    fun draw() {
        beginDrawing()
        beginMode2D(camera)
            clearBackground(Color(0xFF181818u));
            for (entity in entities) entity.draw()
        endMode2D()
        endDrawing()
    }
}

fun main() {
    setTraceLogLevel(LOG_ALL)
    setConfigFlags(FLAG_WINDOW_UNDECORATED  or
                   FLAG_WINDOW_TOPMOST      or
                   FLAG_WINDOW_ALWAYS_RUN)
    setTargetFPS(60)
    initWindow(Config.WIDTH, Config.HEIGHT, Config.WIN_TITLE)
    initAudioDevice()

    var game = Game()

    while(!game.shouldClose()) {
        game.handleKey(getKeyPressed())
        if (game.over()) {
            beginDrawing()
            clearBackground(Color(0xFF181818u));
            endDrawing()
            drawText("Game Over", Config.WIDTH/2, Config.HEIGHT/2, 28, WHITE);
        } else {
            game.update(getFrameTime())
            game.draw()
        }
    }

    closeAudioDevice()
    closeWindow()
}
