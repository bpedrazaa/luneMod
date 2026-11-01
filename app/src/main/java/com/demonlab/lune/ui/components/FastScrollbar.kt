package com.demonlab.lune.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.demonlab.lune.ui.theme.getControlsPrimaryColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun <T> FastScrollbar(
    listState: LazyListState,
    items: List<T>,
    modifier: Modifier = Modifier,
    headerItemCount: Int = 0,
    itemKeyOrLetter: (T) -> String,
    hasBlurBackground: Boolean = false,
    useCustomControlsColor: Boolean = false,
    controlsColorPalette: Int = 0,
    thumbColor: Color = if (useCustomControlsColor) {
        getControlsPrimaryColor(useCustomControlsColor, controlsColorPalette).copy(alpha = 0.85f)
    } else if (hasBlurBackground) {
        Color.White.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.primary
    },
    bubbleColor: Color = if (useCustomControlsColor) {
        getControlsPrimaryColor(useCustomControlsColor, controlsColorPalette)
    } else if (hasBlurBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primaryContainer
    },
    bubbleTextColor: Color = if (useCustomControlsColor) {
        Color.White
    } else if (hasBlurBackground) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
) {
    FastScrollbarCore(
        items = items,
        headerItemCount = headerItemCount,
        itemKeyOrLetter = itemKeyOrLetter,
        firstVisibleIndex = { listState.firstVisibleItemIndex },
        firstVisibleOffset = { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset ?: 0 },
        firstVisibleItemSize = { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0 },
        headerBottomPx = {
            if (headerItemCount > 0) {
                val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == headerItemCount - 1 }
                if (header != null) (header.offset + header.size).toFloat().coerceAtLeast(0f) else 0f
            } else 0f
        },
        scrollToItem = { index -> listState.scrollToItem(index) },
        modifier = modifier,
        hasBlurBackground = hasBlurBackground,
        thumbColor = thumbColor,
        bubbleColor = bubbleColor,
        bubbleTextColor = bubbleTextColor
    )
}

@Composable
fun <T> GridFastScrollbar(
    gridState: LazyGridState,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemKeyOrLetter: (T) -> String,
    hasBlurBackground: Boolean = false,
    useCustomControlsColor: Boolean = false,
    controlsColorPalette: Int = 0,
    thumbColor: Color = if (useCustomControlsColor) {
        getControlsPrimaryColor(useCustomControlsColor, controlsColorPalette).copy(alpha = 0.85f)
    } else if (hasBlurBackground) {
        Color.White.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.primary
    },
    bubbleColor: Color = if (useCustomControlsColor) {
        getControlsPrimaryColor(useCustomControlsColor, controlsColorPalette)
    } else if (hasBlurBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primaryContainer
    },
    bubbleTextColor: Color = if (useCustomControlsColor) {
        Color.White
    } else if (hasBlurBackground) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
) {
    FastScrollbarCore(
        items = items,
        headerItemCount = 0,
        itemKeyOrLetter = itemKeyOrLetter,
        firstVisibleIndex = { gridState.firstVisibleItemIndex },
        firstVisibleOffset = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0 },
        firstVisibleItemSize = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0 },
        headerBottomPx = { 0f },
        scrollToItem = { index -> gridState.scrollToItem(index) },
        modifier = modifier,
        hasBlurBackground = hasBlurBackground,
        thumbColor = thumbColor,
        bubbleColor = bubbleColor,
        bubbleTextColor = bubbleTextColor
    )
}

