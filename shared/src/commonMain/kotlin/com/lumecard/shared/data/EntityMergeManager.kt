package com.lumecard.shared.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.first

class EntityMergeManager(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
) {

    suspend fun mergeKnowledgeBases(
        sourceId: String,
        targetId: String,
    ): Result<MoveMergeResult> {
        if (sourceId == targetId) {
            return Result.failure(IllegalArgumentException("Cannot merge a knowledge base into itself"))
        }

        val source = knowledgeBaseRepository.getById(sourceId)
            ?: return Result.failure(IllegalArgumentException("Source knowledge base not found"))
        val target = knowledgeBaseRepository.getById(targetId)
            ?: return Result.failure(IllegalArgumentException("Target knowledge base not found"))

        val sourceDecks = deckRepository.getByKnowledgeBase(sourceId).first()
        val targetDeckNames = deckRepository.getByKnowledgeBase(targetId).first().map { it.name }.toSet()

        val conflicts = mutableListOf<String>()
        var itemsMoved = 0

        for (deck in sourceDecks) {
            when {
                deck.name in targetDeckNames -> {
                    val newName = findUniqueDeckName(targetId, deck.name)
                    val updatedDeck = deck.copy(
                        knowledgeBaseId = targetId,
                        name = newName,
                        updatedAt = Clock.System.now(),
                    )
                    deckRepository.update(updatedDeck)
                    conflicts.add("Deck \"${deck.name}\" renamed to \"$newName\"")
                }
                else -> {
                    deckRepository.update(deck.copy(
                        knowledgeBaseId = targetId,
                        updatedAt = Clock.System.now(),
                    ))
                }
            }
            itemsMoved++
        }

        knowledgeBaseRepository.delete(sourceId)

        return Result.success(MoveMergeResult(
            operationType = EntityOperationType.KB_MERGE,
            sourceId = sourceId,
            targetId = targetId,
            itemsMoved = itemsMoved,
            conflictsResolved = conflicts.size,
            sourceDeleted = true,
            conflictMessages = conflicts,
        ))
    }

    suspend fun mergeDecks(
        sourceId: String,
        targetId: String,
    ): Result<MoveMergeResult> {
        if (sourceId == targetId) {
            return Result.failure(IllegalArgumentException("Cannot merge a deck into itself"))
        }

        val sourceDeck = deckRepository.getById(sourceId)
            ?: return Result.failure(IllegalArgumentException("Source deck not found"))
        val targetDeck = deckRepository.getById(targetId)
            ?: return Result.failure(IllegalArgumentException("Target deck not found"))

        val sourceCards = cardRepository.getByDeck(sourceId).first()
        val targetCardFronts = cardRepository.getByDeck(targetId).first().map { it.front }.toSet()

        var itemsMoved = 0
        var conflictsResolved = 0

        for (card in sourceCards) {
            val newFront = when {
                card.front in targetCardFronts -> resolveCardFrontConflict(card.front, targetCardFronts)
                card.front.isBlank() -> generateDefaultCardName(itemsMoved)
                else -> card.front
            }
            val updatedCard = card.copy(
                deckId = targetId,
                front = newFront,
                updatedAt = Clock.System.now(),
            )
            cardRepository.update(updatedCard)
            if (newFront != card.front) conflictsResolved++
            itemsMoved++
        }

        deckRepository.delete(sourceId)

        return Result.success(MoveMergeResult(
            operationType = EntityOperationType.DECK_MERGE,
            sourceId = sourceId,
            targetId = targetId,
            itemsMoved = itemsMoved,
            conflictsResolved = conflictsResolved,
            sourceDeleted = true,
        ))
    }

    suspend fun moveDeck(
        deckId: String,
        targetKnowledgeBaseId: String,
    ): Result<MoveMergeResult> {
        val deck = deckRepository.getById(deckId)
            ?: return Result.failure(IllegalArgumentException("Deck not found"))

        val targetKb = knowledgeBaseRepository.getById(targetKnowledgeBaseId)
            ?: return Result.failure(IllegalArgumentException("Target knowledge base not found"))

        if (deck.knowledgeBaseId == targetKnowledgeBaseId) {
            return Result.failure(IllegalArgumentException("Deck is already in this knowledge base"))
        }

        val existingNames = deckRepository.getByKnowledgeBase(targetKnowledgeBaseId).first().map { it.name }.toSet()

        val newName = if (deck.name in existingNames) {
            findUniqueDeckName(targetKnowledgeBaseId, deck.name)
        } else deck.name

        deckRepository.update(deck.copy(
            knowledgeBaseId = targetKnowledgeBaseId,
            name = newName,
            updatedAt = Clock.System.now(),
        ))

        return Result.success(MoveMergeResult(
            operationType = EntityOperationType.DECK_MOVE,
            sourceId = deckId,
            targetId = targetKnowledgeBaseId,
            itemsMoved = 1,
            conflictsResolved = if (newName != deck.name) 1 else 0,
            sourceDeleted = false,
            conflictMessages = if (newName != deck.name) listOf("Deck renamed to \"$newName\"") else emptyList(),
        ))
    }

    suspend fun moveCard(
        cardId: String,
        targetDeckId: String,
    ): Result<MoveMergeResult> {
        val card = cardRepository.getById(cardId)
            ?: return Result.failure(IllegalArgumentException("Card not found"))

        val targetDeck = deckRepository.getById(targetDeckId)
            ?: return Result.failure(IllegalArgumentException("Target deck not found"))

        if (card.deckId == targetDeckId) {
            return Result.failure(IllegalArgumentException("Card is already in this deck"))
        }

        val targetFronts = cardRepository.getByDeck(targetDeckId).first().map { it.front }.toSet()

        val front = when {
            card.front in targetFronts -> resolveCardFrontConflict(card.front, targetFronts)
            card.front.isBlank() -> generateDefaultCardName(0)
            else -> card.front
        }

        cardRepository.update(card.copy(
            deckId = targetDeckId,
            front = front,
            updatedAt = Clock.System.now(),
        ))

        return Result.success(MoveMergeResult(
            operationType = EntityOperationType.CARD_MOVE,
            sourceId = cardId,
            targetId = targetDeckId,
            itemsMoved = 1,
            conflictsResolved = if (front != card.front) 1 else 0,
            sourceDeleted = false,
        ))
    }

    private suspend fun findUniqueDeckName(kbId: String, baseName: String): String {
        val existingNames = deckRepository.getByKnowledgeBase(kbId).first().map { it.name }.toSet()
        if (baseName !in existingNames) return baseName
        var suffix = 1
        while (true) {
            val candidate = "$baseName ($suffix)"
            if (candidate !in existingNames) return candidate
            suffix++
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateDefaultCardName(index: Int): String {
        return "Card_${Uuid.random().toString().take(6)}_$index"
    }

    private fun resolveCardFrontConflict(originalFront: String, existingFronts: Set<String>): String {
        if (originalFront.isBlank()) return generateDefaultCardName(0)
        var suffix = 1
        while (true) {
            val candidate = "$originalFront ($suffix)"
            if (candidate !in existingFronts) return candidate
            suffix++
        }
    }
}
