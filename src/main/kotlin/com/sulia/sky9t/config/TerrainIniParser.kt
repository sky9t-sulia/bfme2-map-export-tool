package com.sulia.sky9t.config

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

// ============================================================================
// Domain Models
// ============================================================================

data class TextureNameFile(
    val textureName: String,
    val textureFile: String
)

// ============================================================================
// Terrain INI Parser
// ============================================================================

class TerrainIniParser(private val iniPath: Path) {
    private var currentTextureName: String? = null
    private var inCommentBlock = false
    private val map = mutableMapOf<String, TextureNameFile>()

    fun parse(): Map<String, TextureNameFile> {
        if (!iniPath.exists()) {
            printWarning()
            return emptyMap()
        }

        iniPath.readLines().forEach { line ->
            processLine(line.trim())
        }

        printSummary()
        return map
    }

    private fun processLine(line: String) {
        when {
            shouldSkipLine(line) -> return
            isCommentBlockStart(line) -> inCommentBlock = true
            isCommentBlockEnd(line) -> inCommentBlock = false
            inCommentBlock -> return
            isTextureBlockDeclaration(line) -> handleTextureBlockDeclaration(line)
            isTextureDeclaration(line) -> handleTextureDeclaration(line)
            isEndBlock(line) -> currentTextureName = null
        }
    }

    private fun shouldSkipLine(line: String): Boolean {
        return line.isEmpty() || line.startsWith(";")
    }

    private fun isCommentBlockStart(line: String): Boolean {
        return line.startsWith("; Terrain ")
    }

    private fun isCommentBlockEnd(line: String): Boolean {
        return line.startsWith("End")
    }

    private fun isTextureBlockDeclaration(line: String): Boolean {
        return line.startsWith("Terrain ")
    }

    private fun isTextureDeclaration(line: String): Boolean {
        return line.startsWith("Texture = ") && currentTextureName != null
    }

    private fun isEndBlock(line: String): Boolean {
        return line == "End"
    }

    private fun handleTextureBlockDeclaration(line: String) {
        currentTextureName = line.removePrefix("Terrain ").trim()
        inCommentBlock = false
    }

    private fun handleTextureDeclaration(line: String) {
        val textureName = currentTextureName ?: return
        val textureFile = line.removePrefix("Texture = ").trim()

        map[textureName] = TextureNameFile(
            textureName = textureName,
            textureFile = textureFile
        )
    }

    private fun printWarning() {
        println("Warning: terrain.ini not found at $iniPath")
    }

    private fun printSummary() {
        println("Loaded ${map.size} textures")
    }
}

// ============================================================================
// Public API Function
// ============================================================================

fun parseTerrainIni(iniPath: Path): Map<String, TextureNameFile> {
    val parser = TerrainIniParser(iniPath)
    return parser.parse()
}