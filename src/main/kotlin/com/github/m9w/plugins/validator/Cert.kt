package com.github.m9w.plugins.validator

import io.ktor.util.*
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class Cert(val cert: String, val repo: String, val signedBy: List<String>, val signedCerts: Map<String, Pair<String, Long>>) {
    val certBytes = cert.decodeBase64Bytes()
    val publicKey: PublicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(certBytes))
    val hash = MessageDigest.getInstance("SHA-256").digest(certBytes).encodeBase64()

    fun isSignedBy(parent: Cert): Boolean {
        val (signature, expiryDate) = parent.signedCerts[hash] ?: return false
        if (expiryDate < System.currentTimeMillis()) return false
        return isSigned(certBytes, signature)
    }

    fun isSigned(data: ByteArray, signature: String): Boolean {
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(data)
        return verifier.verify(signature.decodeBase64Bytes())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Cert
        if (cert != other.cert) return false
        if (repo != other.repo) return false
        return true
    }

    override fun hashCode(): Int {
        var result = cert.hashCode()
        result = 31 * result + repo.hashCode()
        return result
    }

    override fun toString() = "Cert[$repo::$hash]"

    companion object {
        fun deserialize(cert: String, owner: String): Cert {
            val lines = cert.lines()
            val cert = lines[0]
            val signedBy = lines.filter { it.contains("/") }.getOrNull(1)?.split(",") ?: emptyList()
            val signedCerts = (lines.takeIf { it.size > 2 }?.subList(2, lines.size) ?: emptyList())
                .map { it.split(":") }
                .filter { it.size == 3 }
                .associate { it[0] to (it[1] to it[2].toLong()) }
            return Cert(cert, owner, signedBy, signedCerts)
        }
    }
}