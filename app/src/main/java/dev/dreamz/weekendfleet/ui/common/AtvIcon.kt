package dev.dreamz.weekendfleet.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Side-on quad (ATV / UTV) in the 24 dp Material grid. Material's icon set has no ATV, and
 * Agriculture reads as a tractor. Filled like the other vehicle icons; tinted by Icon().
 */
val AtvIcon: ImageVector by lazy {
    fun ImageVector.Builder.part(d: String, fill: PathFillType = PathFillType.NonZero) =
        addPath(PathParser().parsePathString(d).toNodes(), pathFillType = fill, fill = SolidColor(Color.Black))
    ImageVector.Builder("Atv", 24.dp, 24.dp, 24f, 24f).apply {
        // Wheels (ring with a hub hole) and their knobby tread.
        part("M5.6,13.7a4.3,4.3 0 1,0 0.001,0zM5.6,16.4a1.6,1.6 0 1,1 -0.001,0z", PathFillType.EvenOdd)
        part("M18.4,13.7a4.3,4.3 0 1,0 0.001,0zM18.4,16.4a1.6,1.6 0 1,1 -0.001,0z", PathFillType.EvenOdd)
        part("M9.6,18.53L10.42,18.75L10.15,19.76L9.33,19.54ZM8.8,20.46L9.4,21.06L8.66,21.8L8.06,21.2ZM7.14,21.73L7.36,22.55L6.35,22.82L6.13,22ZM5.07,22L4.85,22.82L3.84,22.55L4.06,21.73ZM3.14,21.2L2.54,21.8L1.8,21.06L2.4,20.46ZM1.87,19.54L1.05,19.76L0.78,18.75L1.6,18.53ZM1.6,17.47L0.78,17.25L1.05,16.24L1.87,16.46ZM2.4,15.54L1.8,14.94L2.54,14.2L3.14,14.8ZM4.06,14.27L3.84,13.45L4.85,13.18L5.07,14ZM6.13,14L6.35,13.18L7.36,13.45L7.14,14.27ZM8.06,14.8L8.66,14.2L9.4,14.94L8.8,15.54ZM9.33,16.46L10.15,16.24L10.42,17.25L9.6,17.47Z")
        part("M22.4,18.53L23.22,18.75L22.95,19.76L22.13,19.54ZM21.6,20.46L22.2,21.06L21.46,21.8L20.86,21.2ZM19.94,21.73L20.16,22.55L19.15,22.82L18.93,22ZM17.87,22L17.65,22.82L16.64,22.55L16.86,21.73ZM15.94,21.2L15.34,21.8L14.6,21.06L15.2,20.46ZM14.67,19.54L13.85,19.76L13.58,18.75L14.4,18.53ZM14.4,17.47L13.58,17.25L13.85,16.24L14.67,16.46ZM15.2,15.54L14.6,14.94L15.34,14.2L15.94,14.8ZM16.86,14.27L16.64,13.45L17.65,13.18L17.87,14ZM18.93,14L19.15,13.18L20.16,13.45L19.94,14.27ZM20.86,14.8L21.46,14.2L22.2,14.94L21.6,15.54ZM22.13,16.46L22.95,16.24L23.22,17.25L22.4,17.47Z")
        // Rear rack, seat and front rack, arched over the wheels.
        part("M0.4,13.2C0.4,10.6 2.4,9.6 4.4,9.6H8.4L9.9,8.2H14.1L15.6,9.6H19.6C21.6,9.6 23.6,10.6 23.6,13.2H22.1C21.9,12 20.6,11.6 18.4,11.6C16.2,11.6 14.9,12 14.7,13.2H9.3C9.1,12 7.8,11.6 5.6,11.6C3.4,11.6 2.1,12 1.9,13.2Z")
        part("M10.3,13.2H13.7V16.2H10.3Z")
        part("M14.6,9.6L16.3,5.4H19.4V6.8H17.3L16,9.6Z")
    }.build()
}
