// % -BASED RESPONSIVE V14 - AYOTAKAR KEY-ER RADIUS BESI (2026-11-07) - REPLACE NormalCalculatorScreen.kt.
//  V13-er ayotakar key thik chilo, kintu radius kom chilo.
//  V14: radius 20% -> 35% (key-er choto dik-er) - corner aro
//  gol dekhabe, tobuo ayotakar thakbe. Aro besi chaile (pill
//  shape) 50% kora jabe - janale kore dibo.
//  V12-e key BORGAkar (square) hoye gechilo - user thik
//  boleche, se AYOTAKAR cheyechilo, borgakar na. KARON: key-er
//  Box chilo size(min) - cell-er choto dik-er soman square.
//  V13: ayotakar mode-e key Box fillMaxSize() - cell-er PURO
//  width x height vore jabe (cell emnitei ayotakar: width >
//  height), tai key sotti ayotakar hobe, radius = choto dik-er
//  20%. Gol mode-e (full screen) ager motoi size(min) +
//  CircleShape. Key-er size, gap, font, logic - sob ager motoi.
//  FLOATING window-e (screenshot 16:11) gol key-er majhe fanka
//  dekha jay. V12: choto window-e (mini ba small - floating /
//  split / mini) key AR GOL NA - AYOTAKAR, kintu border radius
//  ache (radius = key-er 20%), tai fanka tuku vore jay, keypad
//  ekta solid grid-er moto dekhay. Full screen-e ager motoi
//  GOL thakbe. Shape sudhu % : radius key size-er 20%.
//  Key-er size, gap, font, logic - sob ager motoi.
//  V10-e caret height hisab-e toPx()/sp/toDp() chilo, ar font
//  choto howar step-ta chilo fixed 2sp - user thik boleche,
//  sob jaygay % hote hobe. V11: (1) font choto howar step ekhon
//  minDim-er 0.55% (2sp/360dp - 360dp phone-e ager motoi,
//  boro/choto screen-e scale korbe); (2) caret height ekhon
//  sudhu % : font minDim-er koto % (21.7%) * 1.2 (line-height
//  ratio, kono size na) - floor 14.4% - kono toPx/sp/toDp
//  hisab nei. Draw-er vitore px-e convert korte hoy (draw API
//  px-ei kaj kore), kintu size-ta % thekei asche. V10-er
//  min-height + bottom-anchor + blink - eki ache.
//  V9-er poreo khali caret CHOTO dekhacchilo (user, screenshot
//  16:03, mepe dekha: khali caret ~161px, "6" likhe ~298px -
//  pray DOUBLE). KARON: khali Text-er height type kora Text-er
//  cheye choto hoy (~54dp), ar caret Text-er bhitore aka hoy -
//  scroll Row caret-er uporer ongsho CLIP kore dito, tai khali
//  caret kete choto dekhacchilo. FIX: Text-er MIN HEIGHT =
//  caret height (font*1.2) - khali Text-o ekhon caret-er
//  soman lomba hobe, caret ar kokhono clip hobe na. Caret
//  height ekhon ekta jayga theke (caretHeightDp) - Text-er
//  min height ar draw - dujonei eki value. Bottom-anchor
//  (V9), blink on/off (V8), gol matha, halo - eki ache.
//  V8-er poreo ekta parthokko chilo (user, screenshot 15:56
//  vs 15:57, mepe dekha): caret-er X eki (900), kintu Y
//  alada - khali 440, "69" likhe 360 (0-1000 scale); Ans
//  theke durotto khali 297, type kore 353. KARON: khali
//  text-er height type kora text-er cheye choto hoy, ar caret
//  Text-er MAJHKHANE (center) bosano chilo - tai khali caret
//  niche (Ans-er kache) ar type kora caret upore sore jeto.
//  FIX: caret ekhon Text-er NICHE anchor kora (caretTop =
//  size.height - caretHeight) - caret-er NICHE-r kinara sob
//  somoy Text-er niche thakbe, ar Text niche-aligned, tai
//  Ans theke durotto khali ba type kora - sob somoy EKI.
//  Height (V8, font theke), blink on/off, gol matha, halo -
//  eki ache.
//  V6-er caret-eo duita asol parthokko theke gechilo (user,
//  screenshot 15:50): (1) HEIGHT: caretHeight lineHeight-er
//  upor nirbhor korto - khali text-e lineCount/lineHeight
//  alada hoy, tai khali caret CHOTO dekhacchilo. Ekhon height
//  sudhu font size theke (font*1.2) - khali ba type kora,
//  eki font-e eki height. (2) THICKNESS/LOOK: V6-er fade blink
//  (1 -> 0.15) majhe caret-ke halka/patla dekhacchilo, ar
//  typing pulse (1.6x) type korar somoy caret-ke MOTA
//  banacchilo - tai dui obosthay alada lagto. Ekhon pulse
//  tule deoa, blink clean on/off (600ms dekha, 400ms lukano,
//  120ms fade) - jokhon dekha jay, sob somoy eki size, eki
//  rong. POSITION (V6): Text full width + khali cx = line-er
//  dan kinara - eki ache, tai khali/type kora eki jaygay.
//  COPY BUTTON KATA (user, screenshot 15:45): '=' chaple final
//  answer (86sp, ~107dp) + Copy button (~40dp) = ~147dp, kintu
//  display-er Box chilo matro ~134dp - karon preview Box khali
//  thakleo 6.5% height (49dp) kheye nito. Tai Copy kete jeto.
//  FIX: final thakle preview Box-er height 0% - khali jayga
//  ar nebe na, Box ~183dp hobe, Copy + answer puropuri dekhabe.
//  FIRST ROW TOP GAP: prothom row-er upore halka ektu jayga
//  (height-er 0.8%, ~6dp) - khub beshi na, nahole kharap dekhay.
//  displayH ar landscape row height theke oi 0.8% bad deoa -
//  tai sob hisab ager motoi milbe, sob % -ei.
//  V5 thik chilo, kintu button ektu niche namate hobe ar display
//  ektu boro korte hobe, gap-o ektu komate hobe (user, screenshot
//  15:36). V6: vertical gap 0.8% -> 0.6% height, keyCell cap
//  height-er 12% -> 10.2% - display ~29dp boro hobe, button
//  29dp niche nambe, size 81dp -> 77.5dp (sob soman-i thakbe).
//  CARET FIX: khali thakle caret ek jaygay, type korle arek
//  jaygay thakto - karon Text choto (min 56dp) chilo ar khali
//  text-er cx chilo 0f (bam dik). Ekhon Text min width =
//  display width (screenW - 3*padDispH) - sob somoy full
//  width, ar khali thakle cx = line-er dan kinara
//  (lr.size.width) - tai khali ba type kora - caret eki
//  jaygay thakbe. CARET BEAUTY: smooth blink (fade 1 -> 0.15,
//  600ms), gol matha (rounded), type korle halka pulse
//  (1.6x -> 1x, 250ms) ar halka halo - dekhte sundor lagbe.
//  V4 BHUL CHILO NA, kintu BESHI BORO hoye gechilo: gap 0% koray
//  circle gulo gaye gaye lege giyechilo, dekhte bhirbhar lagchilo.
//  V5: CHOTO % gap firiye deoa - horizontal 2% width, vertical
//  0.8% height, side padding 2% width. Sob key EKHONO ekdom
//  soman, hisab diye: cell width = (width - 2*side - 3*gap)/4,
//  row height = cell, top row height = cell, pager height =
//  cell*5 + 4*vertical gap, display height = baki height.
//  Tai circle gulo ektu choto hobe, majhe halka faka thakbe,
//  kintu kono key konotar cheye boro/choto hobe na.
//  Text kata fix (V4) ar glyph size (top 37, Ans 28, AC 40%) -
//  eki ache.
//  Screenshot (2026-11-07 15:02): top row (√ ^ E Ans) ar AC row-er majhe
//  kono faka chilo na, ar sobcheye niche onek faka chilo. Karon:
//  top row ar keypad-er majhe kono Spacer chilo na, ar display
//  weight 0.55 chilo - display boro, keypad choto, button choto.
//  V3: (1) top row ar keypad-er majhe Spacer = height-er 2%,
//  (2) display weight 0.55 -> 0.40 (keypad boro, button boro, niche
//  nambe, nicher faka vorbhe), (3) side padding 3% -> 2% width
//  (cell choura, button boro) + gap boro (H 2.2% width, V 1% height),
//  (4) dots-er padding kom (0.4% height) - dots last row-er kache.
//  Sob % -ei, kono fixed dp/sp layout size nei.
//  V2 fix: ager % version LocalConfiguration (DEVICE screen) diye swipe/
//  top-spacer % nito - multi-window-e window choto holeo device boro thake,
//  tai floating/mini/split-e % bhul hoto. Ekhon SOB size outer
//  BoxWithConstraints-er WINDOW size (maxWidth/maxHeight) theke % neoa -
//  window resize korlei % abar hisab hobe, design vangbe na.
//  margin / padding / width / height / size - SOB % -e.
//  Kono fixed dp/sp size diye layout atke rakha hoyni, tai jekono
//  screen-e (choto, boro, landscape, portrait) size / shape / design
//  nosto hobe na:
//   - width %  = BoxWithConstraints maxWidth * fraction (screenW * 0.02f = 2% width)
//   - height % = BoxWithConstraints maxHeight * fraction (screenH * 0.065f = 6.5% height)
//   - font %   = min(screenW, screenH) * fraction .sp (choto dik-er %,
//                tai landscape-e font fete jabe na, portrait-e choto hobe na)
//   - key font % = nijer cell-er % (cell size * 0.46f), tai key joto boro
//                hok, glyph tar sathe proportionally boro/choto hobe,
//                circle kokhono oval hobe na.
//   - gap / padding / corner / dot / caret / handle - sob uporer % variable.
//  Logic ager file-er motoi 100% same: swipe, drawer, History pager,
//  Ans, E, selection, BlackHole secret, AppLock, pull-down scientific,
//  landscape split, selection overlay - kichu bodlayni, shudhu size % -e.
// FILE: app/src/main/java/com/calnyx/app/ui/screens/NormalCalculatorScreen.kt
// REPLACE the whole file.
// DECOY BACK FIX (2026-11-07): Back on the decoy calculator used to
// finish into the blocked app underneath, so the Service blocked it
// again and the calculator came back forever. Now Back on a decoy
// (pending block exists) clears the block and goes Home, and after
// a successful unlock the decoy finishes itself, so Back inside
// the unlocked app goes Home instead of back to the calculator.
package com.calnyx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.Intent
import android.app.Activity
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.blackhole.BlackHolePrefs
import com.calnyx.app.viewmodel.CalculatorViewModel
import com.calnyx.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalCalculatorScreen(
        darkTheme: Boolean,
        onToggleTheme: () -> Unit,
        onOpenHistory: () -> Unit,
        onHistoryVisibilityChange: (Boolean) -> Unit = {},
        onOpenBlackHole: () -> Unit = {},
        onOpenBlackHoleSetup: () -> Unit = {},
        onOpenDrawer: () -> Unit = {},
        viewModel: CalculatorViewModel = viewModel(),
        historyViewModel: HistoryViewModel = viewModel()
) {
        val context = LocalContext.current
        // Window size ekhane na - niche outer BoxWithConstraints theke asbe
        // (multi-window aware). LocalConfiguration ekhane use kora hoy na.
        val expression by viewModel.expression.collectAsState()
        val cursorPosition by viewModel.cursorPosition.collectAsState()
        val result by viewModel.result.collectAsState()
        val finalResult by viewModel.finalResult.collectAsState()
        val history by historyViewModel.history.collectAsState()
        var scientificExpanded by remember { mutableStateOf(false) }
        var pullAccum by remember { mutableFloatStateOf(0f) }
        val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        val statusBg = MaterialTheme.colorScheme.background
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val bg = statusBg.toArgb()
                window.statusBarColor = bg
                window.navigationBarColor = bg
                val controller = WindowInsetsControllerCompat(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
        // DECOY BACK (2026-11-07): when this calculator is showing
        // as the decoy for a blocked app, Back must NOT finish into
        // the blocked app - that is the loop in the user's video:
        // Back -> blocked app comes forward -> Service blocks it
        // again -> calculator again, forever. Instead Back closes
        // BOTH: forget the blocked app and go Home. The blocked
        // app stays only in Recents (Android does not let one app
        // force-close another); tapping it again blocks it again.
        // Enabled only while a block is pending, so normal Back
        // (no block) behaves exactly as before. ON_RESUME refresh:
        // the decoy is launched with SINGLE_TOP, so a reused
        // activity does not always recompose - resume re-reads it.
        var isDecoy by remember { mutableStateOf(AppLockManager.getPending(context) != null) }
        val decoyLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(decoyLifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                                isDecoy = AppLockManager.getPending(context) != null
                        }
                }
                decoyLifecycleOwner.lifecycle.addObserver(observer)
                onDispose { decoyLifecycleOwner.lifecycle.removeObserver(observer) }
        }
        BackHandler(enabled = isDecoy) {
                AppLockManager.clearPending(context)
                AppLockManager.clearDecoyLaunch(context)
                try {
                        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(homeIntent)
                } catch (_: Exception) {
                }
                (context as? Activity)?.finish()
        }
        var selActive by remember { mutableStateOf(false) }
        var selStart by remember { mutableIntStateOf(0) }
        var selEnd by remember { mutableIntStateOf(0) }
        fun openBlackHoleSetupIfNeeded() {
                onOpenBlackHoleSetup()
        }
        fun handleKeyLabel(label: String) {
                if (label == "=" && BlackHolePrefs.matches(context, expression.trim())) {
                        viewModel.onButtonClick("AC")
                        val pending = AppLockManager.getPending(context)
                        if (pending != null && AppLockManager.isEnabled(context) && AppLockManager.isLocked(context, pending)) {
                                AppLockManager.setUnlockedSession(context, pending)
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(pending)
                                if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                                context.startActivity(launchIntent)
                                                AppLockManager.clearPending(context)
                                                // DECOY DONE (2026-11-07): the locked app is open
                                                // in its own task now. Finish this decoy
                                                // calculator, so Back inside the unlocked app
                                                // goes Home instead of bringing the calculator
                                                // back. Clearing pending only after a
                                                // successful launch is also the privacy fix:
                                                // a failed launch must not lose the pending app.
                                                (context as? Activity)?.finish()
                                        } catch (_: Exception) {
                                                AppLockManager.clearUnlockedSession(context)
                                        }
                                } else {
                                        AppLockManager.clearPending(context)
                                        AppLockManager.clearUnlockedSession(context)
                                        onOpenBlackHole()
                                }
                        } else {
                                onOpenBlackHole()
                        }
                        return
                }
                if (selActive && selEnd > selStart) {
                        val s = selStart
                        val e = selEnd
                        selActive = false
                        if (label == "⌫") {
                                viewModel.deleteRange(s, e)
                                return
                        }
                        if (label != "=") {
                                viewModel.deleteRange(s, e)
                        }
                } else if (selActive) {
                        selActive = false
                }
                viewModel.onButtonClick(label)
        }
        LaunchedEffect(expression) {
                if (selActive && (selStart > expression.length || selEnd > expression.length)) {
                        selActive = false
                }
        }
        LaunchedEffect(finalResult) { if (finalResult != null) selActive = false }
        LaunchedEffect(Unit) { onHistoryVisibilityChange(false) }
        val keyFontScale = 1f
        val groupedHistory = remember(history) { history.groupBy { dayLabel(it.createdAt) } }
        val groupedEntries = remember(groupedHistory) { groupedHistory.entries.toList() }
        // MULTI-WINDOW ROOT: BoxWithConstraints window-er asol size dey -
        // split-screen-e half height, floating-e choto window, mini-te aro
        // choto - sob khetrei etai sotti size. pointerInput(windowW, windowH)
        // key deoa, tai window resize (split drag) korle threshold abar
        // window-er % diye hisab hobe.
        BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
                val windowW = maxWidth
                val windowH = maxHeight
                val isMiniWindow = windowW < 320.dp || windowH < 480.dp
                val isSmallWindow = windowW < 360.dp || windowH < 620.dp
                val isCompactWindow = isMiniWindow || isSmallWindow // V12: floating/choto window-e key ayotakar
                val isVeryShort = windowH < 420.dp
                val swipeThresholdPct = windowW * 0.066f // width-er 6.6%
                val pullThresholdPct = windowH * 0.046f // height-er 4.6%
                val topSpacerHeight = when {
                        isMiniWindow -> windowH * 0.02f // mini-te 2% height
                        isSmallWindow -> windowH * 0.04f // small-e 4%
                        else -> windowH * 0.065f // normal 6.5%
                }
                Box(
                modifier =
                        Modifier.fillMaxSize()
                                .pointerInput(windowW, windowH) {
                                        var totalDx = 0f
                                        var done = false
                                        detectHorizontalDragGestures(
                                                onDragStart = { _ ->
                                                        totalDx = 0f
                                                        done = false
                                                },
                                                onDragEnd = {
                                                        totalDx = 0f
                                                        done = false
                                                },
                                                onDragCancel = {
                                                        totalDx = 0f
                                                        done = false
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                        if (done) return@detectHorizontalDragGestures
                                                        totalDx += dragAmount
                                                        // Threshold % -e: width-er 6.6%.
                                                        val threshold = swipeThresholdPct.toPx()
                                                        if (kotlin.math.abs(totalDx) > threshold) {
                                                                done = true
                                                                if (selActive && selEnd > selStart) {
                                                                } else if (totalDx > 0) {
                                                                        if (pagerState.currentPage == 1) {
                                                                                scope.launch {
                                                                                        pagerState.animateScrollToPage(0)
                                                                                }
                                                                        }
                                                                } else {
                                                                        if (pagerState.currentPage == 1) {
                                                                                onOpenDrawer()
                                                                        } else {
                                                                                scope.launch {
                                                                                        pagerState.animateScrollToPage(1)
                                                                                }
                                                                        }
                                                                }
                                                                change.consume()
                                                        }
                                                }
                                        )
                                }
        ) {
                Column(
                        modifier =
                                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                // Side-by-side: landscape, ba khub choto height + choura window
                                // (floating wide / landscape split) - display dan-e, keypad bam-e.
                                val isLandscape = maxWidth > maxHeight
                                // ---- % SIZE SYSTEM: ekhan theke sob % ----
                                val screenW = maxWidth
                                val screenH = maxHeight
                                val minDim = minOf(screenW, screenH)
                                // Compact font scale: khub choto window-e font-er % aro 15% kom,
                                // nahole choto window-e boro font overflow kore display vangto.
                                val compactFontScale = when {
                                        minDim < 300.dp -> 0.85f
                                        minDim < 340.dp -> 0.92f
                                        else -> 1f
                                }
                                // SMALL GAPS (V5): horizontal 2% width, side padding 2%,
                                // vertical gap 0.8% height - age define korte hobe, karon
                                // niche cell/display hisabe egulo lagbe.
                                val gapKeyH = screenW * 0.02f
                                val padKeyH = screenW * 0.02f
                                val gapKeyV = screenH * 0.006f
                                // First row-er uporer halka faka (V7): height-er 0.8%.
                                val topGapH = screenH * 0.008f
                                // EQUAL CELL (V5): cell width = side padding + 3 gap
                                // bad diye 4 bhag. Height-er 12% diye cap - display-er
                                // jonno 28% height thakbe (mini/split-eo chapa porbe na).
                                // dotsH = dots row-er mot height (height-er 2.5%).
                                val dotsH = screenH * 0.025f
                                val cellW = (screenW - padKeyH * 2f - gapKeyH * 3f) / 4f
                                val keyCell = minOf(cellW, screenH * 0.102f)
                                // Display height = baki height (cell*6 + 5 vertical gap + dots bad diye).
                                val displayH = screenH - keyCell * 6f - gapKeyV * 5f - dotsH - topGapH
                                val padDispH = if (isMiniWindow) screenW * 0.03f else screenW * 0.05f
                                val padHistH = screenW * 0.05f // 20dp
                                val padSmallW = screenW * 0.015f // 6dp
                                val padTinyW = screenW * 0.01f // 4dp
                                val padStartW = screenW * 0.02f // 8dp
                                // Height %: gap/spacer - mini/veryShort-e preview + spacer choto,
                                // khub choto height-e (H<380dp) preview pray lukano, keypad bachbe.
                                val previewH = when {
                                        screenH < 380.dp -> screenH * 0.001f
                                        isMiniWindow -> screenH * 0.04f
                                        else -> screenH * 0.065f
                                }
                                val bottomSpacerH = if (isMiniWindow) screenH * 0.01f else screenH * 0.028f
                                val dotPadBottomH = screenH * 0f
                                val histPadV = screenH * 0.012f // 10dp ~ 1.2% height
                                // Corner radius %: width-er 7% (28dp)
                                val cornerPct = screenW * 0.07f
                                // Font %: choto dik-er % (minDim)
                                // portrait: 78sp / 360 = 21.7%, landscape: 60sp / 360 = 15%
                                val expressionFontSize = if (isLandscape) (minDim.value * 0.15f * compactFontScale).sp else (minDim.value * 0.217f * compactFontScale).sp
                                val finalFontSize = if (isLandscape) (minDim.value * 0.178f * compactFontScale).sp else (minDim.value * 0.239f * compactFontScale).sp
                                val previewFontSize = if (isLandscape) (minDim.value * 0.078f * compactFontScale).sp else (minDim.value * 0.10f * compactFontScale).sp
                                val exprFloor = if (isLandscape) minDim.value * 0.111f else minDim.value * 0.144f
                                val finalFloor = if (isLandscape) minDim.value * 0.111f else minDim.value * 0.144f
                                // Caret / handle %: width-er %
                                val caretWidthPct = screenW * 0.006f // 2.5dp
                                val caretOffsetPct = screenW * 0.003f // 1.25dp
                                val handleSizePct = minDim * 0.055f // 20dp
                                val handleOffsetPct = minDim * 0.027f // 10dp
                                // Top row height = keyCell (portrait) ba soman 6 row
                                // (landscape: (height-dots)/6 - top + 5 keypad row sob soman).
                                val topRowHeight = if (isLandscape) (screenH - dotsH - gapKeyV * 5f - topGapH) / 6f else keyCell
                                val panelClip = if (isLandscape) RoundedCornerShape(bottomEnd = cornerPct)
                                        else RoundedCornerShape(bottomStart = cornerPct, bottomEnd = cornerPct)
                                Column(modifier = Modifier.fillMaxSize()) {
val displayPanel: @Composable (Modifier) -> Unit = { panelModifier ->
                                Box(modifier = panelModifier) {
Column(
                                                        modifier = Modifier.fillMaxSize()
                                                                .clip(panelClip)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                ) {
                                                Spacer(modifier = Modifier.height(topSpacerHeight))
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .weight(1f)
                                                                        .padding(horizontal = padDispH)
                                                ) {
                                                        AnimatedContent(
                                                                targetState = finalResult,
                                                                transitionSpec = {
                                                                        EnterTransition.None togetherWith
                                                                                ExitTransition.None
                                                                },
                                                                label = "displayMode",
                                                                modifier = Modifier.fillMaxSize()
                                                        ) { final ->
                                                                if (final != null) {
                                                                        Column(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize(),
                                                                                verticalArrangement =
                                                                                        Arrangement.Bottom,
                                                                                horizontalAlignment =
                                                                                        Alignment.End
                                                                        ) {
                                                                                val formattedFinal =
                                                                                        remember(final) {
                                                                                                formatForDisplay(
                                                                                                        final
                                                                                                )
                                                                                        }
                                                                                val adaptiveFinalFontSize =
                                                                                        remember(formattedFinal) {
                                                                                                val excess =
                                                                                                        (formattedFinal.length - 7)
                                                                                                                .coerceAtLeast(0)
                                                                                                (finalFontSize.value - minDim.value * 0.0055f * excess).coerceAtLeast(finalFloor).sp
                                                                                        }
                                                                                val finalScrollState =
                                                                                        rememberScrollState()
                                                                                LaunchedEffect(final) {
            finalScrollState.scrollTo(0)
        }
                                val answerClipboard = LocalClipboardManager.current
                                var answerCopied by remember(final) { mutableStateOf(false) }
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .horizontalScroll(
                                                                                                                finalScrollState
                                                                                                        ),
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically,
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .End
                                                                                ) {
                                                                                        SelectionContainer {
         Text(
                                                                                                text =
                                                                                                        formattedFinal,
                                                                                                fontSize =
                                                                                                        adaptiveFinalFontSize,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Normal,
                                                                                                color =
                                                                                                        MaterialTheme
                                                                                                                .colorScheme
                                                                                                                .onSurface,
                                                                                                textAlign =
                                                                                                        TextAlign
                                                                                                                .End,
                                                                                                maxLines =
                                                                                                        1,
                                                                                                softWrap =
                                                                                                        false
                                                                                        )
         }
                                                                                Spacer(modifier = Modifier.width(padDispH)) // TEXT CUT FIX (V4)
                                                                                }
         Row(
             modifier = Modifier.fillMaxWidth(),
             horizontalArrangement = Arrangement.End
         ) {
             TextButton(onClick = {
                 answerClipboard.setText(AnnotatedString(formattedFinal))
                 answerCopied = true
             }) {
                 Text(if (answerCopied) "Copied ✓" else "Copy", fontSize = (minDim.value * 0.039f).sp)
             }
         }
                                                                        }
                                                                } else {
                                                                        Box(
                                                                                modifier = Modifier.fillMaxSize()
                                                                        ) {
                                                                                val formatted =
                                                                                        remember(
                                                                                                expression
                                                                                        ) {
                                                                                                formatWithMap(
                                                                                                        expression
                                                                                                )
                                                                                        }
                                                                                var expressionLayout by remember {
                                                                                        mutableStateOf<
                                                                                                TextLayoutResult?>(
                                                                                                null
                                                                                        )
                                                                                }
                                                                                val currentLayout by
                                                                                        rememberUpdatedState(
                                                                                                expressionLayout
                                                                                        )
                                                                                val currentFormatted by
                                                                                        rememberUpdatedState(
                                                                                                formatted
                                                                                        )
                                                                                val currentExpression by
                                                                                        rememberUpdatedState(
                                                                                                expression
                                                                                        )
                                                                                val caretHolder = remember {
                                                                                        CaretLayoutHolder()
                                                                                }
                                                                                val caretColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface
                                                                                // CARET BLINK (V8): clean on/off - 600ms dekha,
                                                                                // 400ms lukano, 120ms fade. Majhamajhi alpha
                                                                                // ba pulse-e size bodlay na - sob somoy eki.
                                                                                var caretBlinkOn by remember {
                                                                                        mutableStateOf(true)
                                                                                }
                                                                                LaunchedEffect(Unit) {
                                                                                        while (true) {
                                                                                                delay(600)
                                                                                                caretBlinkOn = false
                                                                                                delay(400)
                                                                                                caretBlinkOn = true
                                                                                        }
                                                                                }
                                                                                val caretBlink by animateFloatAsState(
                                                                                        targetValue = if (caretBlinkOn) 1f else 0f,
                                                                                        animationSpec = tween(120),
                                                                                        label = "caretBlinkAlpha"
                                                                                )
                                                                                val clipboardManager =
                                                                                        LocalClipboardManager
                                                                                                .current
                                                                                val scrollState =
                                                                                        rememberScrollState()
                                                                                LaunchedEffect(
                                                                                        expression,
                                                                                        cursorPosition
                                                                                ) {
                                                                                        val lr =
                                                                                                expressionLayout
                                                                                        if (expression
                                                                                                        .isEmpty()
                                                                                        ) {
                                                                                        } else if (cursorPosition >=
                                                                                                        expression
                                                                                                                .length ||
                                                                                                        lr ==
                                                                                                                null
                                                                                        ) {
                                                                                                scrollState
                                                                                                        .scrollTo(
                                                                                                                scrollState
                                                                                                                        .maxValue
                                                                                                        )
                                                                                        } else {
                                                                                                val safeOffset =
                                                                                                        formatted
                                                                                                                        .rawToFmt[
                                                                                                                        cursorPosition
                                                                                                                                .coerceIn(
                                                                                                                                        0,
                                                                                                                                        expression
                                                                                                                                                .length
                                                                                                                                )]
                                                                                                                .coerceAtMost(
                                                                                                                        lr.layoutInput
                                                                                                                                .text
                                                                                                                                .length
                                                                                                                )
                                                                                                val cx =
                                                                                                        lr.getHorizontalPosition(
                                                                                                                safeOffset,
                                                                                                                true
                                                                                                        )
                                                                                                val view =
                                                                                                        scrollState
                                                                                                                .viewportSize
                                                                                                when {
                                                                                                        cx <
                                                                                                                scrollState
                                                                                                                        .value ->
                                                                                                                scrollState
                                                                                                                        .animateScrollTo(
                                                                                                                                cx.toInt()
                                                                                                                                        .coerceAtLeast(
                                                                                                                                                0
                                                                                                                                        ),
                                                                                                                                animationSpec =
                                                                                                                                        tween(
                                                                                                                                                120
                                                                                                                                        )
                                                                                                                        )
                                                                                                        view >
                                                                                                                0 &&
                                                                                                                cx >
                                                                                                                        scrollState
                                                                                                                        .value +
                                                                                                                                view -
                                                                                                                                24 ->
                                                                                                                scrollState
                                                                                                                        .animateScrollTo(
                                                                                                                                (cx -
                                                                                                                                                view +
                                                                                                                                                24)
                                                                                                                                        .toInt(),
                                                                                                                                animationSpec =
                                                                                                                                        tween(
                                                                                                                                                120
                                                                                                                                        )
                                                                                                                        )
                                                                                                }
                                                                                        }
                                                                                }
                                if (selActive) {
                                Row(
                                                                                                                                        modifier =
                                                                                                                                                Modifier.align(Alignment.TopStart).padding(start = padStartW, top = screenH * 0.002f).clip(
                                                                                                                                                                RoundedCornerShape(
                                                                                                                                                                        percent =
                                                                                                                                                                                50
                                                                                                                                                                )
                                                                                                                                                        )
                                                                                                                                                        .background(
                                                                                                                                                                MaterialTheme
                                                                                                                                                                        .colorScheme
                                                                                                                                                                        .surfaceVariant
                                                                                                                                                        )
                                                                                                                                                        .padding(
                                                                                                                                                                horizontal =
                                                                                                                                                                        padSmallW
                                                                                                                                                        ),
                                                                                                                                        verticalAlignment =
                                                                                                                                                Alignment
                                                                                                                                                        .CenterVertically
                                                                                                                                ) {
                                                                                                                                        if (selEnd >
                                                                                                                                                        selStart
                                                                                                                                        ) {
                                                                                                                                                TextButton(
                                                                                                                                                        onClick = {
                                                                                                                                                                val from =
                                                                                                                                                                        formatted
                                                                                                                                                                                .rawToFmt[
                                                                                                                                                                                selStart.coerceIn(
                                                                                                                                                                                        0,
                                                                                                                                                                                        expression
                                                                                                                                                                                                .length
                                                                                                                                                                                )]
                                                                                                                                                                val to =
                                                                                                                                                                        formatted
                                                                                                                                                                                .rawToFmt[
                                                                                                                                                                                selEnd.coerceIn(
                                                                                                                                                                                        0,
                                                                                                                                                                                        expression
                                                                                                                                                                                                .length
                                                                                                                                                                                )]
                                                                                                                                                                clipboardManager
                                                                                                                                                                        .setText(
                                                                                                                                                                                AnnotatedString(
                                                                                                                                                                                        formatted
                                                                                                                                                                                                .text
                                                                                                                                                                                                .substring(
                                                                                                                                                                                                        from,
                                                                                                                                                                                                        to.coerceAtMost(
                                                                                                                                                                                                                formatted
                                                                                                                                                        .text
                                                                                                                                                        .length
                                                                                                                                                                                                        )
                                                                                                                                                                                                )
                                                                                                                                                                                )
                                                                                                                                                                        )
                                                                                                                                                        }
                                                                                                                                                , contentPadding = PaddingValues(horizontal = padTinyW, vertical = 0.dp)) {
                                                                                                                                                        Text("Copy", fontSize = (minDim.value * 0.036f).sp, color = MaterialTheme.colorScheme.onSurface)
                                                                                                                                                }
                                                                                                                                                TextButton(
                                                                                                                                                        onClick = {
                                                                                                                                                                val from =
                                                                                                                                                                        formatted
                                                                                                                                                                                .rawToFmt[
                                                                                                                                                                                selStart.coerceIn(
                                                                                                                                                                                        0,
                                                                                                                                                                                        expression
                                                                                                                                                                                                .length
                                                                                                                                                                                )]
                                                                                                                                                                val to =
                                                                                                                                                                        formatted
                                                                                                                                                                                .rawToFmt[
                                                                                                                                                                                selEnd.coerceIn(
                                                                                                                                                                                        0,
                                                                                                                                                                                        expression
                                                                                                                                                                                                .length
                                                                                                                                                                                )]
                                                                                                                                                                clipboardManager
                                                                                                                                                                        .setText(
                                                                                                                                                                                AnnotatedString(
                                                                                                                                                                                        formatted
                                                                                                                                                                                                .text
                                                                                                                                                                                                .substring(
                                                                                                                                                                                                        from,
                                                                                                                                                                                                        to.coerceAtMost(
                                                                                                                                                                                                                formatted
                                                                                                                                                        .text
                                                                                                                                                        .length
                                                                                                                                                                                                        )
                                                                                                                                                                                                )
                                                                                                                                                                                )
                                                                                                                                                                        )
                                                                                                                                                                viewModel
                                                                                                                                                                        .deleteRange(
                                                                                                                                                                                selStart,
                                                                                                                                                                                selEnd
                                                                                                                                                                        )
                                                                                                                                                                selActive =
                                                                                                                                                                        false
                                                                                                                                                        }
                                                                                                                                                , contentPadding = PaddingValues(horizontal = padTinyW, vertical = 0.dp)) {
                                                                                                                                                        Text("Cut", fontSize = (minDim.value * 0.036f).sp, color = MaterialTheme.colorScheme.onSurface)
                                                                                                                                                }
                                                                                                                                        }
                                                                                                                                        TextButton(
                                                                                                                                                onClick = {
                                                                                                                                                        val clip =
                                                                                                                                                                clipboardManager
                                                                                                                                                                        .getText()
                                                                                                                                                                        ?.text
                                                                                                                                                        if (!clip.isNullOrEmpty()
                                                                                                                                                        ) {
                                                                                                                                                                if (selEnd >
                                                                                                                                                                                selStart
                                                                                                                                                                ) {
                                                                                                                                                                        viewModel
                                                                                                                                                                                .deleteRange(
                                                                                                                                                                                        selStart,
                                                                                                                                                                                selEnd
                                                                                                                                                                        )
                                                                                                                                                                }
                                                                                                                                                                viewModel
                                                                                                                                                                        .pasteText(
                                                                                                                                                                                clip
                                                                                                                                                                        )
                                                                                                                                                                selActive =
                                                                                                                                                                        false
                                                                                                                                                        }
                                                                                                                                                }
                                                                                                                                        , contentPadding = PaddingValues(horizontal = padTinyW, vertical = 0.dp)) {
                                                                                                                                                Text("Paste", fontSize = (minDim.value * 0.036f).sp, color = MaterialTheme.colorScheme.onSurface)
                                                                                                                                        }
                                                                                                                                        if (expression
                                                                                                                                                        .isNotEmpty()
                                                                                                                                        ) {
                                                                                                                                                TextButton(
                                                                                                                                                        onClick = {
                                                                                                                                                                selStart =
                                                                                                                                                                        0
                                                                                                                                                                selEnd =
                                                                                                                                                                        expression
                                                                                                                                                                                .length
                                                                                                                                                        }
                                                                                                                                                , contentPadding = PaddingValues(horizontal = padTinyW, vertical = 0.dp)) {
                                                                                                                                                Text("All", fontSize = (minDim.value * 0.036f).sp, color = MaterialTheme.colorScheme.onSurface)
                                                                                                                                                }
                                                                                                                                        }
                                                                                                                                        TextButton(
                                                                                                                                                onClick = {
                                                                                                                                                        selActive =
                                                                                                                                                                false
                                                                                                                                                }
                                                                                                                                        , contentPadding = PaddingValues(horizontal = padTinyW, vertical = 0.dp)) {
                                                                                                                                                Text("Done", fontSize = (minDim.value * 0.036f).sp, color = MaterialTheme.colorScheme.onSurface)
                                                                                                                                        }
                                                                                                                                }
                                }
                                                                                val adaptiveExpressionFontSize =
                                                                                        remember(formatted.text) {
                                                                                                val excess =
                                                                                                        (formatted.text.length - 8)
                                                                                                                .coerceAtLeast(0)
                                                                                                (expressionFontSize.value - minDim.value * 0.0055f * excess).coerceAtLeast(exprFloor).sp
                                                                                        }
                                                                                // CARET HEIGHT (V11): sob % - font minDim-er koto %,
                                                                                // caret = font * 1.2 (line-height ratio), floor 14.4%.
                                                                                val caretHeightPct = maxOf(
                                                                                        adaptiveExpressionFontSize.value / minDim.value * 1.2f,
                                                                                        0.144f
                                                                                )
                                                                                val caretHeightDp = minDim * caretHeightPct
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                                                                                        .horizontalScroll(
                                                                                                                scrollState,
                                                                                                                enabled =
                                                                                                                        !selActive
                                                                                                        ),
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically,
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .End
                                                                                ) {
                                                                                        Box {
                                                                                                Text(
                                                                                                        text =
                                                                                                                formatted
                                                                                                                        .text,
                                                                                                        fontSize =
                                                                                                                adaptiveExpressionFontSize,
                                                                                                        fontWeight =
                                                                                                                FontWeight
                                                                                                                        .Normal,
                                                                                                        color =
                                                                                                                MaterialTheme
                                                                                                                        .colorScheme
                                                                                                                        .onSurface,
                                                                                                        textAlign =
                                                                                                                TextAlign
                                                                                                                        .End,
                                                                                                        maxLines =
                                                                                                                1,
                                                                                                        softWrap =
                                                                                                                false,
                                                                                                        onTextLayout = {
                                                                                                                expressionLayout =
                                                                                                                        it
                                                                                                                caretHolder
                                                                                                                        .layout =
                                                                                                                        it
                                                                                                        },
                                                                                                        modifier =
                                                                                                                Modifier.widthIn(
                                                                                                                                min =
                                                                                                                                        (screenW - padDispH * 3f)
                                                                                                                        )
                                                                                                                        .heightIn(min = caretHeightDp) // V10: khali Text-o caret-er soman lomba
                                                                                                                        .pointerInput(
                                                                                                                                Unit
                                                                                                                        ) {
                                                                                                                                detectTapGestures(
                                                                                                                                        onTap = {
                                                                                                                                                offset
                                                                                                                                                ->
                                                                                                                                                val lr =
                                                                                                                                                        currentLayout
                                                                                                                                                                ?: return@detectTapGestures
                                                                                                                                                val fmt =
                                                                                                                                                        currentFormatted
                                                                                                                                                selActive =
                                                                                                                                                        false
                                                                                                                                                val fmtOffset =
                                                                                                                                                        lr.getOffsetForPosition(
                                                                                                                                                                        offset
                                                                                                                                                                )
                                                                                                                                                                .coerceIn(
                                                                                                                                                                        0,
                                                                                                                                                                        fmt.text
                                                                                                                                                                                .length
                                                                                                                                                                )
                                                                                                                                                viewModel
                                                                                                                                                        .setCursorPosition(
                                                                                                                                                                fmt.fmtToRaw[
                                                                                                                                                                        fmtOffset]
                                                                                                                                                        )
                                                                                                                                        },
                                                                                                                                        onLongPress = {
                                                                                                                                                offset
                                                                                                                                                ->
                                                                                                                                                val lr =
                                                                                                                                                        currentLayout
                                                                                                                                                                ?: return@detectTapGestures
                                                                                                                                                val fmt =
                                                                                                                                                        currentFormatted
                                                                                                                                                val rawExpr =
                                                                                                                                                        currentExpression
                                                                                                                                                if (rawExpr.isEmpty()
                                                                                                                                                ) {
                                                                                                                                                        selStart =
                                                                                                                                                                0
                                                                                                                                                        selEnd =
                                                                                                                                                                0
                                                                                                                                                        selActive =
                                                                                                                                                                true
                                                                                                                                                } else {
                                                                                                                                                        val fmtOffset =
                                                                                                                                                                lr.getOffsetForPosition(
                                                                                                                                                                        offset
                                                                                                                                                                        )
                                                                                                                                                                        .coerceIn(
                                                                                                                                                                                0,
                                                                                                                                                                                fmt.text
                                                                                                                                                                                        .length
                                                                                                                                                                        )
                                                                                                                                                        val rawBoundary =
                                                                                                                                                                fmt.fmtToRaw[
                                                                                                                                                                        fmtOffset]
                                                                                                                                                        val charIndex =
                                                                                                                                                                rawBoundary
                                                                                                                                                                        .coerceIn(
                                                                                                                                                                                0,
                                                                                                                                                                                rawExpr.length -
                                                                                                                                                                                        1
                                                                                                                                                                        )
                                                                                                                                                        val range =
                                                                                                                                                                tokenRangeAt(
                                                                                                                                                                        rawExpr,
                                                                                                                                                                        charIndex
                                                                                                                                                                )
                                                                                                                                                        selStart =
                                                                                                                                                                range.first
                                                                                                                                                        selEnd =
                                                                                                                                                                range.second
                                                                                                                                                        selActive =
                                                                                                                                                                true
                                                                                                                                                }
                                                                                                                                        }
                                                                                                                                )
                                                                                                                        }
                                                                                                                        .drawWithContent {
                                                                                                                                drawContent()
                                                                                                                                val lr =
                                                                                                                                        caretHolder
                                                                                                                                                .layout
                                                                                                                                if (lr !=
                                                                                                                                                null &&
                                                                                                                                                lr.layoutInput
                                                                                                                                                        .text
                                                                                                                                                        .text ==
                                                                                                                                                        formatted
                                                                                                                                                                .text
                                                                                                                                ) {
                                                                                                                                        if (selActive &&
                                                                                                                                                        selEnd >
                                                                                                                                                                selStart
                                                                                                                                        ) {
                                                                                                                                                val sx =
                                                                                                                                                        lr.getHorizontalPosition(
                                                                                                                                                                formatted
                                                                                                                                                                        .rawToFmt[
                                                                                                                                                                        selStart.coerceIn(
                                                                                                                                                                                0,
                                                                                                                                                                                expression
                                                                                                                                                                                        .length
                                                                                                                                                                        )],
                                                                                                                                                                true
                                                                                                                                                        )
                                                                                                                                                val ex =
                                                                                                                                                        lr.getHorizontalPosition(
                                                                                                                                                                formatted
                                                                                                                                                                        .rawToFmt[
                                                                                                                                                                        selEnd.coerceIn(
                                                                                                                                                                                0,
                                                                                                                                                                                expression
                                                                                                                                                                                        .length
                                                                                                                                                                        )],
                                                                                                                                                                true
                                                                                                                                                        )
                                                                                                                                                drawRect(
                                                                                                                                                        color =
                                                                                                                                                                caretColor
                                                                                                                                                                        .copy(
                                                                                                                                                                                alpha =
                                                                                                                                                                                        0.30f
                                                                                                                                                                        ),
                                                                                                                                                        topLeft =
                                                                                                                                                                Offset(
                                                                                                                                                                        sx,
                                                                                                                                                                        0f
                                                                                                                                                                ),
                                                                                                                                                        size =
                                                                                                                                                                Size(
                                                                                                                                                                        ex -
                                                                                                                                                                                sx,
                                                                                                                                                                        size.height
                                                                                                                                                                )
                                                                                                                                                )
                                                                                                                                        } else if (!selActive
                                                                                                                                        ) {
                                                                                                                                                val caretOffset =
                                                                                                                                                        formatted
                                                                                                                                                                .rawToFmt[
                                                                                                                                                                cursorPosition
                                                                                                                                                                        .coerceIn(
                                                                                                                                                                                0,
                                                                                                                                                                                expression
                                                                                                                                                                                        .length
                                                                                                                                                                        )]
                                                                                                                                                val cx =
                                                                                                                                                        if (formatted
                                                                                                                                                                        .text
                                                                                                                                                                        .isEmpty()
                                                                                                                                                        ) {
                                                                                                                                                                // Khali thakle caret line-er DAN kinare
                                                                                                                                                                // (typing jekhane dekhabe, sekhanei).
                                                                                                                                                                lr.size.width.toFloat()
                                                                                                                                                        } else {
                                                                                                                                                                lr.getHorizontalPosition(
                                                                                                                                                                        caretOffset,
                                                                                                                                                                        true
                                                                                                                                                                )
                                                                                                                                                        }
                                                                                                                                                val caretHeight = caretHeightDp.toPx() // V10: Text-er min height-er sathe eki value
                                                                                                                                                // Caret height sudhu font theke (V8) - khali ba type
                                                                                                                                                // kora, eki font-e eki height, eki vertical position.
                                                                                                                                                // BOTTOM-ANCHOR (V9): caret Text-er niche bosbe - khali
                                                                                // text choto holeo caret niche theke eki jaygay thakbe.
                                                                                val caretTop = size.height - caretHeight
drawRoundRect(
                                                                                                                                                                                                                                                                                                        color = caretColor.copy(alpha = 0.07f * caretBlink),
                                                                                                                                                                                                                                                                                                        topLeft = Offset(
                                                                                                                                                                                                                                                                                                                cx - caretWidthPct.toPx() * 1.5f,
                                                                                                                                                                                                                                                                                                                caretTop
                                                                                                                                                                                                                                                                                                        ),
                                                                                                                                                                                                                                                                                                        size = Size(
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx() * 3f,
                                                                                                                                                                                                                                                                                                                caretHeight
                                                                                                                                                                                                                                                                                                        ),
                                                                                                                                                                                                                                                                                                        cornerRadius = CornerRadius(
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx() * 1.5f,
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx() * 1.5f
                                                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                                                drawRoundRect(
                                                                                                                                                                                                                                                                                                        color = caretColor.copy(alpha = caretBlink),
                                                                                                                                                                                                                                                                                                        topLeft = Offset(
                                                                                                                                                                                                                                                                                                                cx - caretWidthPct.toPx() / 2f,
                                                                                                                                                                                                                                                                                                                caretTop
                                                                                                                                                                                                                                                                                                        ),
                                                                                                                                                                                                                                                                                                        size = Size(
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx(),
                                                                                                                                                                                                                                                                                                                caretHeight
                                                                                                                                                                                                                                                                                                        ),
                                                                                                                                                                                                                                                                                                        cornerRadius = CornerRadius(
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx() / 2f,
                                                                                                                                                                                                                                                                                                                caretWidthPct.toPx() / 2f
                                                                                                                                                                                                                                                                                                        )
                                                                                                                                                                                                                                                                                                )
                                                                                                                                        }
                                                                                                                                }
                                                                                                                        }
                                                                                                )
                                                                                                if (selActive &&
                                                                                                                formatted
                                                                                                                        .text
                                                                                                                        .isNotEmpty()
                                                                                                ) {
                                                                                                        val lr =
                                                                                                                expressionLayout
                                                                                                        if (lr !=
                                                                                                                        null &&
                                                                                                                        lr.layoutInput
                                                                                                                                .text
                                                                                                                                .text ==
                                                                                                                                formatted
                                                                                                                                        .text
                                                                                                        ) {
                                                                                                                val startX =
                                                                                                                        lr.getHorizontalPosition(
                                                                                                                                formatted
                                                                                                                                        .rawToFmt[
                                                                                                                                        selStart.coerceIn(
                                                                                                                                                0,
                                                                                                                                                expression
                                                                                                                                                        .length
                                                                                                                                        )],
                                                                                                                                true
                                                                                                                        )
                                                                                                                val endX =
                                                                                                                        lr.getHorizontalPosition(
                                                                                                                                formatted
                                                                                                                                        .rawToFmt[
                                                                                                                                        selEnd.coerceIn(
                                                                                                                                                0,
                                                                                                                                                expression
                                                                                                                                                        .length
                                                                                                                                        )],
                                                                                                                                true
                                                                                                                        )
                                                                                                                SelectionHandle(
                                                                                                                        currentX =
                                                                                                                                startX,
                                                                                                                        handleSize = handleSizePct,
                                                                                                                        handleOffset = handleOffsetPct,
                                                                                                                        modifier =
                                                                                                                                Modifier
                                                                                                                                        .offset {
                                                                                                                                                IntOffset(
                                                                                                                                                        (startX -
                                                                                                                                                                        handleOffsetPct.toPx())
                                                                                                                                                                .roundToInt(),
                                                                                                                                                        (lr.size
                                                                                                                                                                        .height -
                                                                                                                                                                        handleOffsetPct.toPx())
                                                                                                                                                                .roundToInt()
                                                                                                                                                )
                                                                                                                                        }
                                                                                                                ) {
                                                                                                                        targetX
                                                                                                                        ->
                                                                                                                        val fmtOffset =
                                                                                                                                lr.getOffsetForPosition(
                                                                                                                                                Offset(
                                                                                                                                                        targetX.coerceAtLeast(
                                                                                                                                                                0f
                                                                                                                                                        ),
                                                                                                                                                        lr.size
                                                                                                                                                                .height /
                                                                                                                                                                2f
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                formatted
                                                                                                                                                        .text
                                                                                                                                                        .length
                                                                                                                                        )
                                                                                                                        selStart =
                                                                                                                                formatted
                                                                                                                                                .fmtToRaw[
                                                                                                                                                fmtOffset]
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                selEnd
                                                                                                                                        )
                                                                                                                }
                                                                                                                SelectionHandle(
                                                                                                                        currentX =
                                                                                                                                endX,
                                                                                                                        handleSize = handleSizePct,
                                                                                                                        handleOffset = handleOffsetPct,
                                                                                                                        modifier =
                                                                                                                                Modifier
                                                                                                                                        .offset {
                                                                                                                                                IntOffset(
                                                                                                                                                        (endX -
                                                                                                                                                                        handleOffsetPct.toPx())
                                                                                                                                                                .roundToInt(),
                                                                                                                                                        (lr.size
                                                                                                                                                                        .height -
                                                                                                                                                                        handleOffsetPct.toPx())
                                                                                                                                                                .roundToInt()
                                                                                                                                                )
                                                                                                                                        }
                                                                                                                ) {
                                                                                                                        targetX
                                                                                                                        ->
                                                                                                                        val fmtOffset =
                                                                                                                                lr.getOffsetForPosition(
                                                                                                                                                Offset(
                                                                                                                                                        targetX.coerceAtLeast(
                                                                                                                                                                0f
                                                                                                                                                        ),
                                                                                                                                                        lr.size
                                                                                                                                                                .height /
                                                                                                                                                                2f
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                        .coerceIn(
                                                                                                                                                0,
                                                                                                                                                formatted
                                                                                                                                                        .text
                                                                                                                                                        .length
                                                                                                                                        )
                                                                                                                        selEnd =
                                                                                                                                formatted
                                                                                                                                                .fmtToRaw[
                                                                                                                                                fmtOffset]
                                                                                                                                        .coerceIn(
                                                                                                                                                selStart,
                                                                                                                                                expression
                                                                                                                                                        .length
                                                                                                                                        )
                                                                                                                }
                                                                                                        }
                                                                                                }
                                                                                        }
                                                                                Spacer(modifier = Modifier.width(padDispH)) // TEXT CUT FIX (V4)
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        // Final thakle preview khali - height 0%,
                                                                        // nahole oi 6.5% jayga kheye Copy kete jeto.
                                                                        .height(if (finalResult == null) previewH else screenH * 0f)
                                                                        .padding(horizontal = padDispH),
                                                        contentAlignment = Alignment.CenterEnd
                                                ) {
                                                        if (finalResult == null &&
                                                                        result != "0" &&
                                                                        expression.isNotEmpty()
                                                        ) {
                                                                val previewScrollState =
                                                                        rememberScrollState()
                                                                LaunchedEffect(result) {
            previewScrollState.scrollTo(0)
        }
                                                                Row(
                                                                        modifier =
                                                                                Modifier.horizontalScroll(
                                                                                        previewScrollState
                                                                                ),
                                                                        verticalAlignment =
                                                                                Alignment.CenterVertically,
                                                                        horizontalArrangement =
                                                                                Arrangement.End
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        formatForDisplay(
                                                                                                result
                                                                                        ),
                                                                                fontSize = previewFontSize,
                                                                                color =
                                                                                        // ERROR RED (2026-11-07): when
                                                                                        // "=" fails, result is "Error"
                                                                                        // - show it red, not white.
                                                                                        if (result == "Error") Color(0xFFE53935)
                                                                                        else MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface,
                                                                                textAlign = TextAlign.End,
                                                                                maxLines = 1,
                                                                                softWrap = false
                                                                        )
                                                                Spacer(modifier = Modifier.width(padDispH)) // TEXT CUT FIX (V4)
                                                                }
                                                        }
                                                }
                                                Spacer(modifier = Modifier.height(bottomSpacerH))
                                                }
                                }
                        }
val keypadSection: @Composable () -> Unit = {
                                        // FIRST ROW TOP GAP (V7): prothom row-er upore halka ektu faka.
                                        Spacer(modifier = Modifier.height(topGapH))
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(topRowHeight)
                                                                .padding(start = padKeyH, top = 0.dp, end = padKeyH),
                                                horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                TopRoundKey(
                                                        rectangular = isCompactWindow,
                                                        text = "√",
                                                        fontSize = 37,
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                        onClick = { handleKeyLabel("√") }
                                                )
                                                TopRoundKey(
                                                        rectangular = isCompactWindow,
                                                        text = "^",
                                                        fontSize = 37,
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                        onClick = { handleKeyLabel("^") }
                                                )
                                                TopRoundKey(
                                                        rectangular = isCompactWindow,
                                                        text = "E",
                                                        fontSize = 37,
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                        onClick = { handleKeyLabel("E") }
                                                )
                                                TopRoundKey(
                                                        rectangular = isCompactWindow,
                                                        text = "Ans",
                                                        fontSize = 28,
                                                        bold = true,
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                        onClick = { handleKeyLabel("Ans") }
                                                )
                                        }
                                        // V5: top row ar keypad-er majhe 1 vertical gap (0.8% height).
                                        Spacer(modifier = Modifier.height(gapKeyV))
                                        HorizontalPager(
                                                state = pagerState,
                                                userScrollEnabled = false,
                                                modifier = Modifier.fillMaxWidth().height(topRowHeight * 5f + gapKeyV * 4f)
                                        ) { page ->
                                                if (page == 1) {
                                                        Column(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .padding(
                                                                                        horizontal = padKeyH
                                                                                )
                                                                                .pointerInput(scientificExpanded, pullThresholdPct) {
                                                                                        val threshold = pullThresholdPct.toPx()
                                                                                        detectVerticalDragGestures(
                                                                                                onVerticalDrag = { _, dragAmount ->
                                                                                                        pullAccum += dragAmount
                                                                                                        if (pullAccum > threshold && !scientificExpanded) {
                                                                                                                scientificExpanded = true
                                                                                                                pullAccum = 0f
                                                                                                        } else if (pullAccum < -threshold && scientificExpanded) {
                                                                                                                scientificExpanded = false
                                                                                                                pullAccum = 0f
                                                                                                        }
                                                                                                },
                                                                                                onDragEnd = { pullAccum = 0f },
                                                                                                onDragCancel = { pullAccum = 0f }
                                                                                        )
                                                                                },
                                                                verticalArrangement =
                                                                        Arrangement.spacedBy(gapKeyV)
                                                        ) {
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                                                        horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "AC", modifier = Modifier.weight(1f).fillMaxHeight(), fontScale = keyFontScale, onClick = { handleKeyLabel("AC") })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "()", modifier = Modifier.weight(1f).fillMaxHeight(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("()") })
                                                                        SwapKey(
                                                                                scientificExpanded = scientificExpanded,
                                                                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                                                                normal = { CalculatorButton(rectangular = isCompactWindow, text = "%", modifier = Modifier.fillMaxSize(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("%") }) },
                                                                                scientific = { CalculatorButton(rectangular = isCompactWindow, text = "π", modifier = Modifier.fillMaxSize(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("π") }) }
                                                                        )
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "÷", modifier = Modifier.weight(1f).fillMaxHeight(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("÷") })
                                                                }
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                                                        horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "7", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("7") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "|x|", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("abs") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "8", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("8") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "ln", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("ln") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "9", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("9") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "log", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("log") }) })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "×", modifier = Modifier.weight(1f).fillMaxHeight(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("×") })
                                                                }
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                                                        horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "4", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("4") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "sin", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("sin") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "5", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("5") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "cos", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("cos") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "6", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("6") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "tan", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("tan") }) })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "−", modifier = Modifier.weight(1f).fillMaxHeight(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("−") })
                                                                }
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                                                        horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "1", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("1") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "sin⁻¹", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("asin") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "2", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("2") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "cos⁻¹", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("acos") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "3", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("3") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "tan⁻¹", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("tan") }) })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "+", modifier = Modifier.weight(1f).fillMaxHeight(), isOperator = true, fontScale = keyFontScale, onClick = { handleKeyLabel("+") })
                                                                }
                                                                Row(
                                                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                                                        horizontalArrangement = Arrangement.spacedBy(gapKeyH),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = "0", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("0") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "!", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("!") }) })
                                                                        SwapKey(scientificExpanded = scientificExpanded, modifier = Modifier.weight(1f).fillMaxHeight(), normal = { CalculatorButton(rectangular = isCompactWindow, text = ".", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel(".") }) }, scientific = { ScientificTextKey(rectangular = isCompactWindow, text = "eˣ", modifier = Modifier.fillMaxSize(), fontScale = keyFontScale, onClick = { handleKeyLabel("exp") }) })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "⌫", modifier = Modifier.weight(1f).fillMaxHeight(), fontScale = keyFontScale, onClick = { handleKeyLabel("⌫") })
                                                                        CalculatorButton(rectangular = isCompactWindow, text = "=", modifier = Modifier.weight(1f).fillMaxHeight(), fontScale = keyFontScale, onLongClick = { openBlackHoleSetupIfNeeded() }, onClick = { handleKeyLabel("=") })
                                                                }
                                                        }
                                                } else {
                                                        Column(
                                                                modifier =
                                                                        Modifier.fillMaxSize()
                                                                                .padding(horizontal = padStartW)
                                                        ) {
                                                                if (history.isEmpty()) {
                                                                        Box(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize(),
                                                                                contentAlignment =
                                                                                        Alignment.Center
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                "No history yet",
                                                                                        color =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurfaceVariant
                                                                                )
                                                                        }
                                                                } else {
                                                                        LazyColumn(
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                                                .padding(
                                                                                                        horizontal =
                                                                                                                padHistH
                                                                                                )
                                                                        ) {
                                                                                groupedEntries
                                                                                        .forEachIndexed {
                                                                                                groupIndex,
                                                                                                (
                                                                                                        day,
                                                                                                        dayItems)
                                                                                                ->
                                                                                                item(
                                                                                                        key =
                                                                                                                "panel-header-$day"
                                                                                                ) {
                                                                                                        Box(
                                                                                                                modifier =
                                                                                                                        Modifier.fillMaxWidth()
                                                                                                                                .padding(
                                                                                                                                        top =
                                                                                                                                                histPadV,
                                                                                                                                        bottom =
                                                                                                                                                histPadV
                                                                                                                                )
                                                                                                        ) {
                                                                                                                Text(
                                                                                                                        text =
                                                                                                                                day,
                                                                                                                        fontSize =
                                                                                                                                (minDim.value * 0.042f).sp,
                                                                                                                        color =
                                                                                                                                MaterialTheme
                                                                                                                                        .colorScheme
                                                                                                                                        .onSurfaceVariant,
                                                                                                                        modifier =
                                                                                                                                Modifier.align(
                                                                                                                                        Alignment
                                                                                                                                                .Center
                                                                                                                                )
                                                                                                                )
                                                                                                                if (groupIndex ==
                                                                                                                                0
                                                                                                                ) {
                                                                                                                        TextButton(
                                                                                                                                onClick = {
                                                                                                                                        historyViewModel
                                                                                                                                                .clearAll()
                                                                                                                                },
                                                                                                                                modifier =
                                                                                                                                        Modifier.align(
                                                                                                                                                Alignment
                                                                                                                                                        .CenterEnd
                                                                                                                                        )
                                                                                                                        ) {
                                                                                                                                Text(
                                                                                                                                        text =
                                                                                                                                                "Clear",
                                                                                                                                        fontSize =
                                                                                                                                                (minDim.value * 0.039f).sp,
                                                                                                                                        color =
                                                                                                                                                MaterialTheme
                                                                                                                                                        .colorScheme
                                                                                                                                        .onSurface
                                                                                                                                )
                                                                                                                        }
                                                                                                                }
                                                                                                        }
                                                                                                }
                                                                                                itemsIndexed(
                                                                                                        dayItems
                                                                                                ) { _, item
                                                                                                        ->
                                                                                                        Column(
                                                                                                                modifier =
                                                                                                                        Modifier.fillMaxWidth()
                                                                                                                                .clickable {
                                                                                                                                        selActive =
                                                                                                                                                false
                                                                                                                                        viewModel
                                                                                                                                                .onHistoryItemClick(
                                                                                                                                                        item.result
                                                                                                                                                )
                                                                                                                                        scope
                                                                                                                                                .launch {
                                                                                                                                                        pagerState
                                                                                                                                                                .scrollToPage(
                                                                                                                                                                        1
                                                                                                                                                                )
                                                                                                                                                }
                                                                                                                                }
                                                                                                                                .padding(
                                                                                                                                        vertical =
                                                                                                                                                histPadV
                                                                                                                                ),
                                                                                                                horizontalAlignment =
                                                                                                                        Alignment
                                                                                                                                .Start
                                                                                                        ) {
                                                                                                                Text(
            text = formatForDisplay(item.expression),
            fontSize = (minDim.value * 0.055f).sp,
            lineHeight = (minDim.value * 0.072f).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            softWrap = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(screenH * 0.007f))
        Text(
            text = formatForDisplay(item.result),
            fontSize = (minDim.value * 0.094f).sp,
            lineHeight = (minDim.value * 0.117f).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            softWrap = true,
            modifier = Modifier.fillMaxWidth()
        )
                                                                                                        }
                                                                                                }
                                                                                                item(
                                                                                                        key =
                                                                                                                "panel-divider-$day"
                                                                                                ) {
                                                                                                        HorizontalDivider(
                                                                                                                color =
                                                                                                                        MaterialTheme
                                                                                                                                .colorScheme
                                                                                                                                .outline
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.4f
                                                                                                                                ),
                                                                                                                modifier =
                                                                                                                        Modifier.padding(
                                                                                                                                vertical =
                                                                                                                                        screenH * 0.009f
                                                                                                                        )
                                                                                                        )
                                                                                                }
                                                                                        }
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(dotsH),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                repeat(2) { index ->
                                                        val active = pagerState.currentPage == index
                                                        Box(
                                                                modifier =
                                                                        Modifier.clickable {
                                                                                                scope.launch {
                                                                                                        pagerState.animateScrollToPage(index)
                                                                                                }
                                                                                        }
                                                                                .padding(horizontal = padStartW, vertical = dotPadBottomH)
                                                                                .size(
                                                                                        if (active) screenW * 0.018f
                                                                                        else screenW * 0.015f
                                                                                )
                                                                                .clip(CircleShape)
                                                                                .background(
                                                                                        if (active) {
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurface
                                                                                        } else {
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurfaceVariant
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.35f
                                                                                                        )
                                                                                        }
                                                                                )
                                                        )
                                                }
                                        }
                        }
