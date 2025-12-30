package com.sulia.sky9t

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sulia.sky9t.config.MapExporterConfig
import com.sulia.sky9t.config.TextureNameFile
import com.sulia.sky9t.config.loadConfig
import com.sulia.sky9t.config.parseTerrainIni
import de.darkatra.bfme2.map.MapFile
import de.darkatra.bfme2.map.serialization.MapFileReader
import com.sulia.sky9t.heightmap.HeightMapExporter
import com.sulia.sky9t.help.createDefaultConfigFile
import com.sulia.sky9t.help.printHelp
import com.sulia.sky9t.texture.TextureManager
import com.sulia.sky9t.tilemap.TileMapExporter
import java.nio.file.Path
import kotlin.io.path.*

// ============================================================================
// Constants
// ============================================================================

const val TILE_SIZE = 32u
const val PREVIEW_TILE_SIZE = TILE_SIZE
const val BLOCK_SIZE = 16u

val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

// ============================================================================
// Main Entry Point
// ============================================================================

fun main(args: Array<String>) {
    if (shouldShowHelp(args)) {
        printHelp()
        return
    }

    if (!hasWritePermissions()) {
        printPermissionError()
        return
    }

    val config = initializeConfig() ?: return

    MapExportRunner(config).run()
}

// ============================================================================
// Application Runner
// ============================================================================

class MapExportRunner(private val config: MapExporterConfig) {
    private val texturesMap: Map<String, TextureNameFile> by lazy {
        parseTerrainIni(config.pathToTerrainIni)
    }

    fun run() {
        printConfiguration()
        ensureOutputDirectory()

        val mapFiles = findMapFiles()
        if (mapFiles.isEmpty()) {
            println("No .map files found in ${config.pathToMapsFolder}")
            return
        }

        processAllMaps(mapFiles)
        printCompletionMessage()
    }

    private fun printConfiguration() {
        println("=== Map Exporter Configuration ===")
        println("Maps Folder: ${config.pathToMapsFolder}")
        println("Textures Folder: ${config.pathToTexturesFolder}")
        println("Output Folder: ${config.pathToOutput}")
        println("Terrain INI: ${config.pathToTerrainIni}")
        println()
    }

    private fun ensureOutputDirectory() {
        config.pathToOutput.createDirectories()
    }

    private fun findMapFiles(): List<Path> {
        return try {
            config.pathToMapsFolder.listDirectoryEntries("*.map")
        } catch (e: Exception) {
            println("Error: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private fun processAllMaps(mapFiles: List<Path>) {
        println("Found ${mapFiles.size} map file(s) to process\n")

        mapFiles.forEach { mapFilePath ->
            MapProcessor(mapFilePath, config, texturesMap).process()
        }
    }

    private fun printCompletionMessage() {
        println("\n=== Export Complete ===")
    }
}

// ============================================================================
// Map Processor
// ============================================================================

class MapProcessor(
    private val mapFilePath: Path,
    private val config: MapExporterConfig,
    private val texturesMap: Map<String, TextureNameFile>
) {
    private val mapName = mapFilePath.fileName.toString().removeSuffix(".map")
    private val outputDir = config.pathToOutput.resolve(mapName)

    fun process() {
        printHeader()
        outputDir.createDirectories()

        try {
            val mapFile = readMapFile()
            exportMapData(mapFile)
            printSuccess()
        } catch (e: Exception) {
            printError(e)
        }

        println()
    }

    private fun readMapFile(): MapFile {
        return MapFileReader().read(mapFilePath)
    }

    private fun exportMapData(mapFile: MapFile) {
        var exportedAnything = false

        if (config.generateHeightMap) {
            exportHeightMap(mapFile)
            exportedAnything = true
        }

        if (config.generateTileMap) {
            exportTileMap(mapFile)
            exportedAnything = true
        }

        if (!exportedAnything) {
            println("Nothing exported! Check your config")
        }
    }

    private fun exportHeightMap(mapFile: MapFile) {
        println("\n--- Exporting Heightmap ---")
        HeightMapExporter(mapFile).export(outputDir)
    }

    private fun exportTileMap(mapFile: MapFile) {
        println("\n--- Exporting Tilemap ---")
        val textureManager = TextureManager(mapFile, texturesMap, config)
        TileMapExporter(mapFile, textureManager, config).export(outputDir)
    }

    private fun printHeader() {
        println("====================================")
        println("Processing: $mapName")
        println("====================================")
    }

    private fun printSuccess() {
        println("\nSuccessfully exported: $mapName")
        println("Output directory: ${outputDir.absolutePathString()}")
    }

    private fun printError(e: Exception) {
        println("ERROR processing $mapName: ${e.message}")
    }
}

// ============================================================================
// Initialization Helpers
// ============================================================================

private fun shouldShowHelp(args: Array<String>): Boolean {
    return args.any { it.equals("-h", ignoreCase = true) || it.equals("-help", ignoreCase = true) }
}

private fun hasWritePermissions(): Boolean {
    val testFilePath = Path.of("permission_test.tmp")
    return try {
        testFilePath.writeText("permission test")
        testFilePath.toFile().delete()
        true
    } catch (_: Exception) {
        false
    }
}

private fun printPermissionError() {
    println("Error: No write permissions in the current working directory.")
    println("Possible reasons:")
    println(" - You are running the tool from a protected system folder (e.g., Program Files)")
    println(" - You do not have sufficient user permissions")
    println(" - Windows Protected Folders feature is enabled for this location")
    println("Please run the tool from a different location where you have write permissions.")
}

private fun initializeConfig(): MapExporterConfig? {
    val configPath = Path.of("config.ini")

    if (!configPath.exists()) {
        createDefaultConfigFile(configPath)
        println("Default config.ini created. Please review and adjust as needed.")
        return null
    }

    return loadConfig(configPath)
}