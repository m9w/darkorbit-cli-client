package com.github.m9w.util

import com.google.gson.Gson
import java.io.*
import java.net.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*
import javax.net.ssl.*


/**
 * Utility for HTTP connections.
 * Use it like builder, just one time for instance
 */
class Http(private val baseUrl: String, val method: String = "GET", private val followRedirects: Boolean = true) {
    private val bodyHolder = BodyHolder()
    private var userAgent = defaultUserAgent
    private var suppliers = ArrayList<() -> Unit>()
    private var headers = LinkedHashMap<String, String>()
    private var doSid: String = ""

    /**
     * Adds action which will be executed at the end of the connection
     */
    fun addSupplier(block: () -> Unit): Http {
        this.suppliers.add(block)
        return this
    }

    /**
     * Sets or overrides connection header.
     * Encoded via [java.net.URLEncoder.encode] in UTF-8
     * To set header without encoding look [Http.setRawHeaders]
     *
     * Equivalent to [HttpURLConnection.setRequestProperty]
     */
    fun setHeaders(vararg headers: Pair<String, String>): Http {
        headers.forEach { this.headers.put(encode(it.first), encode(it.second)) }
        return this
    }

    /**
     * Sets or overrides connection header without encoding.
     * Equivalent to [HttpURLConnection.setRequestProperty]
     */
    fun setRawHeaders(vararg rawHeaders: Pair<String, String>): Http {
        rawHeaders.forEach { headers.put(it.first, it.second) }
        return this
    }

    fun setSid(sessionID: String) = this.apply { doSid = sessionID }

    /**
     * Sets or overrides parameter for POST as body or for GET as
     * additional query url only if current url doesn't contains '?' char.
     * Is encoded via [java.net.URLEncoder.encode]
     */
    fun setParams(vararg params: Pair<Any, Any>): Http {
        params.forEach { bodyHolder.setParam(it.first, it.second) }
        return this
    }

    /**
     * Sets or overrides parameter for POST as body or for GET as
     * additional query url only if current url doesn't contains '?' char.
     * Be aware, this wont be encoded via [java.net.URLEncoder.encode]
     */
    fun setRawParams(vararg rawParams: Pair<Any, Any>): Http {
        rawParams.forEach { bodyHolder.setRawParam(it.first, it.second) }
        return this
    }

    /**
     * Set the body for POST requests
     *
     * @param body bytes to send as body
     * @return current instance of http
     */
    fun setBody(body: ByteArray?): Http {
        this.bodyHolder.setBody(body)
        return this
    }

    /**
     * Serializes the object into JSON and set it as POST body
     *
     * @param json         object to be serialized into JSON
     * @param encodeBase64 should JSON be encoded in Base64
     * @return current instance of http
     */
    fun setJsonBody(json: Any, encodeBase64: Boolean = false): Http = setBody(ByteArrayOutputStream().use { baos ->
        OutputStreamWriter(if (encodeBase64) Base64.getEncoder().wrap(baos) else baos, StandardCharsets.UTF_8)
            .use { gson.toJson(json, it) }
        baos.toByteArray()
    })

    /**
     * Sets user agent used in connection.
     *
     * @param userAgent to use.
     * @return current instance of Http
     */
    fun setUserAgent(userAgent: String): Http {
        this.userAgent = userAgent
        return this
    }

    val url: URL get() {
        var url: String = baseUrl
        if (method === "GET" && bodyHolder.hasParams()) url += (if (url.contains("?")) "" else "?") + bodyHolder
        return URL(url)
    }

    /**
     * Gets [HttpResponse] with provided params,
     * request method, and body.
     * **Creates new connection on each call**
     * @return HttpResponse<ByteArray>
     */
    fun getConnection(httpProxy: InetSocketAddress? = null, ignoreSSL: Boolean = false, followRedirects: Boolean = true): HttpResponse<ByteArray> {
        val cookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }
        val clientBuilder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .cookieHandler(cookieManager)
            .followRedirects(if (followRedirects) HttpClient.Redirect.ALWAYS else HttpClient.Redirect.NEVER)

        if (ignoreSSL) clientBuilder.sslContext(mockedSslContext)
        if (httpProxy != null) clientBuilder.proxy(ProxySelector.of(httpProxy))
        val client = clientBuilder.build()
        val requestBuilder = HttpRequest.newBuilder().uri(url.toURI()).timeout(Duration.ofSeconds(30))
        if (!headers.containsKey("User-Agent")) headers["User-Agent"] = userAgent
        for ((key, value) in headers) requestBuilder.header(key, value)

