package com.lumecard.shared.data

import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Media 字段向后兼容守护。
 *
 * `SqlDelightRepositories.toDomain()` 用 kotlinx 默认（严格）Json 反序列化 DB 里的
 * `media TEXT` 列，且异常被 `catch` 吞成 `emptyList()`。因此 Media 新增字段必须带
 * 默认值，否则旧 JSON（5 字段）会反序列化失败并静默丢图。
 */
class CardMediaSerializationTest {

    // 与 SqlDelightRepositories 完全一致：默认 Json（严格模式、不忽略未知键）
    private val strictJson = Json.Default

    @Test
    fun `legacy five field media json decodes with defaults`() {
        val legacy = """{"id":"m1","type":"IMAGE","filePath":"a1b2.png","mimeType":"image/png","size":1024}"""
        val media = strictJson.decodeFromString(Media.serializer(), legacy)
        assertEquals("m1", media.id)
        assertEquals(MediaType.IMAGE, media.type)
        assertEquals("a1b2.png", media.filePath)
        assertEquals("image/png", media.mimeType)
        assertEquals(1024L, media.size)
        assertEquals("", media.caption)
    }

    @Test
    fun `network media without mimeType and size decodes with defaults`() {
        // 图床链接场景：skill / AI 只提供 id/type/filePath + 可选 caption
        val minimal = """{"id":"m2","type":"IMAGE","filePath":"https://cdn.example.com/x.png","caption":"示意图"}"""
        val media = strictJson.decodeFromString(Media.serializer(), minimal)
        assertEquals("https://cdn.example.com/x.png", media.filePath)
        assertEquals("", media.mimeType)
        assertEquals(0L, media.size)
        assertEquals("示意图", media.caption)
    }

    @Test
    fun `media with caption round trips`() {
        val original = Media(
            id = "m3",
            type = MediaType.IMAGE,
            filePath = "x.png",
            mimeType = "image/png",
            size = 10L,
            caption = "图注"
        )
        val encoded = strictJson.encodeToString(Media.serializer(), original)
        assertEquals(original, strictJson.decodeFromString(Media.serializer(), encoded))
    }

    @Test
    fun `legacy db media column decodes as list`() {
        val column = """[{"id":"m1","type":"IMAGE","filePath":"a.png","mimeType":"image/png","size":1},{"id":"m2","type":"IMAGE","filePath":"b.png","mimeType":"image/png","size":2}]"""
        val list = strictJson.decodeFromString(ListSerializer(Media.serializer()), column)
        assertEquals(2, list.size)
        assertEquals("", list[0].caption)
        assertEquals("", list[1].caption)
    }
}
