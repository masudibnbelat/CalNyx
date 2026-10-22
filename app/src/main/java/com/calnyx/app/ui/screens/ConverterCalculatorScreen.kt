package com.calnyx.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

// One unit. For every linear category, toBase is "how many base units
// is ONE of this unit" (Length base = metre, Weight base = gram,
// Volume base = litre, Area base = m², Speed base = m/s, Time base =
// second, Data base = byte, Pressure base = Pa, Energy base = joule,
// Power base = watt, Force base = newton, Torque base = N·m,
// Frequency base = Hz, Angle base = degree). Temperature ignores
// toBase — it has real formulas below.
private data class ConverterUnit(
    val name: String,
    val symbol: String,
    val toBase: Double
)

private data class ConverterCategory(
    val name: String,
    val units: List<ConverterUnit>,
    val defaultFrom: String,
    val defaultTo: String,
    val isTemperature: Boolean = false
)

private val converterCategories = listOf(
    ConverterCategory(
        name = "Length",
        defaultFrom = "km",
        defaultTo = "m",
        units = listOf(
            ConverterUnit("Nanometre", "nm", 1e-9),
            ConverterUnit("Micrometre", "µm", 1e-6),
            ConverterUnit("Millimetre", "mm", 0.001),
            ConverterUnit("Centimetre", "cm", 0.01),
            ConverterUnit("Metre", "m", 1.0),
            ConverterUnit("Kilometre", "km", 1000.0),
            ConverterUnit("Inch", "in", 0.0254),
            ConverterUnit("Foot", "ft", 0.3048),
            ConverterUnit("Yard", "yd", 0.9144),
            ConverterUnit("Mile", "mi", 1609.344),
            ConverterUnit("Nautical mile", "nmi", 1852.0),
            ConverterUnit("Ångström", "Å", 1e-10),
            ConverterUnit("Astronomical unit", "AU", 1.495978707e11),
            ConverterUnit("Light-year", "ly", 9.4607304725808e15),
            ConverterUnit("Parsec", "pc", 3.0856775814913673e16)
        )
    ),
    ConverterCategory(
        name = "Weight",
        defaultFrom = "kg",
        defaultTo = "g",
        units = listOf(
            ConverterUnit("Microgram", "µg", 1e-6),
            ConverterUnit("Milligram", "mg", 0.001),
            ConverterUnit("Gram", "g", 1.0),
            ConverterUnit("Kilogram", "kg", 1000.0),
            ConverterUnit("Quintal", "q", 100000.0),
            ConverterUnit("Tonne", "t", 1_000_000.0),
            ConverterUnit("Ounce", "oz", 28.349523125),
            ConverterUnit("Pound", "lb", 453.59237),
            ConverterUnit("Stone", "st", 6350.29318),
            ConverterUnit("US ton", "US ton", 907184.74),
            ConverterUnit("UK ton", "UK ton", 1016046.9),
            ConverterUnit("Carat", "ct", 0.2),
            ConverterUnit("Slug", "slug", 14593.9029),
            ConverterUnit("Seer (সের)", "seer", 933.104),
            ConverterUnit("Maund (মণ)", "maund", 37324.16)
        )
    ),
    ConverterCategory(
        name = "Volume",
        defaultFrom = "L",
        defaultTo = "ml",
        units = listOf(
            ConverterUnit("Millilitre", "ml", 0.001),
            ConverterUnit("Litre", "L", 1.0),
            ConverterUnit("Cubic centimetre", "cm³", 0.001),
            ConverterUnit("Cubic metre", "m³", 1000.0),
            ConverterUnit("Cubic inch", "in³", 0.016387064),
            ConverterUnit("Cubic foot", "ft³", 28.316846592),
            ConverterUnit("Teaspoon (US)", "tsp", 0.00492892159),
            ConverterUnit("Tablespoon (US)", "tbsp", 0.0147867648),
            ConverterUnit("Fluid ounce (US)", "fl oz", 0.0295735296),
            ConverterUnit("Fluid ounce (UK)", "fl oz (UK)", 0.0284130625),
            ConverterUnit("Cup (US)", "cup", 0.236588237),
            ConverterUnit("Pint (US)", "pt", 0.473176473),
            ConverterUnit("Quart (US)", "qt", 0.946352946),
            ConverterUnit("Gallon (US)", "gal (US)", 3.78541178),
            ConverterUnit("Gallon (UK)", "gal (UK)", 4.54609)
        )
    ),
    ConverterCategory(
        name = "Area",
        defaultFrom = "m²",
        defaultTo = "ft²",
        units = listOf(
            ConverterUnit("Square millimetre", "mm²", 1e-6),
            ConverterUnit("Square centimetre", "cm²", 1e-4),
            ConverterUnit("Square metre", "m²", 1.0),
            ConverterUnit("Square kilometre", "km²", 1e6),
            ConverterUnit("Hectare", "ha", 10000.0),
            ConverterUnit("Acre", "acre", 4046.8564224),
            ConverterUnit("Rood", "rood", 1011.7141056),
            ConverterUnit("Square foot", "ft²", 0.09290304),
            ConverterUnit("Square inch", "in²", 0.00064516),
            ConverterUnit("Square yard", "yd²", 0.83612736),
            ConverterUnit("Square mile", "mi²", 2589988.11),
            ConverterUnit("Decimal (শতাংশ)", "decimal", 40.468564),
            ConverterUnit("Katha (Dhaka)", "katha", 66.8901888),
            ConverterUnit("Bigha (Dhaka)", "bigha", 1337.803776)
        )
    ),
    ConverterCategory(
        name = "Speed",
        defaultFrom = "km/h",
        defaultTo = "m/s",
        units = listOf(
            ConverterUnit("Metres per second", "m/s", 1.0),
            ConverterUnit("Kilometres per hour", "km/h", 0.2777777778),
            ConverterUnit("Miles per hour", "mph", 0.44704),
            ConverterUnit("Feet per second", "ft/s", 0.3048),
            ConverterUnit("Knot", "kn", 0.5144444444),
            ConverterUnit("Mach (sea level)", "Mach", 340.29)
        )
    ),
    ConverterCategory(
        name = "Temperature",
        defaultFrom = "°C",
        defaultTo = "°F",
        isTemperature = true,
        units = listOf(
            ConverterUnit("Celsius", "°C", 1.0),
            ConverterUnit("Fahrenheit", "°F", 1.0),
            ConverterUnit("Kelvin", "K", 1.0),
            ConverterUnit("Rankine", "°R", 1.0)
        )
    ),
    ConverterCategory(
        name = "Time",
        defaultFrom = "h",
        defaultTo = "min",
        units = listOf(
            ConverterUnit("Millisecond", "ms", 0.001),
            ConverterUnit("Second", "s", 1.0),
            ConverterUnit("Minute", "min", 60.0),
            ConverterUnit("Hour", "h", 3600.0),
            ConverterUnit("Day", "day", 86400.0),
            ConverterUnit("Week", "wk", 604800.0),
            ConverterUnit("Month (avg)", "mo", 2629746.0),
            ConverterUnit("Year (avg)", "yr", 31556952.0),
            ConverterUnit("Decade", "decade", 315569520.0),
            ConverterUnit("Century", "century", 3155695200.0)
        )
    ),
    ConverterCategory(
        name = "Data",
        defaultFrom = "MB",
        defaultTo = "KB",
        units = listOf(
            ConverterUnit("Bit", "bit", 0.125),
            ConverterUnit("Byte", "B", 1.0),
            ConverterUnit("Kilobyte", "KB", 1000.0),
            ConverterUnit("Kibibyte", "KiB", 1024.0),
            ConverterUnit("Megabyte", "MB", 1e6),
            ConverterUnit("Mebibyte", "MiB", 1048576.0),
            ConverterUnit("Gigabyte", "GB", 1e9),
            ConverterUnit("Gibibyte", "GiB", 1073741824.0),
            ConverterUnit("Terabyte", "TB", 1e12),
            ConverterUnit("Tebibyte", "TiB", 1099511627776.0),
            ConverterUnit("Petabyte", "PB", 1e15)
        )
    ),
    ConverterCategory(
        name = "Pressure",
        defaultFrom = "atm",
        defaultTo = "psi",
        units = listOf(
            ConverterUnit("Pascal", "Pa", 1.0),
            ConverterUnit("Kilopascal", "kPa", 1000.0),
            ConverterUnit("Megapascal", "MPa", 1e6),
            ConverterUnit("Bar", "bar", 100000.0),
            ConverterUnit("Atmosphere", "atm", 101325.0),
            ConverterUnit("PSI", "psi", 6894.75729),
            ConverterUnit("Millimetre of mercury", "mmHg", 133.322387),
            ConverterUnit("Torr", "Torr", 133.322368),
            ConverterUnit("Inch of mercury", "inHg", 3386.38867)
        )
    ),
    ConverterCategory(
        name = "Energy",
        defaultFrom = "kcal",
        defaultTo = "kJ",
        units = listOf(
            ConverterUnit("Joule", "J", 1.0),
            ConverterUnit("Kilojoule", "kJ", 1000.0),
            ConverterUnit("Calorie", "cal", 4.184),
            ConverterUnit("Kilocalorie", "kcal", 4184.0),
            ConverterUnit("Watt-hour", "Wh", 3600.0),
            ConverterUnit("Kilowatt-hour", "kWh", 3600000.0),
            ConverterUnit("BTU", "BTU", 1055.05585),
            ConverterUnit("Foot-pound", "ft·lbf", 1.35581795),
            ConverterUnit("Erg", "erg", 1e-7),
            ConverterUnit("Electronvolt", "eV", 1.602176634e-19)
        )
    ),
    ConverterCategory(
        name = "Power",
        defaultFrom = "kW",
        defaultTo = "hp",
        units = listOf(
            ConverterUnit("Watt", "W", 1.0),
            ConverterUnit("Kilowatt", "kW", 1000.0),
            ConverterUnit("Megawatt", "MW", 1e6),
            ConverterUnit("Horsepower", "hp", 745.699872),
            ConverterUnit("BTU per hour", "BTU/h", 0.29307107),
            ConverterUnit("Kilocalorie per hour", "kcal/h", 1.16222222),
            ConverterUnit("Foot-pound per second", "ft·lbf/s", 1.35581795)
        )
    ),
    ConverterCategory(
        name = "Force",
        defaultFrom = "N",
        defaultTo = "lbf",
        units = listOf(
            ConverterUnit("Newton", "N", 1.0),
            ConverterUnit("Kilonewton", "kN", 1000.0),
            ConverterUnit("Dyne", "dyn", 1e-5),
            ConverterUnit("Kilogram-force", "kgf", 9.80665),
            ConverterUnit("Pound-force", "lbf", 4.448221615),
            ConverterUnit("Poundal", "pdl", 0.138254954),
            ConverterUnit("Kip", "kip", 4448.221615)
        )
    ),
    ConverterCategory(
        name = "Torque",
        defaultFrom = "N·m",
        defaultTo = "lbf·ft",
        units = listOf(
            ConverterUnit("Newton metre", "N·m", 1.0),
            ConverterUnit("Kilonewton metre", "kN·m", 1000.0),
            ConverterUnit("Pound-force foot", "lbf·ft", 1.35581795),
            ConverterUnit("Pound-force inch", "lbf·in", 0.112984829),
            ConverterUnit("Kilogram-force metre", "kgf·m", 9.80665)
        )
    ),
    ConverterCategory(
        name = "Frequency",
        defaultFrom = "Hz",
        defaultTo = "rpm",
        units = listOf(
            ConverterUnit("Hertz", "Hz", 1.0),
            ConverterUnit("Kilohertz", "kHz", 1000.0),
            ConverterUnit("Megahertz", "MHz", 1e6),
            ConverterUnit("Gigahertz", "GHz", 1e9),
            ConverterUnit("Revolutions per minute", "rpm", 0.0166666667),
            ConverterUnit("Cycles per second", "cps", 1.0)
        )
    ),
    ConverterCategory(
        name = "Angle",
        defaultFrom = "°",
        defaultTo = "rad",
        units = listOf(
            ConverterUnit("Degree", "°", 1.0),
            ConverterUnit("Radian", "rad", 57.29577951),
            ConverterUnit("Gradian", "grad", 0.9),
            ConverterUnit("Turn", "turn", 360.0),
            ConverterUnit("Arcminute", "′", 0.0166666667),
            ConverterUnit("Arcsecond", "″", 0.0002777778)
        )
    )
)

