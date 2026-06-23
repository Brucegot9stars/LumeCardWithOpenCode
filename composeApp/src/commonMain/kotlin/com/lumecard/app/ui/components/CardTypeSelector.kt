package com.lumecard.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumecard.app.i18n.I18nStrings
import com.lumecard.app.ui.theme.LumeCardTheme
import com.lumecard.shared.model.CardType

/**
 * Visual grid selector for card types.
 * Displays all card types as selectable cards with emoji + name.
 */
@Composable
fun CardTypeSelector(
    selectedType: CardType,
    onTypeSelected: (CardType) -> Unit,
    strings: I18nStrings,
    modifier: Modifier = Modifier,
) {
    val spacing = LumeCardTheme.spacing
    val radius = LumeCardTheme.radius

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = PaddingValues(spacing.sm),
    ) {
        items(CardType.entries, key = { it.name }) { type ->
            val isSelected = type == selectedType
            val cardColors = if (isSelected) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            } else {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                )
            }
            val border = if (isSelected) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTypeSelected(type) },
                shape = RoundedCornerShape(radius.sm),
                colors = cardColors,
                border = border,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = cardTypeEmoji(type),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cardTypeShortName(type, strings),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun cardTypeEmoji(type: CardType): String = when (type) {
    CardType.BASIC -> "\uD83D\uDCDD"
    CardType.REVERSED -> "\uD83D\uDD04"
    CardType.CLOZE -> "\uD83D\uDD24"
    CardType.MULTIPLE_CHOICE -> "\u2705"
    CardType.MARKDOWN -> "\uD83D\uDCC4"
    CardType.AI_GENERATED -> "\uD83E\uDD16"
}

private fun cardTypeShortName(type: CardType, strings: I18nStrings): String = when (type) {
    CardType.BASIC -> strings.cardTypeBasic
    CardType.REVERSED -> strings.cardTypeReversed
    CardType.CLOZE -> strings.cardTypeCloze
    CardType.MULTIPLE_CHOICE -> strings.cardTypeChoice
    CardType.MARKDOWN -> "Markdown"
    CardType.AI_GENERATED -> "AI"
}

