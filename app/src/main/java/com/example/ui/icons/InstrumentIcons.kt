package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Unified 1.75px rounded-stroke instrument icon set built strictly with Compose ImageVector paths.
 * Ensures visual coherence across navigation, actions, security indicators, and settings.
 */
object InstrumentIcons {
    private const val STROKE = 1.75f
    private val BLACK = SolidColor(Color.Black)

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    private fun ImageVector.Builder.stroke(block: PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = BLACK,
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }

    val Dial: ImageVector by lazy {
        icon("Dial") {
            stroke {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
                close()
            }
            stroke {
                moveTo(12f, 7f)
                lineTo(12f, 12f)
                lineTo(15.5f, 14f)
            }
            stroke {
                moveTo(12f, 3.5f)
                lineTo(12f, 5f)
            }
        }
    }

    val Stats: ImageVector by lazy {
        icon("Stats") {
            stroke {
                moveTo(4f, 19.5f)
                lineTo(20f, 19.5f)
            }
            stroke {
                moveTo(5f, 15f)
                lineTo(9.5f, 10.5f)
                lineTo(13.5f, 13.5f)
                lineTo(19f, 7f)
            }
            stroke {
                moveTo(16f, 7f)
                lineTo(19f, 7f)
                lineTo(19f, 10f)
            }
        }
    }

