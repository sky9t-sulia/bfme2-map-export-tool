package com.sulia.sky9t.config

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

// ============================================================================
// Domain Models
// ============================================================================

data class TextureMapping(
    val index: Int,
    val textureName: String,
    val textureFile: String
)

// ============================================================================
// Terrain INI Parser
// ============================================================================

class TerrainIniParser(private val iniPath: Path) {
    private var currentIndex = 0
    private var currentTerrainName: String? = null
    private var inCommentBlock = false
    private val mappings = mutableListOf<TextureMapping>()

    fun parse(): List<TextureMapping> {
        if (!iniPath.exists()) {
            printWarning()
            return emptyList()
        }

        iniPath.readLines().forEach { line ->
            processLine(line.trim())
        }

        printSummary()
        return mappings
    }

    private fun processLine(line: String) {
        when {
            shouldSkipLine(line) -> return
            isCommentBlockStart(line) -> inCommentBlock = true
            isCommentBlockEnd(line) -> inCommentBlock = false
            inCommentBlock -> return
            isTerrainDeclaration(line) -> handleTerrainDeclaration(line)
            isTextureDeclaration(line) -> handleTextureDeclaration(line)
            isEndBlock(line) -> currentTerrainName = null
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

    private fun isTerrainDeclaration(line: String): Boolean {
        return line.startsWith("Terrain ")
    }

    private fun isTextureDeclaration(line: String): Boolean {
        return line.startsWith("Texture = ") && currentTerrainName != null
    }

    private fun isEndBlock(line: String): Boolean {
        return line == "End"
    }

    private fun handleTerrainDeclaration(line: String) {
        currentTerrainName = line.removePrefix("Terrain ").trim()
        inCommentBlock = false
    }

    private fun handleTextureDeclaration(line: String) {
        val terrainName = currentTerrainName ?: return
        val textureFile = line.removePrefix("Texture = ").trim()

        mappings.add(
            TextureMapping(
                index = currentIndex,
                textureName = terrainName,
                textureFile = textureFile
            )
        )
        currentIndex++
    }

    private fun printWarning() {
        println("Warning: terrain.ini not found at $iniPath")
    }

    private fun printSummary() {
        println("Loaded ${mappings.size} terrain texture mappings")
    }
}

// ============================================================================
// Public API Function
// ============================================================================

fun parseTerrainIni(iniPath: Path): List<TextureMapping> {
    val parser = TerrainIniParser(iniPath)
    return parser.parse()
}