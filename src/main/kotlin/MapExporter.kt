import heightMap.exportHeightMap
import tileMap.exportTileMapJson
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import config.MapExporterConfig
import config.loadConfig
import config.parseTerrainIni
import de.darkatra.bfme2.map.serialization.MapFileReader
import help.createDefaultConfigFile
import help.printHelp
import java.nio.file.Path
import kotlin.io.path.*

// ============================================================================
// Main Entry Point
// ============================================================================
val gson: Gson = GsonBuilder().setPrettyPrinting().create()

const val CELL_SIZE = 32
const val PREVIEW_CELL_SIZE = CELL_SIZE

// This value is experimental. I've got this value by tests.
const val HEIGHTMAP_SCALE = 7.0

fun main(args: Array<String>) {
    // Check for help flags
    if (args.any { it.equals("-h", true) || it.equals("-help", true) }) {
        printHelp()
        return
    }

    // Load configuration from config.ini
    val configPath = Path.of("config.ini")
    if (!configPath.exists()) {
        createDefaultConfigFile(configPath)
        println("Default config.ini created. Please review and adjust as needed.")
        return
    }
    val config = loadConfig(configPath)

    println("=== Map Exporter Configuration ===")
    println("Maps Folder: ${config.pathToMapsFolder}")
    println("Textures Folder: ${config.pathToTexturesFolder}")
    println("Output Folder: ${config.pathToOutput}")
    println("Terrain INI: ${config.pathToTerrainIni}")
    println()

    // Create output directory if it doesn't exist
    config.pathToOutput.createDirectories()

    // Load terrain mappings once (shared across all maps)
    val terrainMappings = parseTerrainIni(config.pathToTerrainIni)

    try {
        // Process all .map files in the maps folder
        val mapFiles = config.pathToMapsFolder.listDirectoryEntries("*.map")

        if (mapFiles.isEmpty()) {
            println("No .map files found in ${config.pathToMapsFolder}")
            return
        }

        println("Found ${mapFiles.size} map file(s) to process\n")

        mapFiles.forEach { mapFilePath ->
            processMap(mapFilePath, config, terrainMappings)
        }
    } catch (e: Exception) {
        println("Error: ${e.javaClass.name}: ${e.message}")
        return
    }



    println("\n=== Export Complete ===")
}

// ============================================================================
// Map Processing
// ============================================================================
fun processMap(mapFilePath: Path, config: MapExporterConfig, terrainMappings: Map<String, String>) {
    val mapFileName = mapFilePath.fileName.toString().removeSuffix(".map")
    val mapOutputDir = config.pathToOutput.resolve(mapFileName)

    println("====================================")
    println("Processing: $mapFileName")
    println("====================================")

    // Create map-specific output directory
    mapOutputDir.createDirectories()

    try {
        // Read the map file
        val mapFile = MapFileReader().read(mapFilePath)

        if (config.generateHeightMap) {
            // Export heightmap
            println("\n--- Exporting Heightmap ---")
            exportHeightMap(mapFile, mapOutputDir, config)
        }

        if (config.generateTileMap) {
            // Export tilemap
            println("\n--- Exporting Tilemap ---")
            exportTileMapJson(
                mapFile,
                terrainMappings,
                mapOutputDir,
                mapFileName,
                config
            )
        }

        if (config.generateHeightMap || config.generateTileMap) {
            println("\nSuccessfully exported: $mapFileName")
            println("Output directory: ${mapOutputDir.absolutePathString()}")
        } else {
            println("Nothing exported! Check your config")
        }
    } catch (e: Exception) {
        println("ERROR processing $mapFileName: ${e.message}")
    }

    println()
}