if (isLandscape) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                        Column(modifier = Modifier.weight(0.58f).fillMaxHeight()) { keypadSection() }
                                        Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) { displayPanel(Modifier.fillMaxSize()) }
                                }
                        } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                        displayPanel(Modifier.fillMaxWidth().height(displayH))
                                        keypadSection()
                                }
                        }
}
                        }
                }
        }
        }
}

@Composable
private fun SelectionHandle(
        currentX: Float,
        handleSize: Dp,
        handleOffset: Dp,
        modifier: Modifier = Modifier,
        onDragTo: (Float) -> Unit
) {
        val currentXState by rememberUpdatedState(currentX)
        val dragToState by rememberUpdatedState(onDragTo)
        Box(
                modifier =
                        modifier.size(handleSize)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                                .pointerInput(Unit) {
                                        var anchor = 0f
                                        var total = 0f
                                        detectDragGestures(
                                                onDragStart = {
                                                        anchor = currentXState
                                                        total = 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        total += dragAmount.x
                                                        dragToState(anchor + total)
                                                }
                                        )
                                }
        )
}

private fun tokenRangeAt(raw: String, index: Int): Pair<Int, Int> {
        if (raw.isEmpty()) return 0 to 0
        val i = index.coerceIn(0, raw.length - 1)
        val c = raw[i]
        return when {
                c.isDigit() || c == '.' -> {
                        var s = i
                        while (s > 0 && (raw[s - 1].isDigit() || raw[s - 1] == '.')) s--
                        var e = i + 1
                        while (e < raw.length && (raw[e].isDigit() || raw[e] == '.')) e++
                        s to e
                }
                c.isLetter() -> {
                        var s = i
                        while (s > 0 && raw[s - 1].isLetter()) s--
                        var e = i + 1
                        while (e < raw.length && raw[e].isLetter()) e++
                        if (e < raw.length && raw[e] == '(') e++
                        s to e
                }
                else -> i to i + 1
        }
}

