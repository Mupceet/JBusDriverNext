package me.jbusdriver.modern.core.serialization

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import me.jbusdriver.modern.domain.model.ContentBlock
import me.jbusdriver.modern.domain.model.RichList
import me.jbusdriver.modern.domain.model.RichParagraph
import me.jbusdriver.modern.domain.model.TextPart

private val RICH_PARAGRAPHS_TYPE: Type = object : TypeToken<List<RichParagraph>>() {}.type
private val RICH_LIST_TYPE: Type = object : TypeToken<RichList>() {}.type

class ContentBlockTypeAdapter(private val gson: Gson) : TypeAdapter<ContentBlock>() {
    override fun write(out: JsonWriter, value: ContentBlock?) {
        if (value == null) {
            out.nullValue()
            return
        }
        val json = JsonObject()
        when (value) {
            is ContentBlock.RichText -> {
                json.addProperty("type", "richtext")
                json.add("paragraphs", gson.toJsonTree(value.paragraphs, RICH_PARAGRAPHS_TYPE))
            }

            is ContentBlock.ListBlock -> {
                json.addProperty("type", "list")
                json.add("list", gson.toJsonTree(value.list, RICH_LIST_TYPE))
            }

            is ContentBlock.Image -> {
                json.addProperty("type", "image")
                json.addProperty("url", value.url)
                if (value.width > 0) json.addProperty("width", value.width)
                if (value.height > 0) json.addProperty("height", value.height)
                if (value.isFullSize) json.addProperty("fullSize", true)
                if (value.isGif) json.addProperty("isGif", true)
            }

            is ContentBlock.Quote -> {
                json.addProperty("type", "quote")
                json.addProperty("author", value.author)
                json.addProperty("content", value.content)
            }

            is ContentBlock.RestrictedNotice -> {
                json.addProperty("type", "restricted")
                json.addProperty("message", value.message)
            }
        }
        gson.toJson(json, out)
    }

    override fun read(`in`: JsonReader): ContentBlock? {
        val element = JsonParser.parseReader(`in`)
        if (element.isJsonNull || !element.isJsonObject) return null
        val json = element.asJsonObject
        return when (json.string("type")) {
            "richtext" -> readRichText(json)
            "text" -> ContentBlock.RichText(listOf(RichParagraph(listOf(TextPart(json.string("content"))))))
            "list" -> json["list"]
                ?.takeUnless { it.isJsonNull }
                ?.let { ContentBlock.ListBlock(gson.fromJson(it, RICH_LIST_TYPE)) }

            "image" -> ContentBlock.Image(
                url = json.string("url"),
                width = json.int("width"),
                height = json.int("height"),
                isFullSize = json.boolean("fullSize"),
                isGif = json.boolean("isGif")
            )

            "quote" -> ContentBlock.Quote(json.string("author"), json.string("content"))
            "restricted" -> ContentBlock.RestrictedNotice(json.string("message"))
            else -> null
        }
    }

    private fun readRichText(json: JsonObject): ContentBlock.RichText {
        json["paragraphs"]
            ?.takeUnless { it.isJsonNull }
            ?.let { paragraphs ->
                return ContentBlock.RichText(
                    gson.fromJson<List<RichParagraph>>(paragraphs, RICH_PARAGRAPHS_TYPE)
                )
            }

        val parts = json["parts"]
            ?.takeUnless { it.isJsonNull }
            ?.asJsonArray
            ?.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val part = element.asJsonObject
                when (part.string("type")) {
                    "plain" -> TextPart(text = part.string("text"))
                    "link" -> TextPart(
                        text = part.string("text"),
                        isLink = true,
                        linkUrl = part.string("url")
                    )

                    else -> null
                }
            }
            .orEmpty()
        return ContentBlock.RichText(listOf(RichParagraph(parts)))
    }
}

object ContentBlockAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!ContentBlock::class.java.isAssignableFrom(type.rawType)) return null
        @Suppress("UNCHECKED_CAST")
        return ContentBlockTypeAdapter(gson) as TypeAdapter<T>
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

private fun JsonObject.int(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

private fun JsonObject.boolean(name: String): Boolean =
    get(name)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