        if (method.uppercase() == "POST" && bodyHolder.isValid) {
            val data = bodyHolder.bytes
            val contentType = if (bodyHolder.hasParams()) { "application/x-www-form-urlencoded" } else { "application/octet-stream" }
            requestBuilder.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofByteArray(data))
        }
        else requestBuilder.method(method.uppercase(Locale.ROOT), HttpRequest.BodyPublishers.noBody())
        suppliers.forEach { it.invoke() }
        val request = requestBuilder.build()
        if (doSid.isNotEmpty()) cookieManager.cookieStore.add(request.uri(), HttpCookie.parse("Set-Cookie: dosid=$doSid; path=/; samesite=none; secure; HttpOnly")[0])
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    val connect get() = getConnection()

    private class ParamBuilder {
        /**
         * Get current HashMap of parameters.
         *
         * @return map with current parameters.
         */
        var params: MutableMap<Any?, Any?> = LinkedHashMap<Any?, Any?>()

        /**
         * Set or overwrite a parameter.
         * Will be encoded via URLEncoder in UTF-8
         *
         * @param key   of parameter.
         * @param value of parameter.
         * @return current instance of ParamBuilder
         */
        fun set(key: Any, value: Any): ParamBuilder = this.apply { params.put(encode(key), encode(value)) }

        /**
         * Set or overwrite a parameter without URLEncoder.
         *
         * @param key   of parameter.
         * @param value of parameter.
         * @return current instance of ParamBuilder
         */
        fun setRaw(key: Any?, value: Any?): ParamBuilder = this.apply { params.put(key, value) }


        /**
         * Creates bytes from current params in UTF_8 encoding.
         *
         * @return byte array
         */
        val bytes: ByteArray? get() = toString().toByteArray(StandardCharsets.UTF_8)

        /**
         * Creates a String of parameters from objects.
         * Example: "key=value&amp;anotherKey=anotherValue".
         *
         * @return String of current parameters
         */
        override fun toString(): String = this.params.entries.joinToString("&") { it.toString() }

    }

    private class BodyHolder {
        private var body: ByteArray? = null
        private var paramBuilder: ParamBuilder? = null

        private val params: ParamBuilder get() = paramBuilder ?: if (body == null) ParamBuilder().also { paramBuilder = it }
            else throw UnsupportedOperationException("Cannot mix body & params")

        val isValid: Boolean get() = paramBuilder != null || body != null

        fun hasParams(): Boolean = paramBuilder != null

        val bytes: ByteArray get() = body ?: paramBuilder?.bytes ?: throw IllegalStateException("data & params are null!")

        fun setParam(key: Any, value: Any) {
            this.params.set(key, value)
        }

        fun setRawParam(key: Any?, value: Any?) {
            this.params.setRaw(key, value)
        }

        fun setBody(body: ByteArray?) {
            if (paramBuilder != null) throw UnsupportedOperationException("Cannot mix body & params")
            this.body = body
        }

        override fun toString(): String = paramBuilder?.toString() ?: body?.contentToString() ?: super.toString()
    }

    companion object {
        val gson: Gson = Gson()
        private var defaultUserAgent: String = "DarkOrbit Unity Client 1.1.46"
        init { System.setProperty("sun.net.http.allowRestrictedHeaders", "true") }
        /**
         * Checks if Objects is instance of String
         * and encodes it via [URLEncoder.encode] in UTF-8
         * else returns raw value.
         *
         * @param value Object to encode
         * @return encoded String or raw Object if is not a String / on exception.
         */
        private fun encode(value: Any): Any {
            if (value !is String) return value
            return Companion.encode(value)
        }

        /**
         * Encodes String via [URLEncoder.encode] in UTF-8
         *
         * @param value to encode
         * @return encoded String or raw value on exception
         */
        private fun encode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
        }

        private val mockedSslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                override fun checkClientTrusted(arg0: Array<X509Certificate>, arg1: String?) {}
                override fun checkServerTrusted(arg0: Array<X509Certificate>, arg1: String?) {}
            }), null)
        }

        /**
         * Deserializes the JSON response from the input stream into an object of the specified type
         *
         * @param isBase64 is JSON response base64 encoded
         * @return deserialized JSON response
         */
        inline fun <reified T> HttpResponse<ByteArray>.asJson(isBase64: Boolean = false): T = try {
            gson.fromJson<T>(String(if (isBase64) Base64.getDecoder().decode(this.body()) else this.body()), T::class.java)
        } catch (e: Exception) {
            throw RuntimeException(String(body()),e)
        }
        /**
         * Connects, gets and converts InputStream to String then closes stream.
         * **Creates new connection on each call**
         *
         * @return body of request as String
         */
        val HttpResponse<ByteArray>.content: String get() = String(this.body())
    }
}
