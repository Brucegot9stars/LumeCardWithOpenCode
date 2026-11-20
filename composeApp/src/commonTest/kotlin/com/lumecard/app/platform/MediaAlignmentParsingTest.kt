package com.lumecard.app.platform

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaAlignmentParsingTest {

    @Test
    fun knownAlignmentsMapToTheirValues() {
        assertEquals(MediaAlignment.START, parseMediaAlignment("start"))
        assertEquals(MediaAlignment.CENTER, parseMediaAlignment("center"))
        assertEquals(MediaAlignment.END, parseMediaAlignment("end"))
    }

    @Test
    fun alignmentParsingIgnoresCaseAndSurroundingSpace() {
        assertEquals(MediaAlignment.START, parseMediaAlignment(" START "))
        assertEquals(MediaAlignment.END, parseMediaAlignment("End"))
    }

    @Test
    fun unknownAlignmentFallsBackToCenterInsteadOfDroppingTheImage() {
        assertEquals(MediaAlignment.CENTER, parseMediaAlignment("middle"))
        assertEquals(MediaAlignment.CENTER, parseMediaAlignment(""))
        assertEquals(MediaAlignment.CENTER, parseMediaAlignment(null))
    }

    @Test
    fun imageRefsCarryPerImageAlignment() {
        val card = Card(
            id = "card_1",
            deckId = "deck_1",
            type = CardType.BASIC,
            front = "front",
            back = "back",
            media = listOf(
                Media(id = "img_1", type = MediaType.IMAGE, filePath = "a.png", alignment = "start"),
                Media(id = "img_2", type = MediaType.IMAGE, filePath = "b.png", alignment = "end"),
                Media(id = "img_3", type = MediaType.IMAGE, filePath = "c.png"),
            )
        )
        val refs = parseImageRefs(card)
        assertEquals(
            listOf(MediaAlignment.START, MediaAlignment.END, MediaAlignment.CENTER),
            refs.map { it.alignment }
        )
    }

    @Test
    fun nonImageMediaIsIgnoredSoPlainCardsStayCheap() {
        val card = Card(
            id = "card_2",
            deckId = "deck_1",
            type = CardType.BASIC,
            front = "front",
            back = "back",
            media = listOf(Media(id = "aud_1", type = MediaType.AUDIO, filePath = "a.mp3"))
        )
        assertEquals(emptyList(), parseImageRefs(card))
    }
}