private fun toCelsius(value: Double, fromSymbol: String): Double = when (fromSymbol) {
    "°F" -> (value - 32.0) * 5.0 / 9.0
    "K" -> value - 273.15
    "°R" -> (value - 491.67) * 5.0 / 9.0
    else -> value
}

private fun fromCelsius(value: Double, toSymbol: String): Double = when (toSymbol) {
    "°F" -> value * 9.0 / 5.0 + 32.0
    "K" -> value + 273.15
    "°R" -> (value + 273.15) * 9.0 / 5.0
    else -> value
}

private fun convertValue(
    value: Double,
    category: ConverterCategory,
    from: ConverterUnit,
    to: ConverterUnit
): Double {
    if (category.isTemperature) {
        return fromCelsius(toCelsius(value, from.symbol), to.symbol)
    }
    return value * from.toBase / to.toBase
}

private fun formatConverted(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    if (value == 0.0) return "0"
    val a = abs(value)
    val raw = when {
        a >= 1e15 || a < 1e-9 -> String.format(Locale.US, "%.6e", value)
        a >= 1000.0 -> String.format(Locale.US, "%.4f", value)
        a >= 1.0 -> String.format(Locale.US, "%.6f", value)
        else -> String.format(Locale.US, "%.10f", value)
    }
    return trimFormattedNumber(raw)
}

