import org.gradle.api.Plugin
import org.gradle.api.Project
import java.net.URI
import com.google.gson.Gson

class Darkorbit : Plugin<Project> {
    val gson = Gson()

    override fun apply(project: Project) {
        project.extensions.create("DarkorbitApi", GenerateInterfacesExtension::class.java)

        val sourcesDir = project.layout.buildDirectory.dir("darkorbit/kotlin")
        val resourcesDir = project.layout.buildDirectory.dir("darkorbit/resources")

        val generateInterfaces = project.tasks.register("DarkorbitApi") {
            outputs.dir(sourcesDir)
            doLast {
                fun parseType(type: String): String {
                    if (type.startsWith("Enum:")) return "Int"
                    val pattern = Regex("(?:^|:)([A-z0-9]+)(?:$|/)")
                    var typeVal = pattern.find(type)?.groupValues?.get(1) ?: type
                    typeVal = when (typeVal) { "i8" -> "Byte"; "i16" -> "Short"; "i32" -> "Int"; "i64" -> "Long"; else -> typeVal }
                    return if (type.startsWith("List")) "MutableList<$typeVal>" else typeVal
                }

                val packageDir = sourcesDir.get().asFile.resolve("com/darkorbit").apply { mkdirs() }
                val protocol = resourcesDir.get().asFile.apply { mkdirs() }.resolve("darkorbit-protocol.json")
                if (protocol.exists()) return@doLast
                protocol.writeText(GitHubAPI.getLatestProtocol("m9w/darkorbit-protocol"))
                gson.fromJson<Map<String, MutableMap<String, String>>>(protocol.readText(), Map::class.java).forEach { className, fields ->
                    val superclass = fields.remove("super")?.let { ": $it " } ?: ""
                    val name = className.split("#")[0]
                    val builder = StringBuilder("package com.darkorbit\n\ninterface $name $superclass{\n")
                    fields.entries.sortedBy { it.key }.forEach { (name, type) -> builder.append("\tvar $name: ${parseType(type)}\n") }
                    builder.append("}\n")
                    packageDir.resolve("$name.kt").writeText(builder.toString())
                }
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.extensions.configure<org.gradle.api.tasks.SourceSetContainer>("sourceSets") {
                named("main") {
                    java.srcDir(sourcesDir)
                    resources.srcDir(resourcesDir)
                }
            }

            project.tasks.named("compileKotlin") {
                dependsOn(generateInterfaces)
            }
        }
    }

    open class GenerateInterfacesExtension

    object GitHubAPI {
        fun readURL(url: String) = String(URI(url).toURL().openStream().readAllBytes())

        fun getLatestProtocol(path: String): String {
            val gson = Gson()
            val release = gson.fromJson(readURL("https://api.github.com/repos/$path/releases/latest"), Release::class.java)
            return readURL(release.assets.find { it.name == "darkorbit-protocol.json" } ?.browser_download_url ?: throw RuntimeException("`darkorbit-protocol.json` not found"))
        }

        private data class Release(val assets: List<Asset>)
        private data class Asset(val name: String, val browser_download_url: String)
    }
}