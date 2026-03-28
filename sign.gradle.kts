import org.gradle.internal.time.Time.currentTimeMillis
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

tasks.register("generateNewKeys") {
    val signFile = File(projectDir, "private.signature")
    val certFile = File(projectDir, "public.cert")
    doLast {
        if (signFile.exists() || certFile.exists()) println("Cert already exist. Skipping...")
        else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(4096, SecureRandom()) }.generateKeyPair()
            Base64.getEncoder().let { encoder ->
                signFile.writeText(encoder.encodeToString(pair.private.encoded))
                certFile.writeText(encoder.encodeToString(pair.public.encoded) + "\nUnsigned\n")
            }
            println("Keys generated successfully.")
        }
    }
}

tasks.register("signPlugins") {
    dependsOn("generateNewKeys")
    val pattern = Regex("\\s/\\*\\*\\s+(@[^/]+/[^/]+)\\s+((?:\\s\\*\\s[A-z0-9/+=]*\\s+)+)\\s\\*\\s\\*/$")
    val signFile = File(projectDir, "private.signature")
    val pluginDir1 = File(projectDir, "plugins")
    val pluginDir2 = File(projectDir, "src/main/kotlin-scripting")
    doLast {
        ((pluginDir1.listFiles() ?: emptyArray()) + (pluginDir2.listFiles() ?: emptyArray())).forEach { plugin ->
            val text = plugin.readText()
            val match = pattern.find(text)
            val current = match?.let {
                val (owner, signature) = match.destructured
                return@let "$owner\n$signature".trim()
            } ?: ""
            val body = text.replace(pattern, "").trimEnd()
            val encoder = Base64.getEncoder()
            val signature = encoder.encodeToString(signData(body.toByteArray(), signFile.readCert.toPrivateKey)).chunked(100)
            val new = "@${System.getProperty("owner")}\r\n${signature.joinToString("\r\n") { " * $it" }}".trim()

            if (current == new) println("${plugin.nameWithoutExtension} already signed. Skipping...")
            else {
                plugin.writeText("$body\r\n/** $new\r\n * */")
                println("${plugin.nameWithoutExtension} signed")
            }
        }
    }
}

tasks.register("signThirdpartyCert") {
    dependsOn("generateNewKeys")
    val certFile = File(projectDir, "public.cert")
    val signFile = File(projectDir, "private.signature")
    val thirdPartyFile = File(projectDir, "thirdparty.cert")
    doLast {
        val cert = thirdPartyFile.readCert
        val encoder = Base64.getEncoder()
        val certSha256 = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(cert))
        val sign = encoder.encodeToString(signData(cert, signFile.readCert.toPrivateKey))
        val validTo = currentTimeMillis() + 365 * 24 * 60 * 60 * 1000

        val lines = certFile.readLines().toMutableList()
        lines.indexOfFirst { it.startsWith("$certSha256:") }.takeIf { it != -1 }?.let { i ->
            lines[i] = "$certSha256:$sign:$validTo"
            lines.add("")
            certFile.writeText(lines.joinToString("\n"))
            println("Thirdparty cert enddate uploaded successfully.")
        } ?: certFile.appendText("$certSha256:$sign:$validTo\n").also { println("Thirdparty cert added") }
    }
}

fun signData(data: ByteArray, privateKey: PrivateKey): ByteArray = Signature.getInstance("SHA256withRSA").apply {
    initSign(privateKey)
    update(data)
}.sign()

val File.readCert: ByteArray get() = Base64.getDecoder().decode(this.readLines().first())

val ByteArray.toPrivateKey: PrivateKey get() = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(this))