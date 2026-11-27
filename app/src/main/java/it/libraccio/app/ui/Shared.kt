@file:OptIn(ExperimentalSharedTransitionApi::class)

package it.libraccio.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** La copertina "vola" tra le schermate: stessa chiave nello scaffale, nell'elenco e nella scheda. */
@Composable
fun Modifier.sharedCover(bookId: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val anim = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedCover.sharedElement(
            state = rememberSharedContentState(key = "cover-$bookId"),
            animatedVisibilityScope = anim,
        )
    }
}