@Composable
private fun <T> FastScrollbarCore(
    items: List<T>,
    headerItemCount: Int,
    itemKeyOrLetter: (T) -> String,
    firstVisibleIndex: () -> Int,
    firstVisibleOffset: () -> Int,
    firstVisibleItemSize: () -> Int,
    headerBottomPx: () -> Float,
    scrollToItem: suspend (Int) -> Unit,
    modifier: Modifier,
    hasBlurBackground: Boolean,
    thumbColor: Color,
    bubbleColor: Color,
    bubbleTextColor: Color
) {
    if (items.size <= 15) return

    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragThumbY by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var currentLetter by remember { mutableStateOf("") }
    var frozenTopPx by remember { mutableFloatStateOf(0f) }

    val thumbHeightDp = 48.dp
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    val currentItems by rememberUpdatedState(items)
    val currentHeaderCount by rememberUpdatedState(headerItemCount)
    val currentItemKeyOrLetter by rememberUpdatedState(itemKeyOrLetter)
    val currentFirstVisibleIndex by rememberUpdatedState(firstVisibleIndex)
    val currentFirstVisibleOffset by rememberUpdatedState(firstVisibleOffset)
    val currentFirstVisibleItemSize by rememberUpdatedState(firstVisibleItemSize)
    val currentHeaderBottomPx by rememberUpdatedState(headerBottomPx)
    val currentScrollToItem by rememberUpdatedState(scrollToItem)

    val idleHeaderBottom = if (headerItemCount > 0) currentHeaderBottomPx() else 0f

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = tween(durationMillis = 150),
        label = "thumbWidth"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.4f,
        animationSpec = tween(durationMillis = 150),
        label = "thumbAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onSizeChanged { size -> containerHeight = size.height.toFloat() }
            .pointerInput(Unit) {
                var scrollJob: Job? = null
                var lastTargetIndex = -1

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()

                    frozenTopPx = if (currentHeaderCount > 0) currentHeaderBottomPx() else 0f
                    isDragging = true

                    fun updatePosition(y: Float) {
                        val totalItems = currentItems.size
                        if (totalItems <= 1) return
                        val availableHeight = (containerHeight - frozenTopPx - thumbHeightPx).coerceAtLeast(0f)
                        val targetThumbY = (y - thumbHeightPx / 2f).coerceIn(frozenTopPx, frozenTopPx + availableHeight)
                        dragThumbY = targetThumbY
                        val dragProgress = if (availableHeight > 0f) {
                            ((targetThumbY - frozenTopPx) / availableHeight).coerceIn(0f, 1f)
                        } else 0f
                        val targetItemOffset = (dragProgress * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1)
                        val targetIndex = if (dragProgress == 0f && currentHeaderCount > 0) 0 else currentHeaderCount + targetItemOffset

                        val actualItemIndex = targetIndex - currentHeaderCount
                        val newLetter = if (actualItemIndex in currentItems.indices) {
                            val str = currentItemKeyOrLetter(currentItems[actualItemIndex]).trim()
                            if (str.isNotEmpty()) str.take(1).uppercase() else ""
                        } else ""

                        if (newLetter != currentLetter) {
                            currentLetter = newLetter
                            if (newLetter.isNotBlank()) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        if (targetIndex != lastTargetIndex) {
                            lastTargetIndex = targetIndex
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                currentScrollToItem(targetIndex)
                            }
                        }
                    }

                    updatePosition(down.position.y)

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                updatePosition(change.position.y)
                            } else {
                                break
                            }
                        }
                    } finally {
                        isDragging = false
                        lastTargetIndex = -1
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val thumbY = if (isDragging) {
                        dragThumbY
                    } else {
                        val availableHeight = (containerHeight - idleHeaderBottom - thumbHeightPx).coerceAtLeast(0f)
                        val firstVisible = (currentFirstVisibleIndex() - headerItemCount).coerceAtLeast(0)
                        val itemSize = currentFirstVisibleItemSize()
                        val offsetFraction = if (itemSize > 0) {
                            (-currentFirstVisibleOffset().toFloat() / itemSize.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val progress = if (items.size > 1) {
                            ((firstVisible + offsetFraction) / (items.size - 1).toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        idleHeaderBottom + (progress * availableHeight)
                    }
                    IntOffset(-4.dp.roundToPx(), thumbY.roundToInt())
                }
                .width(thumbWidth)
                .height(thumbHeightDp)
                .clip(RoundedCornerShape(percent = 50))
                .background(thumbColor.copy(alpha = thumbAlpha))
                .then(
                    if (hasBlurBackground && isDragging) {
                        Modifier.border(
                            1.dp,
                            Color.White.copy(alpha = 0.35f),
                            RoundedCornerShape(percent = 50)
                        )
                    } else {
                        Modifier
                    }
                )
        )

        AnimatedVisibility(
            visible = isDragging && currentLetter.isNotEmpty(),
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 0.5f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 0.5f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        -56.dp.roundToPx(),
                        (dragThumbY - 12.dp.toPx()).roundToInt()
                    )
                }
        ) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = bubbleColor,
                shadowElevation = if (hasBlurBackground) 4.dp else 8.dp,
                border = if (hasBlurBackground) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null,
                modifier = Modifier.width(48.dp).height(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = currentLetter,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = bubbleTextColor
                    )
                }
            }
        }
    }
}
