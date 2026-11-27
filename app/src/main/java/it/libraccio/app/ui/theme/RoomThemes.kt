package it.libraccio.app.ui.theme

import androidx.compose.ui.graphics.Color

data class RoomTheme(
    val key: String,
    val label: String,
    val wall: Color,
    val wallPattern: Color,
    val wood: Color,
    val woodLight: Color,
    val woodDark: Color,
    val shelfBack: Color,
    val onWall: Color,
)

object RoomThemes {
    val all = listOf(
        RoomTheme(
            key = "STUDIO", label = "Studio in legno",
            wall = Color(0xFF4A3528), wallPattern = Color(0x14FFFFFF),
            wood = Color(0xFF6B4226), woodLight = Color(0xFF9A6A42), woodDark = Color(0xFF3F2716),
            shelfBack = Color(0xFF2E1E15), onWall = Color(0xFFF3E6D6),
        ),
        RoomTheme(
            key = "MINIMAL", label = "Minimal chiaro",
            wall = Color(0xFFEDE6DA), wallPattern = Color(0x0D000000),
            wood = Color(0xFFD9C3A0), woodLight = Color(0xFFEBDCC2), woodDark = Color(0xFFB39670),
            shelfBack = Color(0xFFD6C9B4), onWall = Color(0xFF3A2E24),
        ),
        RoomTheme(
            key = "SOFFITTA", label = "Soffitta",
            wall = Color(0xFF5C6B70), wallPattern = Color(0x12FFFFFF),
            wood = Color(0xFF7A6A55), woodLight = Color(0xFFA08E74), woodDark = Color(0xFF4F4436),
            shelfBack = Color(0xFF3A4144), onWall = Color(0xFFEFF2F0),
        ),
        RoomTheme(
            key = "GOTICA", label = "Biblioteca gotica",
            wall = Color(0xFF1F2B25), wallPattern = Color(0x10C9A86A),
            wood = Color(0xFF3E2618), woodLight = Color(0xFF6A4430), woodDark = Color(0xFF22140C),
            shelfBack = Color(0xFF140D09), onWall = Color(0xFFE9DCC0),
        ),
    )

    fun byKey(key: String): RoomTheme = all.firstOrNull { it.key == key } ?: all.first()
}
