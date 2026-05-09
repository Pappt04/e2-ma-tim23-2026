package uns.ac.rs.team23.slagalica.ui.theme

import androidx.compose.ui.graphics.Color

// —— Yolk Yellow (primary) ——
val YolkYellow50 = Color(0xFFFFF3A3)
val YolkYellow400 = Color(0xFFFFD700)
val YolkYellow600 = Color(0xFFE6B800)
val YolkYellow800 = Color(0xFFA07C00)

// —— Clown Red (secondary / accent) ——
val ClownRed50 = Color(0xFFFFE0DF)
val ClownRed500 = Color(0xFFE8382F)
val ClownRed700 = Color(0xFFA01C15)

// —— Grape Purple (brand strip / tertiary) ——
val GrapePurple50 = Color(0xFFEDE0F7)
val GrapePurple500 = Color(0xFF9B3CC4)
val GrapePurple700 = Color(0xFF62147F)

// —— Cobalt blue-gray (neutrals) ——
val Neutral50 = Color(0xFFEEF3FB)
val Neutral100 = Color(0xFFC8D8EE)
val Neutral300 = Color(0xFF8EA9CE)
val Neutral500 = Color(0xFF4D72A8)
val Neutral700 = Color(0xFF1C3D6E)
val Neutral900 = Color(0xFF0E2347)
/** Deepest app background (dark mode) */
val NeutralBgDeep = Color(0xFF02060F)
/** Cards / elevated surfaces on deep bg */
val NeutralSurfaceDark = Color(0xFF0A1424)
/** Muted panels / chips on deep bg */
val NeutralSurfaceVariantDark = Color(0xFF132338)

// —— Semantic ——
val SuccessBackground = Color(0xFFD6F0D6)
val SuccessOnContainer = Color(0xFF2E8B2E)

val ErrorBackground = Color(0xFFFFE0DF)
val ErrorOnContainer = Color(0xFFE8382F)

val WarningBackground = YolkYellow50
val WarningOnContainer = YolkYellow800

// —— Text ——
val TextPrimary = Color(0xFF0E2347)
val TextSecondary = Color(0xFF4D72A8)
val TextInverse = Neutral50

/** Readable label on solid yolk / yellow fills */
val TextOnYolk = TextPrimary