private fun trimFormattedNumber(raw: String): String {
    val trimmed = if (raw.contains('e')) {
        val parts = raw.split('e')
        val mantissa = parts[0].trimEnd('0').trimEnd('.')
        mantissa + "e" + parts[1]
    } else {
        raw.trimEnd('0').trimEnd('.')
    }
    return if (trimmed == "-0" || trimmed.isEmpty()) "0" else trimmed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedCategoryName by remember { mutableStateOf(converterCategories.first().name) }
    val category = converterCategories.first { it.name == selectedCategoryName }
    var fromSymbol by remember { mutableStateOf(converterCategories.first().defaultFrom) }
    var toSymbol by remember { mutableStateOf(converterCategories.first().defaultTo) }
    var input by remember { mutableStateOf("1") }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var showAllUnits by remember { mutableStateOf(false) }
    var swapTurns by remember { mutableStateOf(0f) }

    val fromUnit = category.units.firstOrNull { it.symbol == fromSymbol } ?: category.units.first()
    val toUnit = category.units.firstOrNull { it.symbol == toSymbol } ?: category.units.first()

    val parsed = input.toDoubleOrNull()
    val result = parsed?.let { convertValue(it, category, fromUnit, toUnit) }

    // The result number visibly counts to its new value on every
    // change, then snaps to the exact Double at rest (no float drift).
    // Huge results (>= 1e12) skip the ticker and show instantly.
    val animatedResult by animateFloatAsState(
        targetValue = if (result != null && abs(result) < 1e12) result.toFloat() else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "resultTicker"
    )
    val resultDisplayText = when {
        result == null -> "—"
        abs(result) >= 1e12 -> formatConverted(result) + " " + toUnit.symbol
        else -> {
            val animatedValue = animatedResult.toDouble()
            val settled = abs(animatedValue - result) <= max(1e-6, abs(result) * 1e-5)
            formatConverted(if (settled) result else animatedValue) + " " + toUnit.symbol
        }
    }

    val inputFontSize = when {
        input.length <= 9 -> 40.sp
        input.length <= 13 -> 34.sp
        else -> 28.sp
    }

    // Blinking caret next to the input value
    val caretTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    // Slow drift for the aurora background blobs
    val driftTransition = rememberInfiniteTransition(label = "drift")
    val drift by driftTransition.animateFloat(
        initialValue = -26f,
        targetValue = 26f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )

    // Rotating swap icon
    val swapRotation by animateFloatAsState(
        targetValue = swapTurns,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "swapRotation"
    )

    val glassBorderBrush = Brush.linearGradient(
        listOf(
            colors.onSurface.copy(alpha = 0.16f),
            colors.onSurface.copy(alpha = 0.03f)
        )
    )

    fun swapUnits() {
        val tmp = fromSymbol
        fromSymbol = toSymbol
        toSymbol = tmp
        swapTurns += 180f
    }

    fun onKey(key: String) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val isDigitKey = key == "00" || (key.length == 1 && key[0].isDigit())
        if (isDigitKey && input.length >= 16) return
        when (key) {
            "AC" -> input = ""
            "⌫" -> input = input.dropLast(1)
            "±" -> input = if (input.startsWith("-")) input.drop(1) else "-" + input
            "." -> if (!input.contains('.')) {
                input = if (input.isEmpty() || input == "-") input + "0." else input + "."
            }

            "00" -> if (input.isNotEmpty() && input != "0" && input != "-0") input += "00"
            else -> {
                input = when (input) {
                    "0" -> key
                    "-0" -> "-" + key
                    else -> input + key
                }
            }
        }
    }

    // Theme fix (kept from v3): NavGraph's root is a Box, not a
    // Surface, so without this provider any Text without an explicit
    // colour would fall back to black. Every Text also sets its own
    // colour explicitly.
    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Aurora blobs — blurred colour fields drifting slowly
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = (70 + drift).dp, y = (-80).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.primaryContainer.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(64.dp)
            )
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-90 - drift).dp, y = (50).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.tertiaryContainer.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(64.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 14.dp)
            ) {
                AnimatedIn(delayMillis = 0) {
                    Column {
                        Text(
                            text = "Converter",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onBackground
                        )
                        Text(
                            text = "15 categories · 150+ units",
                            fontSize = 12.5.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category chips — selected chip grows + fills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    converterCategories.forEach { item ->
                        val selected = item.name == selectedCategoryName
                        val chipBg by animateColorAsState(
                            targetValue = if (selected) colors.primary
                            else colors.surfaceVariant.copy(alpha = 0.55f),
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            ),
                            label = "chipBg"
                        )
                        val chipText by animateColorAsState(
                            targetValue = if (selected) colors.onPrimary
                            else colors.onSurfaceVariant,
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            ),
                            label = "chipText"
                        )
                        val chipScale by animateFloatAsState(
                            targetValue = if (selected) 1.07f else 1f,
                            animationSpec = tween(
                                durationMillis = 220,
                                easing = FastOutSlowInEasing
                            ),
                            label = "chipScale"
                        )
                        Box(
                            modifier = Modifier
                                .scale(chipScale)
                                .clip(RoundedCornerShape(50))
                                .background(chipBg)
                                .clickable {
                                    selectedCategoryName = item.name
                                    fromSymbol = item.defaultFrom
                                    toSymbol = item.defaultTo
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Medium,
                                color = chipText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Glass display card — entrance replays per category
                key(selectedCategoryName) {
                    AnimatedIn(delayMillis = 60) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(colors.surface.copy(alpha = 0.45f))
                                .border(1.dp, glassBorderBrush, RoundedCornerShape(28.dp))
                                .padding(18.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                ConverterUnitPill(
                                    label = "FROM",
                                    unit = fromUnit,
                                    onClick = { pickerFor = "from" },
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(top = 20.dp, start = 8.dp, end = 8.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary)
                                        .clickable { swapUnits() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = "Swap units",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .rotate(swapRotation),
                                        tint = colors.onPrimary
                                    )
                                }
                                ConverterUnitPill(
                                    label = "TO",
                                    unit = toUnit,
                                    onClick = { pickerFor = "to" },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "${fromUnit.name} → ${toUnit.name}",
                                fontSize = 11.5.sp,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = input.ifEmpty { "0" },
                                    fontSize = inputFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(
                                        if (input.isEmpty()) 0.45f else 1f
                                    )
                                )
                                Text(
                                    text = "|",
                                    fontSize = 32.sp,
                                    color = colors.primary,
                                    modifier = Modifier
                                        .padding(start = 2.dp)
                                        .alpha(caretAlpha)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                color = colors.onSurface.copy(alpha = 0.08f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "RESULT",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceVariant
                                    )
                                    Text(
                                        text = resultDisplayText,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                if (result != null) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(
                                                AnnotatedString(
                                                    "${input.ifBlank { "0" }} " +
                                                            "${fromUnit.symbol} = " +
                                                            "${formatConverted(result)} " +
                                                            toUnit.symbol
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy result",
                                            modifier = Modifier.size(18.dp),
                                            tint = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Glass keypad — entrance replays per category too
                key(selectedCategoryName + "-keys") {
                    AnimatedIn(delayMillis = 140) {
                        val keyRows = listOf(
                            listOf("7", "8", "9", "⌫"),
                            listOf("4", "5", "6", "AC"),
                            listOf("1", "2", "3", "±"),
                            listOf("00", "0", ".", "SWAP")
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            keyRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { keyLabel ->
                                        when (keyLabel) {
                                            "SWAP" -> ConverterSwapKey(
                                                rotation = swapRotation,
                                                onClick = { swapUnits() },
                                                modifier = Modifier.weight(1f)
                                            )

                                            "AC" -> ConverterKey(
                                                text = "AC",
                                                containerColor = colors.primaryContainer,
                                                contentColor = colors.onPrimaryContainer,
                                                onClick = { onKey("AC") },
                                                modifier = Modifier.weight(1f)
                                            )

                                            "⌫", "±" -> ConverterKey(
                                                text = keyLabel,
                                                containerColor = colors.secondaryContainer
                                                    .copy(alpha = 0.75f),
                                                contentColor = colors.onSecondaryContainer,
                                                onClick = { onKey(keyLabel) },
                                                modifier = Modifier.weight(1f)
                                            )

                                            else -> ConverterKey(
                                                text = keyLabel,
                                                containerColor = colors.surfaceVariant
                                                    .copy(alpha = 0.55f),
                                                contentColor = colors.onSurface,
                                                onClick = { onKey(keyLabel) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Opens the all-units card-grid sheet
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface.copy(alpha = 0.45f))
                        .border(1.dp, glassBorderBrush, RoundedCornerShape(20.dp))
                        .clickable { showAllUnits = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All units",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = resultDisplayText,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "  ›",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            // Unit picker sheet — 2-column card grid with live previews
            val pickerTarget = pickerFor
            if (pickerTarget != null) {
                ModalBottomSheet(
                    onDismissRequest = { pickerFor = null },
                    sheetState = sheetState,
                    containerColor = colors.surface,
                    contentColor = colors.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "Choose unit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = category.name,
                            fontSize = 12.5.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 430.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(category.units) { unit ->
                                val selectedUnit =
                                    if (pickerTarget == "from") fromUnit else toUnit
                                val preview = if (parsed == null) {
                                    ""
                                } else if (pickerTarget == "from") {
                                    formatConverted(
                                        convertValue(parsed, category, unit, toUnit)
                                    ) + " " + toUnit.symbol
                                } else {
                                    formatConverted(
                                        convertValue(parsed, category, fromUnit, unit)
                                    ) + " " + unit.symbol
                                }
                                UnitGridCard(
                                    name = unit.name,
                                    symbol = unit.symbol,
                                    preview = preview,
                                    selected = unit.symbol == selectedUnit.symbol,
                                    onClick = {
                                        if (pickerTarget == "from") {
                                            fromSymbol = unit.symbol
                                        } else {
                                            toSymbol = unit.symbol
                                        }
                                        pickerFor = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // All-units sheet — 2-column card grid
            if (showAllUnits) {
                ModalBottomSheet(
                    onDismissRequest = { showAllUnits = false },
                    sheetState = sheetState,
                    containerColor = colors.surface,
                    contentColor = colors.onSurface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "${category.name} — all units",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface
                        )
                        Text(
                            text = "${input.ifBlank { "0" }} ${fromUnit.symbol} converted",
                            fontSize = 12.5.sp,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(category.units) { unit ->
                                AllUnitGridCard(
                                    name = unit.name,
                                    symbol = unit.symbol,
                                    valueText = if (parsed != null) {
                                        formatConverted(
                                            convertValue(
                                                parsed, category, fromUnit, unit
                                            )
                                        ) + " " + unit.symbol
                                    } else {
                                        "—"
                                    },
                                    highlighted = unit.symbol == toUnit.symbol
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Month / year use the average Gregorian length. " +
                                    "KB = 1000 bytes, KiB = 1024 bytes. " +
                                    "Katha / Bigha use the Dhaka standard " +
                                    "(720 / 14,400 sq ft).",
                            fontSize = 11.sp,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Fade + slide entrance that replays whenever the block is (re)keyed.
@Composable
private fun AnimatedIn(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 420,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "entrance"
    )
    Box(
        modifier = Modifier
            .alpha(progress)
            .offset(y = ((1f - progress) * 22f).dp)
    ) {
        content()
    }
}

@Composable
private fun ConverterKey(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "keyScale"
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) colors.primaryContainer else containerColor,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "keyBg"
    )
    Box(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(
                1.dp,
                colors.onSurface.copy(alpha = 0.07f),
                RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun ConverterSwapKey(
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "swapKeyScale"
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) colors.primaryContainer else colors.primary,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "swapKeyBg"
    )
    Box(
        modifier = modifier
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.SwapHoriz,
            contentDescription = "Swap units",
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
            tint = colors.onPrimary
        )
    }
}

@Composable
private fun UnitGridCard(
    name: String,
    symbol: String,
    preview: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) colors.primaryContainer
                else colors.surfaceVariant.copy(alpha = 0.6f)
            )
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) colors.primary
                else colors.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(13.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) colors.onPrimaryContainer
                    else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary
                    )
                }
            }
            Text(
                text = symbol,
                fontSize = 11.5.sp,
                color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.7f)
                else colors.onSurfaceVariant
            )
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.onPrimaryContainer
                    else colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun AllUnitGridCard(
    name: String,
    symbol: String,
    valueText: String,
    highlighted: Boolean
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (highlighted) colors.primaryContainer.copy(alpha = 0.55f)
                else colors.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (highlighted) colors.primary.copy(alpha = 0.5f)
                else colors.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(18.dp)
            )
            .padding(13.dp)
    ) {
        Column {
            Text(
                text = name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = symbol,
                fontSize = 11.sp,
                color = colors.onSurfaceVariant
            )
            Text(
                text = valueText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ConverterUnitPill(
    label: String,
    unit: ConverterUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceVariant.copy(alpha = 0.65f))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unit.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = unit.symbol,
                    fontSize = 11.5.sp,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Choose unit",
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant
            )
        }
    }
}
