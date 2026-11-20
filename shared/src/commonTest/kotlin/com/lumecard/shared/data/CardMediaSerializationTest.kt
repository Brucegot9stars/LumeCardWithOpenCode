package com.lumecard.shared.data

import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Media 字段向后兼容守护。
 *
 * `SqlDelightRepositories.toDomain()` 反序列化 DB 里的 `media TEXT` 列，异常被
 * `catch` 吞成 `emptyList()`。实现现已改用宽松 Json（ignoreUnknownKeys = true），
 * 未知键不再导致整列解析失败；Media 字段仍保留默认值以兼容历史 JSON（缺字段）。
 */
class CardMediaSerializationTest {

    // 旧实现的严格 Json：用于守护合法/历史 JSON 仍可解析、默认值仍生效
    private val strictJson = Json.Default

    // 与 SqlDelightRepositories 当前实现一致：宽松 Json（忽略未知键）
    private val lenientJson = Json { ignoreUnknownKeys = true }

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

    @Test
    fun `legacy media without alignment or compress decodes with defaults`() {
        // 引入 alignment / compress 之前写入的行：两个新字段都必须有默认值，否则严格 Json 会失败。
        val legacy = """[{"id":"m1","type":"IMAGE","filePath":"a.png","mimeType":"image/png","size":1,"caption":"旧图"}]"""
        val list = strictJson.decodeFromString(ListSerializer(Media.serializer()), legacy)
        assertEquals(1, list.size)
        assertEquals("center", list[0].alignment)
        assertEquals(true, list[0].compress)
        assertEquals("旧图", list[0].caption)
    }

    @Test
    fun `media alignment and compress round trip`() {
        val original = Media(
            id = "m4",
            type = MediaType.IMAGE,
            filePath = "left.png",
            alignment = "start",
            compress = false
        )
        val encoded = strictJson.encodeToString(Media.serializer(), original)
        val restored = strictJson.decodeFromString(Media.serializer(), encoded)
        assertEquals("start", restored.alignment)
        assertEquals(false, restored.compress)
        assertEquals(original, restored)
    }

    @Test
    fun `lenient json ignores unknown media fields instead of dropping whole list`() {
        // 新版本写入的 media 含 Media 尚不认识的字段：严格 Json 会解析失败被吞成 emptyList()，
        // 宽松 Json 应跳过未知键并保留已知字段（BUG-3 守护）。
        val column = """[{"id":"m1","type":"IMAGE","filePath":"a.png","size":1,"futureField":42}]"""
        val list = lenientJson.decodeFromString(ListSerializer(Media.serializer()), column)
        assertEquals(1, list.size)
        assertEquals("m1", list[0].id)
        assertEquals("a.png", list[0].filePath)
    }

    @Test
    fun `lenient json ignores unknown metadata keys`() {
        val encoded = lenientJson.encodeToString(mapOf("known" to "v"))
        val restored: Map<String, String> = lenientJson.decodeFromString(encoded)
        assertEquals(mapOf("known" to "v"), restored)
        val withUnknown = """{"fontSize":"16","newKey":"value"}"""
        val parsed: Map<String, String> = lenientJson.decodeFromString(withUnknown)
        assertEquals("16", parsed["fontSize"])
        assertEquals("value", parsed["newKey"])
    }
}
