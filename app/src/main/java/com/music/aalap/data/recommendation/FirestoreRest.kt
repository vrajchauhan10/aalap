package com.music.aalap.data.recommendation

import android.content.Context
import com.music.aalap.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Firebase over its web API (no Firebase SDK, which clashes with the YouTube module's
 * protobuf): an anonymous sign-in (nothing to type, no account) and Firestore documents.
 * The project comes from app/google-services.json at build time.
 */
class FirestoreRest(private val context: Context) {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val prefs = context.getSharedPreferences("blend_auth", Context.MODE_PRIVATE)
    private val key = BuildConfig.FIREBASE_API_KEY
    private val base = "https://firestore.googleapis.com/v1/projects/${BuildConfig.FIREBASE_PROJECT}/databases/(default)/documents"

    class HttpError(val code: Int, body: String) : Exception("HTTP $code: ${body.take(300)}")

    // ── sign-in ─────────────────────────────────────────────────────────────

    @Volatile private var idToken: String? = null
    @Volatile private var tokenUntil = 0L

    val uid: String? get() = prefs.getString("uid", null)

    /** Signs in once (anonymous) and keeps a fresh token; returns the uid. */
    @Synchronized
    fun signIn(): String {
        if (idToken != null && System.currentTimeMillis() < tokenUntil) return uid!!
        val refresh = prefs.getString("refresh", null)
        val out = if (refresh == null) {
            post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key", JSONObject().put("returnSecureToken", true), auth = false).also {
                prefs.edit().putString("uid", it.getString("localId")).putString("refresh", it.getString("refreshToken")).apply()
            }.let { JSONObject().put("id_token", it.getString("idToken")).put("expires_in", it.getString("expiresIn")) }
        } else {
            val body = "grant_type=refresh_token&refresh_token=$refresh".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            send(Request.Builder().url("https://securetoken.googleapis.com/v1/token?key=$key").post(body), auth = false).also {
                prefs.edit().putString("uid", it.getString("user_id")).putString("refresh", it.getString("refresh_token")).apply()
            }
        }
        idToken = out.getString("id_token")
        tokenUntil = System.currentTimeMillis() + (out.optString("expires_in").toLongOrNull() ?: 3600) * 1000 - 120_000
        return uid!!
    }

    // ── documents ───────────────────────────────────────────────────────────

    data class Doc(val id: String, val fields: Map<String, Any?>, val updateTime: String)

    /** Documents of [collection] whose array [field] contains [value]. */
    fun whereArrayContains(collection: String, field: String, value: String): List<Doc> {
        val q = JSONObject().put("structuredQuery", JSONObject()
            .put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
            .put("where", JSONObject().put("fieldFilter", JSONObject()
                .put("field", JSONObject().put("fieldPath", field)).put("op", "ARRAY_CONTAINS").put("value", encode(value)))))
        val res = sendArray(Request.Builder().url("$base:runQuery").post(q.toString().toRequestBody(json)))
        return (0 until res.length()).mapNotNull { res.getJSONObject(it).optJSONObject("document")?.let(::doc) }
    }

    fun get(collection: String, id: String): Doc? = try {
        doc(send(Request.Builder().url("$base/$collection/$id").get()))
    } catch (e: HttpError) { if (e.code == 404) null else throw e }

    /** Creates the document; false if it's already there. */
    fun create(collection: String, id: String, fields: Map<String, Any?>): Boolean = try {
        post("$base/$collection?documentId=$id", JSONObject().put("fields", encodeFields(fields))); true
    } catch (e: HttpError) { if (e.code == 409) false else throw e }

    /**
     * Sets [fields] (dotted paths like "tastes.uid"), removes [delete] paths, and adds
     * [arrayUnion] values, in one write. With [ifUpdateTime], only if nobody changed the
     * document since (false then).
     */
    fun update(
        collection: String, id: String,
        fields: Map<String, Any?> = emptyMap(), delete: List<String> = emptyList(),
        arrayUnion: Map<String, List<Any?>> = emptyMap(), ifUpdateTime: String? = null,
    ): Boolean {
        val name = "projects/${BuildConfig.FIREBASE_PROJECT}/databases/(default)/documents/$collection/$id"
        val write = JSONObject().put("update", JSONObject().put("name", name).put("fields", encodeFields(nest(fields))))
            .put("updateMask", JSONObject().put("fieldPaths", JSONArray((fields.keys + delete).map(::quote))))
            .put("currentDocument", if (ifUpdateTime != null) JSONObject().put("updateTime", ifUpdateTime) else JSONObject().put("exists", true))
        if (arrayUnion.isNotEmpty()) write.put("updateTransforms", JSONArray(arrayUnion.map { (path, values) ->
            JSONObject().put("fieldPath", quote(path)).put("appendMissingElements", JSONObject().put("values", JSONArray(values.map(::encode))))
        }))
        return try {
            post("$base:commit", JSONObject().put("writes", JSONArray().put(write))); true
        } catch (e: HttpError) { if (e.code == 400 && ifUpdateTime != null || e.code == 409 || e.code == 412) false else throw e }
    }

    // ── values ──────────────────────────────────────────────────────────────

    /** "a.b.c" → `a`.`b`.`c` (Firestore field paths: map keys may be numbers or ids). */
    private fun quote(path: String) = path.split(".").joinToString(".") { "`${it.replace("`", "\\`")}`" }

    /** {"a.b": 1} → {"a": {"b": 1}} for the write's fields (the mask says what's replaced). */
    private fun nest(flat: Map<String, Any?>): Map<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        flat.forEach { (path, v) ->
            val parts = path.split(".")
            var m = root
            parts.dropLast(1).forEach { p ->
                @Suppress("UNCHECKED_CAST")
                m = (m.getOrPut(p) { LinkedHashMap<String, Any?>() } as MutableMap<String, Any?>) as LinkedHashMap<String, Any?>
            }
            m[parts.last()] = v
        }
        return root
    }

    private fun encodeFields(m: Map<String, Any?>) = JSONObject().apply { m.forEach { (k, v) -> put(k, encode(v)) } }

    private fun encode(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int, is Long -> JSONObject().put("integerValue", v.toString())
        is Number -> JSONObject().put("doubleValue", v.toDouble())
        is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), encode(x)) } }))
        is Collection<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray(v.map(::encode))))
        else -> JSONObject().put("stringValue", v.toString())
    }

    private fun decode(v: JSONObject): Any? = when {
        v.has("stringValue") -> v.getString("stringValue")
        v.has("integerValue") -> v.getString("integerValue").toLong()
        v.has("doubleValue") -> v.getDouble("doubleValue")
        v.has("booleanValue") -> v.getBoolean("booleanValue")
        v.has("mapValue") -> v.getJSONObject("mapValue").optJSONObject("fields")?.let { f -> f.keys().asSequence().associateWith { decode(f.getJSONObject(it)) } } ?: emptyMap<String, Any?>()
        v.has("arrayValue") -> v.getJSONObject("arrayValue").optJSONArray("values")?.let { a -> (0 until a.length()).map { decode(a.getJSONObject(it)) } } ?: emptyList<Any?>()
        else -> null
    }

    private fun doc(o: JSONObject): Doc {
        val f = o.optJSONObject("fields") ?: JSONObject()
        return Doc(o.getString("name").substringAfterLast("/"), f.keys().asSequence().associateWith { decode(f.getJSONObject(it)) }, o.optString("updateTime"))
    }

    // ── HTTP ────────────────────────────────────────────────────────────────

    private fun post(url: String, body: JSONObject, auth: Boolean = true) =
        send(Request.Builder().url(url).post(body.toString().toRequestBody(json)), auth)

    private fun call(b: Request.Builder, auth: Boolean): String {
        // Firebase's Android key may be limited to this app: say which app is calling.
        b.header("X-Android-Package", context.packageName)
        signingSha1()?.let { b.header("X-Android-Cert", it) }
        if (auth) b.header("Authorization", "Bearer ${idToken ?: signIn().let { idToken }}")
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 && auth) { idToken = null; tokenUntil = 0 }
            if (!r.isSuccessful) throw HttpError(r.code, text)
            return text
        }
    }

    private fun send(b: Request.Builder, auth: Boolean = true) = JSONObject(call(b, auth).ifBlank { "{}" })
    private fun sendArray(b: Request.Builder) = JSONArray(call(b, true))

    private fun signingSha1(): String? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val sig = info.signingInfo!!.apkContentsSigners.first().toByteArray()
        java.security.MessageDigest.getInstance("SHA-1").digest(sig).joinToString("") { "%02X".format(it) }
    }.getOrNull()
}