@Composable
private fun SwapKey(
        scientificExpanded: Boolean,
        modifier: Modifier = Modifier,
        normal: @Composable () -> Unit,
        scientific: @Composable () -> Unit
) {
        AnimatedContent(
                targetState = scientificExpanded,
                transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically(tween(180)) { if (targetState) -it / 3 else it / 3 }) togetherWith
                                (fadeOut(tween(120)) + slideOutVertically(tween(120)) { if (targetState) it / 3 else -it / 3 })
                },
                modifier = modifier,
                label = "swapKey"
        ) { isSci ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isSci) scientific() else normal()
                }
        }
}

@Composable
private fun TopRoundKey(
        text: String,
        modifier: Modifier = Modifier,
        fontSize: Int = 28,
        bold: Boolean = false,
        rectangular: Boolean = false, // V12: choto window-e ayotakar
        onClick: () -> Unit
) {
        BoxWithConstraints(
                modifier = modifier,
                contentAlignment = Alignment.Center
        ) {
                // % font: cell-er % - 28sp chilo ~35% cell (80dp cell-e),
                // Ans 20sp ~25% cell. Cell joto boro/choto hok, glyph
                // sei % -ei thakbe, tai shape nosto hobe na.
                val size = maxWidth.coerceAtMost(maxHeight)
                val keyShape = if (rectangular) RoundedCornerShape(size * 0.35f) else CircleShape // V14: radius = key-er 35%
                val sizeModifier = if (rectangular) Modifier.fillMaxSize() else Modifier.size(size) // V13: ayotakar-e cell vore, gol-e square
                val pctFontSize = (size.value * fontSize / 80f).sp
                val topInteraction = remember { MutableInteractionSource() }
                Box(
                        modifier =
                                sizeModifier
                                        .clip(keyShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable(interactionSource = topInteraction, indication = null, onClick = onClick),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = text,
                                fontSize = pctFontSize,
                                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                }
        }
}

@Composable
private fun ScientificTextKey(
        text: String,
        modifier: Modifier = Modifier,
        fontScale: Float = 1f,
        rectangular: Boolean = false, // V12: choto window-e ayotakar
        onClick: () -> Unit
) {
        val interactionSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
                modifier = modifier,
                contentAlignment = Alignment.Center
        ) {
                val size = maxWidth.coerceAtMost(maxHeight)
                val keyShape = if (rectangular) RoundedCornerShape(size * 0.35f) else CircleShape // V14: radius = key-er 35%
                val sizeModifier = if (rectangular) Modifier.fillMaxSize() else Modifier.size(size) // V13: ayotakar-e cell vore, gol-e square
                // % font: 24sp ~30% cell, 20sp (4+ char) ~25% cell.
                val pctFontSize = if (text.length >= 4) (size.value * 0.25f * fontScale).sp
                        else (size.value * 0.30f * fontScale).sp
                Box(
                        modifier =
                                sizeModifier
                                        .clip(keyShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = onClick
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = text,
                                fontSize = pctFontSize,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorButton(
        text: String,
        modifier: Modifier = Modifier,
        isOperator: Boolean = false,
        compact: Boolean = false,
        rectangular: Boolean = false, // V12: choto window-e ayotakar
        fontScale: Float = 1f,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
) {
        val backgroundColor =
                if (text == "=" || text == "⌫" || text == "AC") {
                        MaterialTheme.colorScheme.onSurface
                } else {
                        MaterialTheme.colorScheme.surfaceVariant
                }
        val contentColor =
                if (text == "=" || text == "⌫" || text == "AC") {
                        MaterialTheme.colorScheme.surface
                } else {
                        MaterialTheme.colorScheme.onSurface
                }
        val interactionSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
                modifier = modifier,
                contentAlignment = Alignment.Center
        ) {
                val size = maxWidth.coerceAtMost(maxHeight)
                val keyShape = if (rectangular) RoundedCornerShape(size * 0.35f) else CircleShape // V14: radius = key-er 35%
                val sizeModifier = if (rectangular) Modifier.fillMaxSize() else Modifier.size(size) // V13: ayotakar-e cell vore, gol-e square
                Box(
                        modifier =
                                sizeModifier
                                        .clip(keyShape)
                                        .background(backgroundColor)
                                        .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = onClick,
                                                onLongClick = onLongClick
                                        ),
                        contentAlignment = Alignment.Center
                ) { CalculatorButtonContent(text, contentColor, fontScale, size) }
        }
}

@Composable
private fun CalculatorButtonContent(text: String, contentColor: Color, fontScale: Float, cellSize: Dp) {
        if (text == "⌫") {
                Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete",
                        tint = contentColor,
                        // Icon %: 34dp chilo ~42.5% cell, min 25% cell.
                        modifier = Modifier.size((cellSize.value * 0.425f * fontScale).dp.coerceAtLeast(cellSize * 0.25f))
                )
        } else {
                Text(
                        text = if (text == "()") "( )" else text,
                        color = contentColor,
                        // Font % of cell (80dp cell dhore): AC 25sp=31%, %=36%, >1 char 21sp=26%, digit 37sp=46%.
                        fontSize =
                                when {
                                        text == "AC" || text == "()" || text == "Exp" ->
                                                (cellSize.value * 0.40f * fontScale).sp
                                        text == "%" -> (cellSize.value * 0.42f * fontScale).sp
                                        text.length > 1 -> (cellSize.value * 0.26f * fontScale).sp
                                        else -> (cellSize.value * 0.46f * fontScale).sp
                                },
                        fontWeight = FontWeight.Bold
                )
        }
}

private fun dayLabel(timestamp: Long): String {
        val itemCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
                isSameDay(itemCalendar, today) -> "Today"
                isSameDay(itemCalendar, yesterday) -> "Yesterday"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun formatForDisplay(raw: String): String {
        var display =
                raw.replace("sqrt(", "√(")
                        .replace("log10(", "log(")
                        .replace("pi", "π")
                        .replace("*", "×")
                        .replace("/", "÷")
                        .replace("-", "−")
        return display.replace(Regex("\\d+(\\.\\d+)?")) { match ->
                val parts = match.value.split(".")
                val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
                if (parts.size > 1) grouped + "." + parts[1] else grouped
        }
}

private class CaretLayoutHolder {
        var layout: TextLayoutResult? = null
}

private data class FormattedExpression(
        val text: String,
        val fmtToRaw: IntArray,
        val rawToFmt: IntArray
)

private fun formatWithMap(raw: String): FormattedExpression {
        val sb = StringBuilder()
        val fmtToRaw = mutableListOf(0)
        fun emit(token: String, rawStart: Int, rawEnd: Int) {
                token.forEachIndexed { index, ch ->
                        sb.append(ch)
                        fmtToRaw.add(if (index == token.lastIndex) rawEnd else rawStart)
                }
        }
        var i = 0
        while (i < raw.length) {
                when {
                        raw.startsWith("sqrt(", i) -> {
                                emit("√(", i, i + 5)
                                i += 5
                        }
                        raw.startsWith("log10(", i) -> {
                                emit("log(", i, i + 6)
                                i += 6
                        }
                        raw.startsWith("pi", i) -> {
                                emit("π", i, i + 2)
                                i += 2
                        }
                        raw[i] == '*' -> {
                                emit("×", i, i + 1)
                                i += 1
                        }
                        raw[i] == '/' -> {
                                emit("÷", i, i + 1)
                                i += 1
                        }
                        raw[i] == '-' -> {
                                emit("−", i, i + 1)
                                i += 1
                        }
                        raw[i].isDigit() -> {
                                var j = i
                                while (j < raw.length && (raw[j].isDigit() || raw[j] == '.')) j++
                                val segment = raw.substring(i, j)
                                val dot = segment.indexOf('.')
                                val intPart = if (dot >= 0) segment.substring(0, dot) else segment
                                val rest = if (dot >= 0) segment.substring(dot) else ""
                                intPart.forEachIndexed { index, ch ->
                                        if (index > 0 && (intPart.length - index) % 3 == 0) {
                                                emit(",", i + index, i + index)
                                        }
                                        emit(ch.toString(), i + index, i + index + 1)
                                }
                                rest.forEachIndexed { index, ch ->
                                        val rawPos = i + intPart.length + index
                                        emit(ch.toString(), rawPos, rawPos + 1)
                                }
                                i = j
                        }
                        else -> {
                                emit(raw[i].toString(), i, i + 1)
                                i += 1
                        }
                }
        }
        val rawToFmt = IntArray(raw.length + 1)
        var f = 0
        for (r in 0..raw.length) {
                while (f + 1 < fmtToRaw.size && fmtToRaw[f + 1] <= r) f++
                rawToFmt[r] = f
        }
        return FormattedExpression(sb.toString(), fmtToRaw.toIntArray(), rawToFmt)
}
