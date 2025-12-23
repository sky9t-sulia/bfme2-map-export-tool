package config

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

fun parseTerrainIni(iniPath: Path): Map<String, String> {
    val nameToFile = mutableMapOf<String, String>()

    if (!iniPath.exists()) {
        println("Warning: terrain.ini not found at $iniPath")
        return nameToFile
    }

    val lines = iniPath.readLines()
    var currentTerrainName: String? = null
    var inCommentBlock = false

    for (line in lines) {
        val trimmed = line.trim()

        if (trimmed.isEmpty() || trimmed.startsWith(";")) {
            continue
        }

        if (trimmed.startsWith("; Terrain ")) {
            inCommentBlock = true
            continue
        }

        if (inCommentBlock && trimmed.startsWith("End")) {
            inCommentBlock = false
            continue
        }

        if (inCommentBlock) {
            continue
        }

        if (trimmed.startsWith("Terrain ")) {
            currentTerrainName = trimmed.substring(8).trim()
            inCommentBlock = false
        }

        if (trimmed.startsWith("Texture = ") && currentTerrainName != null) {
            val textureFile = trimmed.substring(10).trim()
            nameToFile[currentTerrainName] = textureFile
        }

        if (trimmed == "End") {
            currentTerrainName = null
        }
    }

    println("Loaded ${nameToFile.size} terrain texture mappings")

    return nameToFile
}