package com.lumecard.app.ui.screens.help

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.ui.theme.LumeCardTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class TourPage(
    val icon: @Composable () -> Unit,
    val title: String,
    val description: String,
)

@Composable
fun WelcomeTourContent(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit,
) {
    val strings = koinInject<I18nManager>().strings
    val spacing = LumeCardTheme.spacing

    val pages = remember {
        listOf(
            TourPage(
                icon = {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = strings.welcomeTourPage1Title,
                description = strings.welcomeTourPage1Desc,
            ),
            TourPage(
                icon = {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = strings.welcomeTourPage2Title,
                description = strings.welcomeTourPage2Desc,
            ),
            TourPage(
                icon = {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = strings.welcomeTourPage3Title,
                description = strings.welcomeTourPage3Desc,
            ),
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onSkip) {
                Text(strings.welcomeTourSkip)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                page.icon()
                Spacer(Modifier.height(spacing.lg))
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(spacing.md))
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Page indicators
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                val color = if (pagerState.currentPage == index)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.width(1.dp))
            Button(
                onClick = {
                    if (isLastPage) {
                        onGetStarted()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                modifier = Modifier,
            ) {
                Text(if (isLastPage) strings.welcomeTourGetStarted else strings.welcomeTourNext)
                if (!isLastPage) {
                    Spacer(Modifier.width(spacing.xs))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
