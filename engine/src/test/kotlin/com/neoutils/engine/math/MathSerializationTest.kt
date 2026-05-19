package com.neoutils.engine.math

import com.neoutils.engine.render.Color
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MathSerializationTest {

    private val json = Json

    @Test
    fun `Vec2 round-trips through JSON`() {
        val original = Vec2(1.5f, -2.25f)
        val encoded = json.encodeToString(Vec2.serializer(), original)
        val decoded = json.decodeFromString(Vec2.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Rect round-trips through JSON`() {
        val original = Rect(Vec2(10f, 20f), Vec2(30f, 40f))
        val encoded = json.encodeToString(Rect.serializer(), original)
        val decoded = json.decodeFromString(Rect.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Transform round-trips through JSON`() {
        val original = Transform(
            position = Vec2(5f, 5f),
            scale = Vec2(2f, 3f),
            rotation = 1.2f,
        )
        val encoded = json.encodeToString(Transform.serializer(), original)
        val decoded = json.decodeFromString(Transform.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Color round-trips through JSON`() {
        val original = Color(0.5f, 0.25f, 0.125f, 0.75f)
        val encoded = json.encodeToString(Color.serializer(), original)
        val decoded = json.decodeFromString(Color.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
