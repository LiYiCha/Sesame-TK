package fansirsqi.xposed.sesame.hook.network.capture

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.IdentityHashMap

/** HTTP 对象快照：把 hook 现场的方法参数/返回对象展开为 JSON，不消费流内容。 */
internal object HttpSnapshot {
    internal const val H5_REQUEST_CLASS = "com.alipay.mobile.common.transport.h5.H5HttpUrlRequest"

    fun arguments(method: Method, args: List<Any?>, dataArguments: Set<Int> = emptySet()): String {
        val values = JSONArray()
        val visited = IdentityHashMap<Any, Boolean>()
        args.forEachIndexed { index, value ->
            val entry = JSONObject()
                .put("index", index)
                .put("type", method.parameterTypes.getOrNull(index)?.name ?: JSONObject.NULL)
            runCatching {
                entry.put("value", snapshot(value, visited, expandFields = index in dataArguments))
            }.onFailure {
                entry.put("readError", "${it.javaClass.name}: ${it.message.orEmpty()}")
            }
            values.put(entry)
        }
        return JSONObject().put("signature", method.toString()).put("arguments", values).toString()
    }

    fun snapshot(
        value: Any?,
        visited: IdentityHashMap<Any, Boolean>,
        streams: MutableList<InputStream>? = null,
        expandFields: Boolean = false,
        bodyObjects: MutableList<Any>? = null
    ): Any {
        when (value) {
            null -> return JSONObject.NULL
            is String, is Number, is Boolean -> return value
            is java.net.URL -> return value.toExternalForm()
            is java.net.URI -> return value.toString()
            is Char -> return value.toString()
            is Enum<*> -> return value.name
            is JSONObject, is JSONArray -> return value.toString()
            is ByteArray -> return JSONObject()
                .put("encoding", "base64")
                .put("size", value.size)
                .put("data", Base64.encodeToString(value, Base64.NO_WRAP))
            is ByteBuffer -> {
                val buffer = value.asReadOnlyBuffer()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                return JSONObject()
                    .put("position", value.position())
                    .put("limit", value.limit())
                    .put("bytes", snapshot(bytes, visited))
            }
        }
        val type = value.javaClass
        val reference = JSONObject().put("type", type.name).put("identity", System.identityHashCode(value))
        if (visited.put(value, true) != null) return reference
        return try {
            when {
                value is Map<*, *> -> JSONArray().apply {
                    value.forEach { (key, item) ->
                        put(
                            JSONObject()
                                .put("key", snapshot(key, visited, streams, bodyObjects = bodyObjects))
                                .put("value", snapshot(item, visited, streams, bodyObjects = bodyObjects))
                        )
                    }
                }
                value is Collection<*> -> JSONArray().apply {
                    value.forEach { put(snapshot(it, visited, streams, bodyObjects = bodyObjects)) }
                }
                type.isArray -> JSONArray().apply {
                    for (index in 0 until java.lang.reflect.Array.getLength(value)) {
                        put(snapshot(java.lang.reflect.Array.get(value, index), visited, streams, bodyObjects = bodyObjects))
                    }
                }
                value is InputStream -> {
                    streams?.add(value)
                    reference.put("note", "stream_not_consumed")
                }
                value is OutputStream -> reference.put("note", "stream_not_consumed")
                type.name == "org.apache.http.message.HeaderGroup" -> snapshot(
                    type.getMethod("getAllHeaders").invoke(value), visited, streams, bodyObjects = bodyObjects
                )
                runCatching {
                    Class.forName("org.apache.http.Header", false, type.classLoader).isInstance(value)
                }.getOrDefault(false) -> reference
                    .put("name", findMethod(type, "getName", 0)?.invoke(value) ?: JSONObject.NULL)
                    .put("value", findMethod(type, "getValue", 0)?.invoke(value) ?: JSONObject.NULL)
                else -> {
                    val properties = JSONObject()
                    val contracts = listOf(
                        "org.apache.http.NameValuePair" to listOf("getName", "getValue"),
                        "org.apache.http.HttpMessage" to listOf("getProtocolVersion", "getAllHeaders"),
                        "org.apache.http.HttpRequest" to listOf("getRequestLine"),
                        "org.apache.http.HttpResponse" to listOf("getStatusLine", "getEntity"),
                        "org.apache.http.RequestLine" to listOf("getMethod", "getUri", "getProtocolVersion"),
                        "org.apache.http.StatusLine" to listOf("getStatusCode", "getReasonPhrase", "getProtocolVersion"),
                        "org.apache.http.ProtocolVersion" to listOf("getProtocol", "getMajor", "getMinor"),
                        "org.apache.http.HttpEntity" to listOf(
                            "getContentLength", "getContentType", "getContentEncoding", "isChunked", "isStreaming", "isRepeatable"
                        )
                    )
                    contracts.forEach { (name, contractMethods) ->
                        val contract = runCatching { Class.forName(name, false, type.classLoader) }.getOrNull()
                        if (contract != null && contract.isInstance(value)) {
                            if (name == "org.apache.http.HttpResponse" || name == "org.apache.http.HttpEntity") {
                                bodyObjects?.add(value)
                            }
                            contractMethods.forEach { contractMethod ->
                                runCatching {
                                    properties.put(contractMethod, snapshot(
                                        contract.getMethod(contractMethod).invoke(value), visited, streams, bodyObjects = bodyObjects
                                    ))
                                }.onFailure {
                                    properties.put(contractMethod, JSONObject().put("readError", "${it.javaClass.name}: ${it.message.orEmpty()}"))
                                }
                            }
                            if (name == "org.apache.http.HttpEntity") reference.put("note", "entity_body_not_read_by_snapshot")
                        }
                    }
                    if (properties.length() > 0) reference.put("properties", properties)
                    val readFields = expandFields || generateSequence<Class<*>>(type) { it.superclass }.any {
                        it.name == H5_REQUEST_CLASS ||
                            it.name == "com.alipay.mobile.common.transport.h5.H5HttpUrlResponse" ||
                            it.name == "com.alipay.mobile.common.transport.http.HttpUrlRequest" ||
                            it.name == "com.alipay.mobile.common.transport.http.HttpUrlResponse" ||
                            it.name == "com.alipay.mobile.common.transport.http.HttpUrlHeader" ||
                            it.name == "org.apache.http.entity.ByteArrayEntity"
                    }
                    if (readFields) {
                        val fields = JSONArray()
                        var current: Class<*>? = type
                        while (current != null && current != Any::class.java) {
                            current.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
                                val entry = JSONObject()
                                    .put("owner", field.declaringClass.name)
                                    .put("name", field.name)
                                    .put("type", field.type.name)
                                runCatching {
                                    field.isAccessible = true
                                    entry.put("value", snapshot(field.get(value), visited, streams, bodyObjects = bodyObjects))
                                }.onFailure {
                                    entry.put("readError", "${it.javaClass.name}: ${it.message.orEmpty()}")
                                }
                                fields.put(entry)
                            }
                            current = current.superclass
                        }
                        reference.put("fields", fields)
                    } else if (properties.length() == 0) {
                        reference.put("note", "object_fields_not_observed")
                    }
                    reference
                }
            }
        } finally {
            visited.remove(value)
        }
    }

    fun findMethod(targetClass: Class<*>, name: String, parameterCount: Int, firstParameterType: Class<*>? = null): Method? {
        val methods = linkedSetOf<Method>()
        var current: Class<*>? = targetClass
        while (current != null) {
            methods.addAll(current.declaredMethods)
            current = current.superclass
        }
        methods.addAll(targetClass.methods)
        return methods.firstOrNull { method ->
            method.name == name &&
                method.parameterCount == parameterCount &&
                (firstParameterType == null || method.parameterTypes.firstOrNull() == firstParameterType)
        }?.apply { isAccessible = true }
    }

    fun findField(targetClass: Class<*>, name: String): Field? {
        var current: Class<*>? = targetClass
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }
}