    val Body: ImageVector by lazy {
        icon("Body") {
            stroke {
                moveTo(3.5f, 12f)
                lineTo(7.5f, 12f)
                lineTo(10f, 6.5f)
                lineTo(14f, 17.5f)
                lineTo(16.5f, 12f)
                lineTo(20.5f, 12f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke {
                moveTo(4f, 7f)
                lineTo(20f, 7f)
                moveTo(4f, 12f)
                lineTo(20f, 12f)
                moveTo(4f, 17f)
                lineTo(20f, 17f)
            }
            stroke {
                moveTo(9f, 5f)
                lineTo(9f, 9f)
                moveTo(15f, 10f)
                lineTo(15f, 14f)
                moveTo(10.5f, 15f)
                lineTo(10.5f, 19f)
            }
        }
    }

    val Wind: ImageVector by lazy {
        icon("Wind") {
            stroke {
                moveTo(4f, 9.5f)
                lineTo(15.5f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, false, 15.5f, 4.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 13.5f, 5.5f)
            }
            stroke {
                moveTo(3.5f, 14f)
                lineTo(18f, 14f)
                arcTo(2.25f, 2.25f, 0f, true, true, 18f, 18.5f)
                arcTo(2.25f, 2.25f, 0f, false, true, 16f, 17.5f)
            }
            stroke {
                moveTo(4f, 18f)
                lineTo(11f, 18f)
            }
        }
    }

    val CheckIn: ImageVector by lazy {
        icon("CheckIn") {
            stroke {
                moveTo(5f, 5f)
                lineTo(19f, 5f)
                lineTo(19f, 19f)
                lineTo(5f, 19f)
                close()
            }
            stroke {
                moveTo(8.5f, 12f)
                lineTo(11f, 14.5f)
                lineTo(15.8f, 9.5f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            stroke {
                moveTo(6f, 11f)
                lineTo(18f, 11f)
                lineTo(18f, 19.5f)
                lineTo(6f, 19.5f)
                close()
            }
            stroke {
                moveTo(8.5f, 11f)
                lineTo(8.5f, 8f)
                arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 8f)
                lineTo(15.5f, 11f)
            }
            stroke {
                moveTo(12f, 14.5f)
                lineTo(12f, 16.5f)
            }
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            stroke {
                moveTo(12f, 3.5f)
                lineTo(19f, 6.2f)
                lineTo(19f, 11.8f)
                curveTo(19f, 16.2f, 15.8f, 19.3f, 12f, 20.5f)
                curveTo(8.2f, 19.3f, 5f, 16.2f, 5f, 11.8f)
                lineTo(5f, 6.2f)
                close()
            }
            stroke {
                moveTo(9.2f, 12f)
                lineTo(11.1f, 13.9f)
                lineTo(15f, 9.8f)
            }
        }
    }

    val Key: ImageVector by lazy {
        icon("Key") {
            stroke {
                moveTo(8.5f, 15.5f)
                arcTo(4f, 4f, 0f, true, true, 8.51f, 15.5f)
                close()
            }
            stroke {
                moveTo(11.5f, 12.5f)
                lineTo(20f, 4f)
                moveTo(16.5f, 7.5f)
                lineTo(19f, 10f)
                moveTo(14.5f, 9.5f)
                lineTo(16.5f, 11.5f)
            }
        }
    }

    val Cloud: ImageVector by lazy {
        icon("Cloud") {
            stroke {
                moveTo(7f, 18f)
                lineTo(17.2f, 18f)
                arcTo(3.5f, 3.5f, 0f, false, false, 17.5f, 11.1f)
                arcTo(5.2f, 5.2f, 0f, false, false, 7.5f, 9.6f)
                arcTo(3.8f, 3.8f, 0f, false, false, 7f, 18f)
                close()
            }
        }
    }

    val Bell: ImageVector by lazy {
        icon("Bell") {
            stroke {
                moveTo(6.5f, 16.5f)
                lineTo(17.5f, 16.5f)
                lineTo(16.5f, 14.2f)
                lineTo(16.5f, 10.5f)
                arcTo(4.5f, 4.5f, 0f, true, false, 7.5f, 10.5f)
                lineTo(7.5f, 14.2f)
                close()
            }
            stroke {
                moveTo(10.2f, 19.5f)
                arcTo(2f, 2f, 0f, false, false, 13.8f, 19.5f)
            }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            stroke {
                moveTo(2.5f, 12f)
                curveTo(5f, 7.5f, 8.5f, 6f, 12f, 6f)
                curveTo(15.5f, 6f, 19f, 7.5f, 21.5f, 12f)
                curveTo(19f, 16.5f, 15.5f, 18f, 12f, 18f)
                curveTo(8.5f, 18f, 5f, 16.5f, 2.5f, 12f)
                close()
            }
            stroke {
                moveTo(12f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, true, true, 11.99f, 9.5f)
                close()
            }
        }
    }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            stroke {
                moveTo(3.5f, 3.5f)
                lineTo(20.5f, 20.5f)
            }
            stroke {
                moveTo(6.5f, 7.5f)
                curveTo(4.8f, 8.8f, 3.5f, 10.3f, 2.5f, 12f)
                curveTo(5f, 16.5f, 8.5f, 18f, 12f, 18f)
                curveTo(13.8f, 18f, 15.5f, 17.5f, 17f, 16.6f)
            }
            stroke {
                moveTo(10.5f, 6.2f)
                curveTo(11f, 6.1f, 11.5f, 6f, 12f, 6f)
                curveTo(15.5f, 6f, 19f, 7.5f, 21.5f, 12f)
                curveTo(20.7f, 13.4f, 19.8f, 14.5f, 18.8f, 15.4f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("Copy") {
            stroke {
                moveTo(9f, 9f)
                lineTo(19f, 9f)
                lineTo(19f, 19f)
                lineTo(9f, 19f)
                close()
            }
            stroke {
                moveTo(15f, 9f)
                lineTo(15f, 5.5f)
                lineTo(5.5f, 5.5f)
                lineTo(5.5f, 15f)
                lineTo(9f, 15f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            stroke {
                moveTo(5f, 12.5f)
                lineTo(9.8f, 17.2f)
                lineTo(19f, 7.5f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        icon("ArrowBack") {
            stroke {
                moveTo(19f, 12f)
                lineTo(5.5f, 12f)
                moveTo(11f, 6.5f)
                lineTo(5.5f, 12f)
                lineTo(11f, 17.5f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke {
                moveTo(9.5f, 6.5f)
                lineTo(15f, 12f)
                lineTo(9.5f, 17.5f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            stroke {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Alert: ImageVector by lazy {
        icon("Alert") {
            stroke {
                moveTo(12f, 4f)
                lineTo(20.5f, 19f)
                lineTo(3.5f, 19f)
                close()
            }
            stroke {
                moveTo(12f, 10f)
                lineTo(12f, 13.5f)
                moveTo(12f, 16.2f)
                lineTo(12f, 16.4f)
            }
        }
    }

    val Phone: ImageVector by lazy {
        icon("Phone") {
            stroke {
                moveTo(6.5f, 4.5f)
                lineTo(9.5f, 4.5f)
                lineTo(10.8f, 8.5f)
                lineTo(8.8f, 10f)
                curveTo(9.8f, 12.2f, 11.8f, 14.2f, 14f, 15.2f)
                lineTo(15.5f, 13.2f)
                lineTo(19.5f, 14.5f)
                lineTo(19.5f, 17.5f)
                curveTo(19.5f, 18.8f, 18.4f, 19.8f, 17f, 19.6f)
                curveTo(10.3f, 18.8f, 5.2f, 13.7f, 4.4f, 7f)
                curveTo(4.2f, 5.6f, 5.2f, 4.5f, 6.5f, 4.5f)
                close()
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            stroke {
                moveTo(4.5f, 7f)
                lineTo(19.5f, 7f)
                moveTo(9.5f, 4.5f)
                lineTo(14.5f, 4.5f)
                moveTo(6.5f, 7f)
                lineTo(7.5f, 19.5f)
                lineTo(16.5f, 19.5f)
                lineTo(17.5f, 7f)
            }
            stroke {
                moveTo(10f, 10.5f)
                lineTo(10f, 16f)
                moveTo(14f, 10.5f)
                lineTo(14f, 16f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            stroke {
                moveTo(19f, 12f)
                arcTo(7f, 7f, 0f, true, true, 16.9f, 7f)
            }
            stroke {
                moveTo(17f, 3.8f)
                lineTo(17.2f, 7.2f)
                lineTo(13.8f, 7.4f)
            }
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            stroke {
                moveTo(12f, 4.5f)
                lineTo(12f, 15f)
                moveTo(8f, 11.2f)
                lineTo(12f, 15.2f)
                lineTo(16f, 11.2f)
            }
            stroke {
                moveTo(5f, 19f)
                lineTo(19f, 19f)
            }
        }
    }

    val Fingerprint: ImageVector by lazy {
        icon("Fingerprint") {
            stroke {
                moveTo(12f, 11.5f)
                verticalLineTo(15f)
                curveTo(12f, 17f, 11.2f, 18.6f, 10f, 19.8f)
            }
            stroke {
                moveTo(9f, 11.5f)
                arcTo(3f, 3f, 0f, false, true, 15f, 11.5f)
                verticalLineTo(14.5f)
                curveTo(15f, 16.5f, 14.5f, 18.2f, 13.6f, 19.6f)
            }
            stroke {
                moveTo(6.2f, 12.2f)
                arcTo(5.8f, 5.8f, 0f, false, true, 17.8f, 11.5f)
                verticalLineTo(13.8f)
                curveTo(17.8f, 15.6f, 17.4f, 17.2f, 16.7f, 18.5f)
            }
            stroke {
                moveTo(6.5f, 15.2f)
                curveTo(6.9f, 16.7f, 7.2f, 17.8f, 6.7f, 19f)
            }
            stroke {
                moveTo(4.2f, 9.8f)
                arcTo(8.3f, 8.3f, 0f, false, true, 19.6f, 9.2f)
            }
        }
    }
}
