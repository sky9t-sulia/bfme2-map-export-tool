import heightmap.exportHeightMap
import tilemap.exportTileMapJson
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import config.MapExporterConfig
import config.loadConfig
import config.parseTerrainIni
import de.darkatra.bfme2.map.serialization.MapFileReader
import help.createDefaultConfigFile
import help.printHelp
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

// ============================================================================
// Main Entry Point
// ============================================================================
val gson: Gson = GsonBuilder().setPrettyPrinting().create()

const val CELL_SIZE = 32
const val PREVIEW_CELL_SIZE = CELL_SIZE

fun main(args: Array<String>) {
    // Check for help flags
    if (args.any { it.equals("-h", true) || it.equals("-help", true) }) {
        printHelp()
        return
    }

    // Windows Protected folder check
    // try to create a file in the working directory to check for write permissions
    val testFilePath = Path.of("permission_test.tmp")
    try {
        testFilePath.writeText("permission test")
        testFilePath.toFile().delete()
    } catch (_: Exception) {
        println("Error: No write permissions in the current working directory.")
        println("Possible reasons:")
        println(" - You are running the tool from a protected system folder (e.g., Program Files)")
        println(" - You do not have sufficient user permissions")
        println(" - Windows Protected Folders feature is enabled for this location")
        println("Please run the tool from a different location where you have write permissions.")
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
            exportHeightMap(mapFile, mapOutputDir)
        }

        if (config.generateTileMap) {
            // Export tilemap
            println("\n--- Exporting Tilemap ---")
            exportTileMapJson(
                mapFile,
                terrainMappings,
                mapOutputDir,
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