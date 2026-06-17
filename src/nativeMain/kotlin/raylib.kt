@file:OptIn(ExperimentalForeignApi::class)
package raylib.wrapper

import kotlinx.cinterop.*
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

fun getTime()                                  = GetTime()
fun getFrameTime()                             = GetFrameTime()


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
