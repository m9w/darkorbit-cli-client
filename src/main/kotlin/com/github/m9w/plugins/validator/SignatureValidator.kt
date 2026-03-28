package com.github.m9w.plugins.validator

import com.github.m9w.config.staticConfig
import com.github.m9w.plugins.dao.Plugin
import com.github.m9w.util.Http
import com.github.m9w.util.Http.Companion.content
import com.github.m9w.util.ms
import java.io.File
import java.lang.System.currentTimeMillis
import java.util.concurrent.ConcurrentHashMap


/** Signed plugins loading automatically. And also can be autoupdated. Unsigned plugins should be loaded manually */
object SignatureValidator {
    val pattern = Regex("\\s/\\*\\*\\s+(@[^/]+/[^/]+)\\s+((?:\\s\\*\\s[A-z0-9/+=]*\\s+)+)\\s\\*\\s\\*/$")
    private val certs: MutableMap<Long, Cert> = ConcurrentHashMap()
    private val trustedRepos: MutableList<String> by staticConfig(mutableListOf("m9w/darkorbit-cli-client"))
    private val localCert get() = Cert.deserialize(File("public.cert").readText(), "local")
    private val isTrustLocal get() = (System.getenv("trust_local") ?: "false") == "true"

    fun getCert(repo: String): Cert? {
        certs.entries.removeIf { it.key < currentTimeMillis() }
        certs.values.firstOrNull { it.repo == repo }?.let { return it }
        val resp = Http( "https://raw.githubusercontent.com/$repo/refs/heads/main/public.cert").connect
        if (resp.statusCode() > 299) return null
        runCatching { return Cert.deserialize(resp.content, repo).also { certs[ms(min = 3)] = it } }.onFailure { it.printStackTrace() }
        return null
    }

    val Plugin.cert: Cert? get() {
        val match = pattern.find(text) ?: return null
        val (repo, rawSignature) = match.destructured
        val body = text.replace(pattern, "").trimEnd()
        val signature = rawSignature.replace(Regex("[\\s*]"), "")
        if (isTrustLocal && localCert.isSigned(body.toByteArray(Charsets.UTF_8), signature)) return localCert
        val cert = getCert(repo.trim().removePrefix("@")) ?: return null
        return if (cert.isSigned(body.toByteArray(Charsets.UTF_8), signature)) cert else null
    }

    val Cert.isTrusted: Boolean get() {
        if (isTrustLocal && cert == localCert.cert) return true
        val visited: MutableSet<Cert> = HashSet()
        val workSet: MutableSet<Cert> = HashSet()
        workSet.add(this)
        while (!workSet.isEmpty()) {
            val cert = workSet.iterator().next().also { visited.add(it) }
            if (trustedRepos.contains(cert.repo)) return true
            if (trustedRepos.contains(cert.repo+"?") && this == cert) return true
            workSet.addAll(cert.signedBy.toSet().mapNotNull { getCert(it) }.filter { cert.isSignedBy(it) })
            workSet.removeAll(visited)
        }
        return false
    }
}