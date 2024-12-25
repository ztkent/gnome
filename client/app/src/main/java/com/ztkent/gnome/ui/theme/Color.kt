package com.ztkent.gnome.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


val NotificationBarColor = Color(red = 250, green = 250, blue = 250)

val BG1 = Color(0xFFF0F0F0)
val BG2 = Color(0xFFDCDCDC)

val DIVIDER_COLOR = Color(0xFFF0F0F0)
val SELECTED_TAB_COLOR = Color(0xFF33691E)

val LuxColorMoonlessOvercast = Color(0x806A6AB2) // 0.00 - 0.3, 50% opacity
val LuxColorFullMoon = Color(0x807B7BBE) // 3.4, 50% opacity
val LuxColorDarkTwilight = Color(0x809A9AD2) // 20 - 50, 50% opacity
val LuxColorLivingRoom = Color(0x80BABAE8) // 50, 50% opacity
val LuxColorOfficeHallway = Color(0x80A9C2F1) // 80, 50% opacity
val LuxColorDarkOvercast = Color(0x8097B5EC) // 100, 50% opacity
val LuxColorTrainStation = Color(0x806495ED) // 150, 50% opacity
val LuxColorOfficeLighting = Color(0x806495ED) // 320 - 500, 50% opacity
val LuxColorSunriseSunset = Color(0x806495ED) // 400, 50% opacity
val LuxColorOvercastDay = Color(0x80389EEF) // 1000, 50% opacity
val LuxColorFullDaylight = Color(0x803695E1) // 10,000 - 25,000, 50% opacity
val LuxColorDirectSunlight = Color(0xA61687E1) // 32,000 - 100,000, 50% opacity


val LuxColorMoonlessOvercastGradient = Brush.linearGradient(
    colors = listOf(LuxColorMoonlessOvercast, NotificationBarColor)
)
val LuxColorFullMoonGradient = Brush.linearGradient(
    colors = listOf(LuxColorFullMoon, NotificationBarColor)
)
val LuxColorDarkTwilightGradient = Brush.linearGradient(
    colors = listOf(LuxColorDarkTwilight, NotificationBarColor)
)
val LuxColorLivingRoomGradient = Brush.linearGradient(
    colors = listOf(LuxColorLivingRoom, NotificationBarColor)
)
val LuxColorOfficeHallwayGradient = Brush.linearGradient(
    colors = listOf(LuxColorOfficeHallway, NotificationBarColor)
)
val LuxColorDarkOvercastGradient = Brush.linearGradient(
    colors = listOf(LuxColorDarkOvercast, NotificationBarColor)
)
val LuxColorTrainStationGradient = Brush.linearGradient(
    colors = listOf(LuxColorTrainStation, NotificationBarColor)
)
val LuxColorOfficeLightingGradient = Brush.linearGradient(
    colors = listOf(LuxColorOfficeLighting, NotificationBarColor)
)
val LuxColorSunriseSunsetGradient = Brush.linearGradient(
    colors = listOf(LuxColorSunriseSunset, NotificationBarColor)
)
val LuxColorOvercastDayGradient = Brush.linearGradient(
    colors = listOf(LuxColorOvercastDay, NotificationBarColor)
)
val LuxColorFullDaylightGradient = Brush.linearGradient(
    colors = listOf(LuxColorFullDaylight, NotificationBarColor)
)
val LuxColorDirectSunlightGradient = Brush.linearGradient(
    colors = listOf(LuxColorDirectSunlight, NotificationBarColor)
)
val LuxDisabledGradient = Brush.linearGradient(
    colors = listOf(NotificationBarColor, Color.White)
)
val LuxUnknownGradient = Brush.linearGradient(
    colors = listOf(NotificationBarColor, Color.White)
)

val LuxColorMoonlessOvercast_Old = Color(0xFF3A3A72) // 0.0001 - 0.002
val LuxColorMoonlessClear_Old = Color(0xFF5A5AB2) // 0.05 - 0.3
val LuxColorFullMoon_Old = Color(0xFF5C69D3) // 3.4
val LuxColorDarkTwilight_Old = Color(0xFF6495ED) // 20 - 50
val LuxColorLivingRoom_Old = Color(0xFFADD8E6) // 50
val LuxColorOfficeHallway_Old = Color(0xFFB0E0E6) // 80
val LuxColorDarkOvercast_Old = Color(0xFFD3D3D3) // 100
val LuxColorTrainStation_Old = Color(0xFF6495ED) // 150
val LuxColorOfficeLighting_Old = Color(0xFF4682B4) // 320 - 500
val LuxColorSunriseSunset_Old = Color(0xFFFFDAB9) // 400
val LuxColorOvercastDay_Old = Color(0xFFFAEBD7) // 1000
val LuxColorFullDaylight_Old = Color(0xFFFFFFE0) // 10,000 - 25,000
val LuxColorDirectSunlight_Old = Color(0xFFFFFFF0) // 32,000 - 100,000


val VisibleLightColor = Color(0xFF0D47A1)
val InfraredLightColor = Color(0xFFB71C1C)
val FullSpectrumColor = Color(0xFF004D40)

val DLILineColor = Color(0xFF004D40)
val PPFDBarColor = Color(0xFF006064)
val LuxChartColor = Color(0xFF006064)