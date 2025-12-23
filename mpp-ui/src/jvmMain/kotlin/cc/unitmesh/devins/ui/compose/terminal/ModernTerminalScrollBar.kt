package cc.unitmesh.devins.ui.compose.terminal

import java.awt.*
import javax.swing.*
import javax.swing.plaf.basic.BasicScrollBarUI

/**
 * Color configuration for a terminal scrollbar.
 */
data class TerminalScrollbarColors(
    val track: Color,
    val thumb: Color,
    val thumbHover: Color? = null,
    val thumbPressed: Color? = null
)

/**
 * A modern, minimal scrollbar inspired by IntelliJ's JBScrollBar styling.
 * - Thin design (theme-friendly, 10px track by default)
 * - Rounded thumb with transparency
 * - Hover & press feedback
 * - Auto-hide track (only thumb visible)
 * - Dynamic theme support via color provider
 *
 * Note: This class is open to allow anonymous subclass creation like IDEA's JBScrollBar.
 * Note: colorsProvider is nullable because updateUI() is called during parent constructor
 * before field initialization.
 */
open class ModernTerminalScrollBar(
    orientation: Int,
    private val colorsProvider: (() -> TerminalScrollbarColors?)? = null
) : JScrollBar(orientation) {
    // Keep dimensions conservative: visible enough to grab, still minimal.
    private val trackThicknessPx = 10
    private val thumbMarginPx = 2

    /**
     * Secondary constructor for backward compatibility with static colors.
     */
    constructor(orientation: Int, colors: TerminalScrollbarColors?) : this(orientation, { colors })

    init {
        isOpaque = false
        putClientProperty("JComponent.sizeVariant", "mini")
        unitIncrement = 4
        blockIncrement = 48
    }

    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        return if (orientation == VERTICAL) {
            Dimension(trackThicknessPx, size.height)
        } else {
            Dimension(size.width, trackThicknessPx)
        }
    }

    override fun updateUI() {
        setUI(
            object : BasicScrollBarUI() {
                private var hovering = false
                private var pressing = false

                override fun configureScrollBarColors() {
                    // colorsProvider may be null during parent constructor call
                    colorsProvider?.invoke()?.let {
                        thumbColor = it.thumb
                        trackColor = it.track
                    }
                }

                override fun getMaximumThumbSize(): Dimension {
                    return if (scrollbar.orientation == VERTICAL) {
                        Dimension(trackThicknessPx, Int.MAX_VALUE)
                    } else {
                        Dimension(Int.MAX_VALUE, trackThicknessPx)
                    }
                }

                override fun getMinimumThumbSize(): Dimension {
                    // Give the thumb a minimum length so it's always grabbable.
                    return if (scrollbar.orientation == VERTICAL) {
                        Dimension(trackThicknessPx, 24)
                    } else {
                        Dimension(24, trackThicknessPx)
                    }
                }

                override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
                    // Don't paint track - auto-hide for cleaner look
                    // Only thumb will be visible
                }

                override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle) {
                    if (thumbBounds.isEmpty) return
                    // Get colors dynamically to support theme changes
                    // colorsProvider may be null during parent constructor call
                    val thumbColors = colorsProvider?.invoke() ?: return super.paintThumb(g, c, thumbBounds)

                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                    // Calculate thumb color with transparency
                    val baseColor =
                        when {
                            pressing && thumbColors.thumbPressed != null -> thumbColors.thumbPressed
                            hovering && thumbColors.thumbHover != null -> thumbColors.thumbHover
                            else -> thumbColors.thumb
                        }

                    // Apply transparency: more opaque on hover/press
                    val alpha =
                        when {
                            pressing -> 220
                            hovering -> 180
                            else -> 120
                        }

                    g2.color = Color(baseColor.red, baseColor.green, baseColor.blue, alpha)

                    // Draw rounded thumb inside track bounds
                    val margin = thumbMarginPx.coerceAtMost((thumbBounds.width / 3).coerceAtLeast(1))
                    val w = (thumbBounds.width - margin * 2).coerceAtLeast(2)
                    val h = (thumbBounds.height - margin * 2).coerceAtLeast(2)
                    val arc = minOf(w, 10)

                    g2.fillRoundRect(
                        thumbBounds.x + margin,
                        thumbBounds.y + margin,
                        w,
                        h,
                        arc,
                        arc
                    )
                }

                override fun createDecreaseButton(orientation: Int): JButton {
                    return createZeroSizeButton()
                }

                override fun createIncreaseButton(orientation: Int): JButton {
                    return createZeroSizeButton()
                }

                private fun createZeroSizeButton(): JButton {
                    return JButton().apply {
                        preferredSize = Dimension(0, 0)
                        minimumSize = Dimension(0, 0)
                        maximumSize = Dimension(0, 0)
                        isFocusable = false
                        isBorderPainted = false
                        isContentAreaFilled = false
                    }
                }

                override fun createTrackListener(): TrackListener {
                    val tl = super.createTrackListener()
                    return object : TrackListener() {
                        override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                            hovering = true
                            scrollbar.repaint()
                            tl.mouseEntered(e)
                        }

                        override fun mouseExited(e: java.awt.event.MouseEvent?) {
                            hovering = false
                            pressing = false
                            scrollbar.repaint()
                            tl.mouseExited(e)
                        }

                        override fun mousePressed(e: java.awt.event.MouseEvent?) {
                            pressing = true
                            scrollbar.repaint()
                            tl.mousePressed(e)
                        }

                        override fun mouseReleased(e: java.awt.event.MouseEvent?) {
                            pressing = false
                            scrollbar.repaint()
                            tl.mouseReleased(e)
                        }
                    }
                }
            }
        )
    }
}
