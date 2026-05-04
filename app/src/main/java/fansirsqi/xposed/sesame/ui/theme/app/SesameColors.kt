package fansirsqi.xposed.sesame.ui.theme.app

import androidx.compose.ui.graphics.Color

object SesameColors {
    // Brand Colors
    val Primary = Color(0xFF0984E3)
    val PrimaryVariant = Color(0xFF0873C4)
    val Secondary = Color(0xFF6C5CE7) // Swapped purple to secondary
    val Accent = Color(0xFF00B894)
    
    // Background & Surface
    val Background = Color(0xFFF8F9FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF1F2F6)
    
    // Status Colors
    val Success = Color(0xFF00B894)
    val Error = Color(0xFFFF7675)
    val Warning = Color(0xFFFDCB6E)
    val Info = Color(0xFF0984E3)
    val Pending = Color(0xFFB2BEC3)
    
    // Text Colors
    val TextMain = Color(0xFF2D3436)
    val TextSecondary = Color(0xFF636E72)
    val TextTertiary = Color(0xFFB2BEC3)
    val TextDisabled = Color(0xFFDFE6E9)
    
    // HTTP Method Colors
    val MethodGet = Color(0xFF0984E3)
    val MethodPost = Color(0xFF00B894)
    val MethodPut = Color(0xFFFDCB6E)
    val MethodDelete = Color(0xFFD63031)
    val MethodOther = Color(0xFF6C5CE7)
    
    // Helper function for status colors
    fun getStatusColor(code: Int): Color = when {
        code == 0 -> Pending
        code in 200..299 -> Success
        code in 400..499 -> Error.copy(alpha = 0.8f)
        code >= 500 -> Error
        else -> Pending
    }
}
