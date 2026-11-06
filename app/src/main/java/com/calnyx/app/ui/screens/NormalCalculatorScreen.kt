// FILE: app/src/main/java/com/calnyx/app/ui/screens/NormalCalculatorScreen.kt
// REPLACE the whole file. ANS + RIGHTDRAWER version, based on LIFTED.
// The user asked (2026-11-06):
//  1. DRAWER moves to the RIGHT - done in NavGraph (right-edge
//     swipe opens it, left-edge swipe opens nothing). This file
//     does NOT change the drawer.
//  2. HISTORY stays on LEFT swipe - it already was: the keypad
//     pager is page 0 = keypad, page 1 = history, so a left
//     swipe over the keypad already opens History. NO change
//     needed here (the user corrected himself mid-sentence
//     and the corrected version matches the code).
//  3. LAB FLASK -> ANS KEY: the round flask toggle is now an
//     "Ans" key. It inserts the last answer (the result of
//     the last "=") at the cursor. No tap opens the scientific
//     keys any more.
//  4. PULL-DOWN scientific keys: drag the keypad DOWN and the
//     keys slide down like a drawer, revealing the hidden
//     scientific keys (the ones the flask used to show);
//     drag UP to go back to digits. The mode still uses the
//     same scientificExpanded state and the same key rows.
//  5. BLACK / WHITE ONLY: the theme's primary (the only
//     chromatic colour) is gone from this screen - the live
//     result, the caret, Clear, the page dots, the selection
//     handle and the = / backspace keys now use plain
//     black / white. The grey keys were already black-white.
//  6. FULLY ROUND KEYS: every key is a perfect circle now.
//     Each key measures its own cell (BoxWithConstraints)
//     and draws a circle with the smaller side as the
//     diameter, so it stays round on any phone.
//  NO function changed otherwise: vault secret, selection,
//  history, E notation, operator handling all as in LIFTED.
package com.calnyx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.vault.VaultPrefs
import com.calnyx.app.viewmodel.CalculatorViewModel
import com.calnyx.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalCalculatorScreen(
        darkTheme: Boolean,
        onToggleTheme: () -> Unit,
        onOpenHistory: () -> Unit,
        onHistoryVisibilityChange: (Boolean) -> Unit = {},
        onOpenVault: () -> Unit = {},
        onOpenVaultSetup: () -> Unit = {},
        viewModel: CalculatorViewModel = viewModel(),
        historyViewModel: HistoryViewModel = viewModel()
) {
        // darkTheme kept for the NavGraph call; onToggleTheme / onOpenHistory
        // are kept for the same call — the floating top bar lives in NavGraph.
        // History no longer opens over the top bar, so it is never hidden.
        // There is NO icon row of our own at the top of this screen.
        val context = LocalContext.current
        val expression by viewModel.expression.collectAsState()
        val cursorPosition by viewModel.cursorPosition.collectAsState()
        val result by viewModel.result.collectAsState()
        val finalResult by viewModel.finalResult.collectAsState()
        val history by historyViewModel.history.collectAsState()
        var scientificExpanded by remember { mutableStateOf(false) }

        // PULL-DOWN slide animation for the keypad: when the
        // user drags the keypad down/up, scientificExpanded
        // flips and the keys slide in like a drawer (from
        // above when opening, from below when closing).
        // Skipped on the very first composition so the keys
        // do not slide in when the app opens.
        val slideY = remember { Animatable(0f) }
        var firstSlide by remember { mutableStateOf(true) }
        LaunchedEffect(scientificExpanded) {
                if (firstSlide) {
                        firstSlide = false
                        return@LaunchedEffect
                }
                slideY.snapTo(if (scientificExpanded) -90f else 90f)
                slideY.animateTo(0f, animationSpec = tween(250))
        }
        var pullAccum by remember { mutableFloatStateOf(0f) }

        // The keypad area is a 2-page horizontal pager: page 0 is the
        // keypad, page 1 is the history. Swiping left/right over the
        // buttons swaps them in place — the display panel and the strip
        // above never move. There is NO swipe-down history any more.
        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()

        // Text selection over the expression, in RAW character indices.
        // Long-press selects the token under the finger; the two handles
        // drag to extend it. While a selection is live, the next key
        // replaces it — exactly like a text field.
        var selActive by remember { mutableStateOf(false) }
        var selStart by remember { mutableIntStateOf(0) }
        var selEnd by remember { mutableIntStateOf(0) }

        // Secret setup — nav-e kono option nei, tai "=" long-press
        // korlei setup khulbe. Eta sob somoy khulbe:
        //  - prothombar (secret set nei): purono secret lagbe na,
        //    sudhu notun secret + confirm diye Save.
        //  - pore bodlate chaile: setup screen-e purono secret
        //    dite hobe, na hole bodlano jabe na.
        // Tap "=" = vault khola, long-press "=" = setup khola —
        // ei parthokko-ta mone rekho.
        fun openVaultSetupIfNeeded() {
                onOpenVaultSetup()
        }

        fun handleKeyLabel(label: String) {
                // SECRET VAULT: expression-টা সিক্রেটের সাথে মিললে
                // হিসাব না করে vault খুলবে, history-তেও যাবে না।
                // Own secret set hoye gele sudhu setai kaj korbe,
                // tar age purono default-tao kaj korbe (migration).
                if (label == "=" && VaultPrefs.matches(context, expression.trim())) {
                        viewModel.onButtonClick("AC")
                        // APP LOCK UNLOCK: kono locked app block hoye thakle
                        // Vault na khule sei app-tai khulbe. Pending na
                        // thakle ager moto Vault khulbe.
                        val pending = AppLockManager.getPending(context)
                        if (pending != null && AppLockManager.isEnabled(context) && AppLockManager.isLocked(context, pending)) {
                                // SESSION: timer ekhan theke cholbe na. Ekhon theke
                                // jotokhon user ei app-er moddhei thakbe, lock hobe na.
                                // Ber hole service nijei grace timer suru korbe.
                                AppLockManager.setUnlockedSession(context, pending)
                                AppLockManager.clearPending(context)
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(pending)
                                if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                } else {
                                        // App-ta khola gelo na (uninstall ityadi) —
                                        // session rekhe dile pore abar secret chara khulto.
                                        AppLockManager.clearUnlockedSession(context)
                                        onOpenVault()
                                }
                        } else {
                                onOpenVault()
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

        // A selection never outlives the expression it points into.
        LaunchedEffect(expression) {
                if (selActive && (selStart > expression.length || selEnd > expression.length)) {
                        selActive = false
                }
        }
        LaunchedEffect(finalResult) { if (finalResult != null) selActive = false }

        // History lives inside the keypad area now, so the top bar
        // stays visible at all times.
        LaunchedEffect(Unit) { onHistoryVisibilityChange(false) }

        // NO ANIMATIONS and NO state-driven resizing: the display panel
        // weight and the key font scale are plain constants, and every
        // content switch uses EnterTransition.None / ExitTransition.None.
        //
        // The panel weight is 0.45 vs the pager's 1f — a bigger panel
        // and slightly shorter keys than the first filled layout.
        val displayWeight = 0.55f
        val keyFontScale = 1f

        // ADAPTIVE display font, Google Calculator style:
        // starts big (78sp typing, 86sp answer). When the text
        // gets longer than what fits, it shrinks 2sp at a time,
        // never below 52sp. After 52sp it stays FIXED at that
        // size and further typing only scrolls horizontally —
        // it never keeps shrinking to tiny. Deleting characters
        // grows it back, because the size is computed from the
        // current text length, not from a shrinking state.
        // LIFTED: both lines sit higher in the panel (bigger
        // bottom spacer, taller preview strip), so the live
        // result never touches the panel's bottom edge.
        val expressionFontSize = 78.sp
        val finalFontSize = 86.sp
        val previewFontSize = 36.sp

        val groupedHistory = remember(history) { history.groupBy { dayLabel(it.createdAt) } }
        val groupedEntries = remember(groupedHistory) { groupedHistory.entries.toList() }

        // Clearance for the floating top bar drawn by NavGraph — the top
        // bar is always visible now, so this never shrinks.
        val topSpacerHeight = 56.dp

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Column(
                        modifier =
                                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                ) {
                        Column(modifier = Modifier.fillMaxSize()) {

                                // DISPLAY PANEL — one big panel across the top, square
                                // at the top, big rounded corners (28dp) at the bottom
                                // only, exactly like the reference. Inside: the
                                // expression area on top (fixed 72sp, scrollable) and
                                // a RESERVED 48dp answer line under it that is always
                                // there, so the preview can never be squeezed out or
                                // clipped by the panel again.
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .weight(displayWeight)
                                                        .clip(
                                                                RoundedCornerShape(
                                                                        bottomStart = 28.dp,
                                                                        bottomEnd = 28.dp
                                                                )
                                                        )
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant
                                                        )
                                ) {
                                        // Clearance for the floating top bar drawn by NavGraph
                                        Spacer(modifier = Modifier.height(topSpacerHeight))

                                        // Expression / final-answer area
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .weight(1f)
                                                                .padding(horizontal = 20.dp)
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
                                                                // Completed calculation: only the
                                                                // answer,
                                                                // big — FIXED 80sp, the line
                                                                // scrolls to
                                                                // keep the end visible when it is
                                                                // long
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
                                                                        // 86sp theke suru, 7 char-er
                                                                        // beshi hole 2sp kore kombe,
                                                                        // 52sp-e giye theme jabe.
                                                                        val adaptiveFinalFontSize =
                                                                                remember(formattedFinal) {
                                                                                        val excess =
                                                                                                (formattedFinal.length - 7)
                                                                                                        .coerceAtLeast(0)
                                                                                        (finalFontSize.value - 2f * excess)
                                                                                                .coerceAtLeast(52f).sp
                                                                                }
                                                                        val finalScrollState =
                                                                                rememberScrollState()
                                                                        LaunchedEffect(final) {
    // FIX: answer-এর প্রথম অংশ দেখাবে, শেষে নয়।
    finalScrollState.scrollTo(0)
}
                        // ANSWER COPY: final answer select + copy-র জন্য।
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
 } // end SelectionContainer
                                                                        }
 Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.End
 ) {
     TextButton(onClick = {
         answerClipboard.setText(AnnotatedString(formattedFinal))
         answerCopied = true
     }) {
         Text(if (answerCopied) "Copied ✓" else "Copy", fontSize = 14.sp)
     }
 }
                                                                }
                                                        } else {
                                                                Column(
                                                                        modifier =
                                                                                Modifier.fillMaxSize(),
                                                                        verticalArrangement =
                                                                                Arrangement.Bottom,
                                                                        horizontalAlignment =
                                                                                Alignment.End
                                                                ) {
                                                                        // Tap-to-edit: the visible
                                                                        // text is
                                                                        // formatted (commas, ×, √)
                                                                        // while the
                                                                        // stored expression is raw
                                                                        // ASCII.
                                                                        // formatWithMap keeps a
                                                                        // boundary map
                                                                        // between the two, so a tap
                                                                        // lands the
                                                                        // caret on the exact
                                                                        // character. The
                                                                        // caret itself is drawn by
                                                                        // the Text,
                                                                        // inside its own draw pass
                                                                        // (see below).
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

                                                                        // Filled by the Text's
                                                                        // onTextLayout in
                                                                        // its layout phase; read by
                                                                        // the Text's
                                                                        // own draw phase in the
                                                                        // same frame.
                                                                        val caretHolder = remember {
                                                                                CaretLayoutHolder()
                                                                        }

                                                                        // The caret no longer
                                                                        // blinks — it is
                                                                        // drawn solid in the draw
                                                                        // pass below.
                                                                        val caretColor =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                        val clipboardManager =
                                                                                LocalClipboardManager
                                                                                        .current

                                                                        val scrollState =
                                                                                rememberScrollState()
                                                                        // Keep the caret visible.
                                                                        // Following
                                                                        // the end while typing is
                                                                        // an INSTANT
                                                                        // scrollTo — an
                                                                        // animateScrollTo per
                                                                        // keystroke queued up
                                                                        // behind fast
                                                                        // typing and made the
                                                                        // screen laggy.
                                                                        // Only a caret placed
                                                                        // mid-expression
                                                                        // by a tap still animates
                                                                        // into view.
                                                                        LaunchedEffect(
                                                                                expression,
                                                                                cursorPosition
                                                                        ) {
                                                                                val lr =
                                                                                        expressionLayout
                                                                                if (expression
                                                                                                .isEmpty()
                                                                                ) {
                                                                                        // nothing
                                                                                        // to scroll
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

                                                                        // Selection action bar —
                                                                        // Copy takes
                                                                        // the FORMATTED slice
                                                                        // (commas and
                                                                        // glyphs), so it pastes
                                                                        // prettily
                                                                        // anywhere; paste is
                                                                        // sanitised back
                                                                        // by the ViewModel.
                                                                        AnimatedVisibility(
                                                                                visible = selActive,
                                                                                enter =
                                                                                        EnterTransition
                                                                                                .None,
                                                                                exit =
                                                                                        ExitTransition
                                                                                                .None,
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                        ) {
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth(),
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .Center
                                                                                ) {
                                                                                        Row(
                                                                                                modifier =
                                                                                                        Modifier.clip(
                                                                                                                        RoundedCornerShape(
                                                                                                                                percent =
                                                                                                                                        50
                                                                                                                        )
                                                                                                                )
                                                                                                                .background(
                                                                                                                        // BLACK / WHITE ONLY: the selection
                                                                                                                        // bar was secondaryContainer, which
                                                                                                                        // is a tinted colour in the theme.
                                                                                                                        MaterialTheme
                                                                                                                                .colorScheme
                                                                                                                                .surfaceVariant
                                                                                                                )
                                                                                                                .padding(
                                                                                                                        horizontal =
                                                                                                                                6.dp
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
                                                                                                        ) {
                                                                                                                Text(
                                                                                                                        "Copy",
                                                                                                                        fontSize =
                                                                                                                                14.sp
                                                                                                                )
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
                                                                                                        ) {
                                                                                                                Text(
                                                                                                                        "Cut",
                                                                                                                        fontSize =
                                                                                                                                14.sp
                                                                                                                )
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
                                                                                                ) {
                                                                                                        Text(
                                                                                                                "Paste",
                                                                                                                fontSize =
                                                                                                                        14.sp
                                                                                                        )
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
                                                                                                        ) {
                                                                                                                Text(
                                                                                                                        "All",
                                                                                                                        fontSize =
                                                                                                                                14.sp
                                                                                                                )
                                                                                                        }
                                                                                                }
                                                                                                TextButton(
                                                                                                        onClick = {
                                                                                                                selActive =
                                                                                                                        false
                                                                                                        }
                                                                                                ) {
                                                                                                        Text(
                                                                                                                "Done",
                                                                                                                fontSize =
                                                                                                                        14.sp
                                                                                                        )
                                                                                                }
                                                                                        }
                                                                                }
                                                                        }
                                                                        if (selActive) {
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.height(
                                                                                                        4.dp
                                                                                                )
                                                                                )
                                                                        }

                                                                        // Single line only — never
                                                                        // wraps. ADAPTIVE: 78sp theke
                                                                        // suru, 8 char-er beshi hole
                                                                        // 2sp kore kombe, 52sp-e giye
                                                                        // theme jabe, tarpor shudhu
                                                                        // scroll hobe, ar choto hobe na.
                                                                        val adaptiveExpressionFontSize =
                                                                                remember(formatted.text) {
                                                                                        val excess =
                                                                                                (formatted.text.length - 8)
                                                                                                        .coerceAtLeast(0)
                                                                                        (expressionFontSize.value - 2f * excess)
                                                                                                .coerceAtLeast(52f).sp
                                                                                }
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
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
                                                                                                                                56.dp
                                                                                                                )
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
                                                                                                                        // Selection highlight and
                                                                                                                        // caret, drawn by the Text
                                                                                                                        // itself in the same frame
                                                                                                                        // whose layout is in the
                                                                                                                        // holder — always in sync
                                                                                                                        // with the glyphs. The
                                                                                                                        // caret
                                                                                                                        // is a THIN line (2.5dp),
                                                                                                                        // at least 52dp tall (an
                                                                                                                        // empty expression gives
                                                                                                                        // the Text almost no
                                                                                                                        // height, which used to
                                                                                                                        // shrink the caret to a
                                                                                                                        // stub), drawn solid
                                                                                                                        // (no blink) — like the
                                                                                                                        // reference.
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
                                                                                                                                                        0f
                                                                                                                                                } else {
                                                                                                                                                        lr.getHorizontalPosition(
                                                                                                                                                                caretOffset,
                                                                                                                                                                true
                                                                                                                                                        )
                                                                                                                                                }
                                                                                                                                        val caretHeight =
                                                                                                                                                max(
                                                                                                                                                        size.height *
                                                                                                                                                                0.95f,
                                                                                                                                                        52.dp.toPx()
                                                                                                                                                )
                                                                                                                                        drawRect(
                                                                                                                                                color =
                                                                                                                                                        caretColor,
                                                                                                                                                topLeft =
                                                                                                                                                        Offset(
                                                                                                                                                                cx -
                                                                                                                                                                        1.25.dp
                                                                                                                                                                                .toPx(),
                                                                                                                                                                (size.height -
                                                                                                                                                                        caretHeight) /
                                                                                                                                                                        2f
                                                                                                                                                        ),
                                                                                                                                                size =
                                                                                                                                                        Size(
                                                                                                                                                                2.5.dp
                                                                                                                                                                        .toPx(),
                                                                                                                                                                caretHeight
                                                                                                                                                        )
                                                                                                                                        )
                                                                                                                                }
                                                                                                                        }
                                                                                                                }
                                                                                        )

                                                                                        // Drag
                                                                                        // handles
                                                                                        // at both
                                                                                        // ends of
                                                                                        // the
                                                                                        // selection
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
                                                                                                                modifier =
                                                                                                                        Modifier
                                                                                                                                .offset {
                                                                                                                                        IntOffset(
                                                                                                                                                (startX -
                                                                                                                                                                10.dp.toPx())
                                                                                                                                                        .roundToInt(),
                                                                                                                                                (lr.size
                                                                                                                                                                .height -
                                                                                                                                                                10.dp.toPx())
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
                                                                                                                modifier =
                                                                                                                        Modifier
                                                                                                                                .offset {
                                                                                                                                        IntOffset(
                                                                                                                                                (endX -
                                                                                                                                                                10.dp.toPx())
                                                                                                                                                        .roundToInt(),
                                                                                                                                                (lr.size
                                                                                                                                                                .height -
                                                                                                                                                                10.dp.toPx())
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
                                                                        }
                                                                }
                                                        }
                                                }
                                        }

                                        // Answer preview line — a RESERVED 56dp strip of
                                        // its own, always present at the panel's bottom,
                                        // so the answer is never pushed out or clipped.
                                        // The line scrolls horizontally like the others
                                        // and follows the end as the answer grows.
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .height(56.dp)
                                                                .padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                        ) {
                                                if (finalResult == null &&
                                                                result != "0" &&
                                                                expression.isNotEmpty()
                                                ) {
                                                        val previewScrollState =
                                                                rememberScrollState()
                                                        LaunchedEffect(result) {
    // FIX: preview-ও প্রথম অংশ থেকে দেখাবে।
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
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface,
                                                                        textAlign = TextAlign.End,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                )
                                                        }
                                                }
                                        }

                                        Spacer(modifier = Modifier.height(24.dp))
                                }

                                // Scientific strip — ONE long pill under the panel
                                // (√, π, xⁿ, E) plus a SEPARATE round ANS button
                                // beside it. Ans inserts the last answer at the
                                // cursor. The scientific keys are no longer behind
                                // a tap: PULL the keypad DOWN to reveal them (see
                                // the drag detector on the keypad below), pull UP
                                // to go back to digits.
                                // The strip stays put when history is showing.
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 8.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.weight(1f)
                                                                .clip(
                                                                        RoundedCornerShape(
                                                                                percent = 50
                                                                        )
                                                                )
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                listOf(
                                                                "√" to "√",
                                                                "π" to "π",
                                                                "xⁿ" to "^",
                                                                "E" to "E"
                                                        )
                                                        .forEach { (displayText, label) ->
                                                                Box(
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                                        .clickable {
                                                                                                handleKeyLabel(
                                                                                                        label
                                                                                                )
                                                                                        }
                                                                                        .padding(
                                                                                                vertical =
                                                                                                        10.dp
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        Text(
                                                                                text = displayText,
                                                                                fontSize = 22.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Medium,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface
                                                                        )
                                                                }
                                                        }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                                modifier =
                                                        Modifier.size(52.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                )
                                                                .clickable {
                                                                        // ANS: insert the last answer (the result
                                                                        // of the last "=") at the cursor. If there
                                                                        // is no answer yet, do nothing.
                                                                        val ans = finalResult
                                                                                ?: if (expression.isNotBlank()) result else null
                                                                        if (!ans.isNullOrBlank()) handleKeyLabel(ans)
                                                                },
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = "Ans",
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurface
                                                )
                                        }
                                }

                                // SWAP AREA — keypad ⇄ history. Swipe left/right over
                                // the buttons: the keypad slides away and the history
                                // shows RIGHT HERE, in this same area, at full height.
                                // Page 0 = keypad, page 1 = history.
                                HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxWidth().weight(1f)
                                ) { page ->
                                        if (page == 0) {

                                                // Keypad — 4 columns x 5 rows, two modes.
                                                // NUMBER mode: round keys like the reference.
                                                // SCIENTIFIC mode: the AC row and the right-hand
                                                // operator column (÷, ×, −, +, ⌫, =) stay as
                                                // round keys, and the digits are swapped for
                                                // scientific text keys (|x| ln log /
                                                // sin cos tan / sin⁻¹ cos⁻¹ tan⁻¹ / e eˣ).
                                                // The rows SHARE the whole area equally and
                                                // fill it completely, strip to bottom, exactly
                                                // like the reference — no fixed heights, no
                                                // dead gaps. Each entry is (display, label).
                                                // PULL-DOWN: drag the keypad DOWN to reveal
                                                // the scientific keys, drag UP to go back to
                                                // digits. The keys slide down like a drawer
                                                // when they change (slideY above).
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .graphicsLayer { translationY = slideY.value }
                                                                        .padding(
                                                                                horizontal = 12.dp
                                                                        )
                                                                        .pointerInput(scientificExpanded) {
                                                                                val threshold = 40.dp.toPx()
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
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        if (!scientificExpanded) {
                                                                val buttons:
                                                                        List<
                                                                                List<
                                                                                        Pair<
                                                                                                String,
                                                                                                String>>> =
                                                                        listOf(
                                                                                listOf(
                                                                                        "AC" to
                                                                                                "AC",
                                                                                        "()" to
                                                                                                "()",
                                                                                        "%" to "%",
                                                                                        "÷" to "÷"
                                                                                ),
                                                                                listOf(
                                                                                        "7" to "7",
                                                                                        "8" to "8",
                                                                                        "9" to "9",
                                                                                        "×" to "×"
                                                                                ),
                                                                                listOf(
                                                                                        "4" to "4",
                                                                                        "5" to "5",
                                                                                        "6" to "6",
                                                                                        "−" to "−"
                                                                                ),
                                                                                listOf(
                                                                                        "1" to "1",
                                                                                        "2" to "2",
                                                                                        "3" to "3",
                                                                                        "+" to "+"
                                                                                ),
                                                                                listOf(
                                                                                        "0" to "0",
                                                                                        "." to ".",
                                                                                        "⌫" to "⌫",
                                                                                        "=" to "="
                                                                                )
                                                                        )

                                                                buttons.forEachIndexed { _, row ->
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                                                .weight(
                                                                                                        1f
                                                                                                ),
                                                                                horizontalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        8.dp
                                                                                                ),
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                row.forEach {
                                                                                        (
                                                                                                displayText,
                                                                                                label)
                                                                                        ->
                                                                                        CalculatorButton(
                                                                                                text =
                                                                                                        displayText,
                                                                                                modifier =
                                                                                                        Modifier.weight(
                                                                                                                        1f
                                                                                                                )
                                                                                                                .fillMaxHeight(),
                                                                                                isOperator =
                                                                                                        displayText in
                                                                                                                listOf(
                                                                                                                        "()",
                                                                                                                        "%",
                                                                                                                        "÷",
                                                                                                                        "×",
                                                                                                                        "−",
                                                                                                                        "+"
                                                                                                                ),
                                                                                                fontScale =
                                                                                                        keyFontScale,
                                                                                                onLongClick =
                                                                                                        if (displayText ==
                                                                                                                        "="
                                                                                                        ) {
                                                                                                                {
                                                                                                                        openVaultSetupIfNeeded()
                                                                                                                }
                                                                                                        } else
                                                                                                                null,
                                                                                                onClick = {
                                                                                                        handleKeyLabel(
                                                                                                                label
                                                                                                        )
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                        } else {
                                                                // Row 1 — same key row as number
                                                                // mode
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        CalculatorButton(
                                                                                text = "AC",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "AC"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "()",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "()"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "%",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "%"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "÷",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "÷"
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                                // Row 2 — |x| ln log as text, × as
                                                                // a key
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        ScientificTextKey(
                                                                                text = "|x|",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "abs"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "ln",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "ln"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "log",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "log"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "×",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "×"
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                                // Row 3 — sin cos tan as text, − as
                                                                // a key
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        ScientificTextKey(
                                                                                text = "sin",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "sin"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "cos",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "cos"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "tan",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "tan"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "−",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "−"
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                                // Row 4 — inverse trig as text, +
                                                                // as a key
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        ScientificTextKey(
                                                                                text = "sin⁻¹",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "asin"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "cos⁻¹",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "acos"
                                                                                        )
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "tan⁻¹",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "atan"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "+",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                isOperator = true,
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "+"
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                                // Row 5 — ! and eˣ as text, ⌫ and =
                                                                // as keys
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .weight(1f),
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        ScientificTextKey(
                                                                                text = "!",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel("!")
                                                                                }
                                                                        )
                                                                        ScientificTextKey(
                                                                                text = "eˣ",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "exp"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "⌫",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "⌫"
                                                                                        )
                                                                                }
                                                                        )
                                                                        CalculatorButton(
                                                                                text = "=",
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                        1f
                                                                                                )
                                                                                                .fillMaxHeight(),
                                                                                fontScale =
                                                                                        keyFontScale,
                                                                                onLongClick = {
                                                                                        openVaultSetupIfNeeded()
                                                                                },
                                                                                onClick = {
                                                                                        handleKeyLabel(
                                                                                                "="
                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }
                                        } else {

                                                // History page — fills the whole swap area.
                                                // The day label is CENTRED and the entries are
                                                // LEFT-aligned, like the reference's history
                                                // mode. Tapping an entry loads its result and
                                                // snaps back to the keypad page.
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxSize()
                                                                        .padding(horizontal = 8.dp)
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
                                                                                                        20.dp
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
                                                                                                                                        10.dp,
                                                                                                                                bottom =
                                                                                                                                        10.dp
                                                                                                                        )
                                                                                                ) {
                                                                                                        Text(
                                                                                                                text =
                                                                                                                        day,
                                                                                                                fontSize =
                                                                                                                        15.sp,
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
                                                                                                        // Clear stays at the end of the
                                                                                                        // first group header ("Today")
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
                                                                                                                                        14.sp,
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
                                                                                                                                                                0
                                                                                                                                                        )
                                                                                                                                        }
                                                                                                                        }
                                                                                                                        .padding(
                                                                                                                                vertical =
                                                                                                                                        10.dp
                                                                                                                        ),
                                                                                                        horizontalAlignment =
                                                                                                                Alignment
                                                                                                                        .Start
                                                                                                ) {
                                                                                                        Text(
    text = formatForDisplay(item.expression),
    fontSize = 20.sp,
    lineHeight = 26.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Start,
    softWrap = true,
    modifier = Modifier.fillMaxWidth()
)
Spacer(modifier = Modifier.height(6.dp))
Text(
    text = formatForDisplay(item.result),
    fontSize = 34.sp,
    lineHeight = 42.sp,
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
                                                                                                                                8.dp
                                                                                                                )
                                                                                                )
                                                                                        }
                                                                                }
                                                                }
                                                        }
                                                }
                                        }
                                }

                                // Page dots — which side of the swap area is showing
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(top = 4.dp, bottom = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        repeat(2) { index ->
                                                val active = pagerState.currentPage == index
                                                Box(
                                                        modifier =
                                                                Modifier.padding(horizontal = 3.dp)
                                                                        .size(
                                                                                if (active) 7.dp
                                                                                else 6.dp
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
                }
        }
}

// One selection drag handle: a filled circle the user pulls along
// the expression; every drag position is reported back as a target
// X in the Text's own coordinate space.
@Composable
private fun SelectionHandle(
        currentX: Float,
        modifier: Modifier = Modifier,
        onDragTo: (Float) -> Unit
) {
        val currentXState by rememberUpdatedState(currentX)
        val dragToState by rememberUpdatedState(onDragTo)
        Box(
                modifier =
                        modifier.size(20.dp)
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

// The token under a long-press, as (start, endExclusive) in raw
// indices: a whole number run, a function name with its "(", or a
// single operator / parenthesis character.
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

// One scientific TEXT key (scientific mode only): now with the
// same background + border as other keys (FIX). Long labels
// (sin⁻¹, cos⁻¹, tan⁻¹) get a smaller font so they fit the cell.
@Composable
private fun ScientificTextKey(
        text: String,
        modifier: Modifier = Modifier,
        fontScale: Float = 1f,
        onClick: () -> Unit
) {
        val interactionSource = remember { MutableInteractionSource() }
        // FULLY ROUND key: the cell is a rectangle, so the key
        // measures its own cell (BoxWithConstraints) and draws
        // a circle with the smaller side as the diameter - a
        // perfect circle on any phone. Black / white only.
        BoxWithConstraints(
                modifier = modifier,
                contentAlignment = Alignment.Center
        ) {
                val size = maxWidth.coerceAtMost(maxHeight)
                Box(
                        modifier =
                                Modifier.size(size)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                                CircleShape
                                        )
                                        .clickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = onClick
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                text = text,
                                fontSize =
                                        if (text.length >= 4) (20f * fontScale).sp
                                        else (24f * fontScale).sp,
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
        fontScale: Float = 1f,
        onLongClick: (() -> Unit)? = null,
        onClick: () -> Unit
) {
        // Colours - BLACK / WHITE ONLY: every key is the same
        // container colour, except ⌫ and =, which are filled
        // with the opposite tone (black in light mode, white
        // in dark mode). No accent colour anywhere.
        val backgroundColor =
                if (text == "=" || text == "⌫") {
                        MaterialTheme.colorScheme.onSurface
                } else {
                        MaterialTheme.colorScheme.surfaceVariant
                }
        val contentColor =
                if (text == "=" || text == "⌫") {
                        MaterialTheme.colorScheme.surface
                } else {
                        MaterialTheme.colorScheme.onSurface
                }

        val interactionSource = remember { MutableInteractionSource() }

        // FULLY ROUND key: the cell is a rectangle, so the key
        // measures its own cell (BoxWithConstraints) and draws
        // a circle with the smaller side as the diameter - a
        // perfect circle on any phone. No press animation, no
        // colour flash. (The compact parameter is kept for
        // call-site compatibility; sizing is driven by fontScale now.)
        BoxWithConstraints(
                modifier = modifier,
                contentAlignment = Alignment.Center
        ) {
                val size = maxWidth.coerceAtMost(maxHeight)
                Box(
                        modifier =
                                Modifier.size(size)
                                        .clip(CircleShape)
                                        .background(backgroundColor)
                                        .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = null,
                                                onClick = onClick,
                                                onLongClick = onLongClick
                                        ),
                        contentAlignment = Alignment.Center
                ) { CalculatorButtonContent(text, contentColor, fontScale) }
        }
}

@Composable
private fun CalculatorButtonContent(text: String, contentColor: Color, fontScale: Float) {
        if (text == "⌫") {
                Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete",
                        tint = contentColor,
                        modifier = Modifier.size((34f * fontScale).dp.coerceAtLeast(20.dp))
                )
        } else {
                Text(
                        text = if (text == "()") "( )" else text,
                        color = contentColor,
                        fontSize =
                                when {
                                        text == "AC" || text == "()" || text == "Exp" ->
                                                (25f * fontScale).sp
                                        text == "%" -> (29f * fontScale).sp
                                        text.length > 1 -> (21f * fontScale).sp
                                        else -> (37f * fontScale).sp
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

// Plain (non-snapshot) holder for the expression's latest text
// layout: the Text writes it in its layout phase and reads it back
// in its own draw phase of the same frame.
private class CaretLayoutHolder {
        var layout: TextLayoutResult? = null
}

// The formatted expression plus the boundary map in both directions:
// fmtToRaw[f] = the raw boundary at formatted boundary f, and
// rawToFmt[r] = the formatted boundary for raw boundary r. The text
// produced is exactly formatForDisplay's output. Multi-character
// tokens (sqrt(, log10(, pi) behave atomically — boundaries inside
// them point at the token start — and grouping commas consume no
// raw position.
private data class FormattedExpression(
        val text: String,
        val fmtToRaw: IntArray,
        val rawToFmt: IntArray
)

private fun formatWithMap(raw: String): FormattedExpression {
        val sb = StringBuilder()
        val fmtToRaw = mutableListOf(0)

        // Append one display token: interior boundaries point at the
        // token's raw start, the final boundary at its raw end.
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

        // Reverse map: each raw boundary takes the LAST formatted
        // boundary that does not pass it (so a caret after a digit sits
        // after any grouping comma before that digit).
        val rawToFmt = IntArray(raw.length + 1)
        var f = 0
        for (r in 0..raw.length) {
                while (f + 1 < fmtToRaw.size && fmtToRaw[f + 1] <= r) f++
                rawToFmt[r] = f
        }

        return FormattedExpression(sb.toString(), fmtToRaw.toIntArray(), rawToFmt)
}