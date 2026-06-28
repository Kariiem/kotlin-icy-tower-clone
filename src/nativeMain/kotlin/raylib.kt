@file:OptIn(ExperimentalForeignApi::class)
package raylib.wrapper

import kotlinx.cinterop.*
import kotlin.native.ref.createCleaner
import raylib.*

fun clearBackground(c: Color)                  = ClearBackground(c.readValue())
fun clearBackground(c: CValue<Color>)          = ClearBackground(c)
fun setTraceLogLevel(i: UInt)                  = SetTraceLogLevel(i.toInt())
fun setConfigFlags(i: UInt)                    = SetConfigFlags(i)

fun initWindow(w: Int, h: Int, name: String)   = InitWindow(w, h, name)
fun closeWindow()                              = CloseWindow()
fun windowShouldClose()                        = WindowShouldClose()

fun setTargetFPS(fps: Int)                     = SetTargetFPS(fps)

fun initAudioDevice()                          = InitAudioDevice()
fun closeAudioDevice()                         = CloseAudioDevice()
fun isAudioDeviceReady()                       = IsAudioDeviceReady()
fun loadSound(filepath: String): CValue<Sound> = LoadSound(filepath)
fun playSound(sound: CValue<Sound>)            = PlaySound(sound)
fun stopSound(sound: CValue<Sound>)            = StopSound(sound)
fun pauseSound(sound: CValue<Sound>)           = PauseSound(sound)
fun resumeSound(sound: CValue<Sound>)          = ResumeSound(sound)


fun getKeyPressed()                            = GetKeyPressed().toUInt()
fun isKeyDown(key: UInt)                       = IsKeyDown(key.toInt())
fun isKeyPressed(key: UInt)                    = IsKeyPressed(key.toInt())
fun isKeyPressedRepeat(key: UInt)              = IsKeyPressedRepeat(key.toInt())

fun beginDrawing()                             = BeginDrawing()
fun endDrawing()                               = EndDrawing()

fun beginMode2D(camera: CValue<Camera2D>)     = BeginMode2D(camera)
fun endMode2D()                               = EndMode2D()

fun getTime()                                  = GetTime()
fun getFrameTime()                             = GetFrameTime()

fun traceLog(logLevel: UInt, message: String) {
    raylib.TraceLog(logLevel.toInt(), message)
}

// Circles
fun drawCircle(centerX: Int, centerY: Int, radius: Float, color: Color) {
    DrawCircle(centerX, centerY, radius, color.readValue());
}

// Rectangles
fun drawRectangleLines(posX: Int, posY: Int, width: Int, height: Int, color: Color) {
    DrawRectangleLines(posX, posY, width, height, color.readValue());
}

fun drawRectangle(posX: Int, posY: Int, width: Int, height: Int, color: Color) {
    DrawRectangle(posX, posY, width, height, color.readValue())
}

fun drawRectangleV(posX: Float, posY: Float, width: Float, height: Float, color: Color) {
    drawRectangle(posX.toInt(),
                  posY.toInt(),
                  width.toInt(),
                  height.toInt(),
                  color)
}

fun drawRectangleF(posX: Float, posY: Float, width: Float, height: Float, color: Color) {
    val win_w = GetScreenWidth()

    DrawRectangle((posX * win_w).toInt(),
                  (posY * win_w).toInt(),
                  (width * win_w).toInt(),
                  (height * win_w).toInt(),
                  color.readValue())
}


fun drawRectangleGradientV(posX: Int, posY: Int, width: Int, height: Int,
                           color1: Color, color2: Color) {
    DrawRectangleGradientV(posX, posY, width, height,
                           color1.readValue(), color2.readValue())
}

fun drawRectangleGradientH(posX: Int, posY: Int, width: Int, height: Int,
                           color1: Color, color2: Color) {
    DrawRectangleGradientH(posX, posY, width, height,
                           color1.readValue(), color2.readValue())
}

// Colors
fun Color(r: UByte, g: UByte, b: UByte, a: UByte): CValue<Color> = cValue {
    this.r = r
    this.g = g
    this.b = b
    this.a = a
}

fun Color(raw: UInt): CValue<Color> =
    Color(
        ((raw shr 8*0) and 0xFFu).toUByte(),
        ((raw shr 8*1) and 0xFFu).toUByte(),
        ((raw shr 8*2) and 0xFFu).toUByte(),
        ((raw shr 8*3) and 0xFFu).toUByte()
    )
// Text
// DrawText(const char *text, int posX, int posY, int fontSize, Color color);
fun drawText(text: String, posX: Int, posY: Int, fontSize: Int, color: Color) {
    DrawText(text, posX, posY, fontSize, color.readValue());
}

// Camera
class NativeCamera2D(
    initialOffsetX: Float,
    initialOffsetY: Float,
    initialTargetX: Float,
    initialTargetY: Float,
    initialRotation: Float,
    initialZoom: Float
) {

    companion object {
        const val SCROLL_SPEED = 100f
        const val FOLLOW_SPEED = 150f
        const val FOLLOW_THRESHOLD = WindowParams.HEIGHT * 1f / 3f
    }
    val cCam = nativeHeap.alloc<raylib.Camera2D>()

    @Suppress("unused")
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private val cleaner = createCleaner(cCam.ptr) { ptr -> nativeHeap.free(ptr) }

    var offsetX: Float
        get() = cCam.offset.x
        set(value) { cCam.offset.x = value }

    var offsetY: Float
        get() = WindowParams.HEIGHT - cCam.offset.y
        set(value) { cCam.offset.y = WindowParams.HEIGHT - value }

    var targetX: Float
        get() = cCam.target.x
        set(value) { cCam.target.x = value }

    var targetY: Float
        get() = WindowParams.HEIGHT - cCam.target.y
        set(value) { cCam.target.y = WindowParams.HEIGHT - value }

    var rotation: Float
        get() = cCam.rotation
        set(value) { cCam.rotation = value }

    var zoom: Float
        get() = cCam.zoom
        set(value) { cCam.zoom = value }

    init {
        this.offsetX = initialOffsetX
        this.offsetY = initialOffsetY
        this.targetX = initialTargetX
        this.targetY = initialTargetY
        this.rotation = initialRotation
        this.zoom = initialZoom
    }

    fun focus(offsetX: Float, offsetY: Float,
              targetX: Float, targetY: Float,
              dt: Float) {
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.targetY += SCROLL_SPEED * dt

        val dist = targetY - this.targetY
        if (dist > FOLLOW_THRESHOLD) {
            val dy = targetY - FOLLOW_THRESHOLD
            this.targetY += (dy - this.targetY) * FOLLOW_SPEED * dt
        }
    }

    inline fun use(block: () -> Unit) {
        beginDrawing()
        beginMode2D(cCam.readValue())
        block()
        endMode2D()
        endDrawing()
    }
}
