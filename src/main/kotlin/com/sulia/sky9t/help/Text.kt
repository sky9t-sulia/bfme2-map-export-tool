package com.sulia.sky9t.help

import java.nio.file.Path
import kotlin.io.path.writeText

fun printHelp() {
    println(
        """
        |Map Exporter Tool - BFME2 Map Utility
        |
        |USAGE:
        |  java -jar map-exporter.jar [OPTIONS]
        |
        |OPTIONS:
        |  -h, -help, --help        Show this help message and exit
        |
        |CONFIGURATION:
        |  All options for map export are controlled via config.ini in the working directory.
        |  Options include:
        |    tile_size, preview_tile_size, split_image_by_blocks, block_size, generate_preview, blend_tiles,
        |    generate_tilemap, generate_heightmap,
        |    path_to_maps_folder, path_to_textures_folder, path_to_output, path_to_terrain_ini
        |
        |EXAMPLE CONFIG.INI:
        |  tile_size=32
        |  preview_tile_size=32
        |  split_image_by_blocks=1
        |  block_size=16
        |  generate_preview=1
        |  blend_tiles=1
        |  generate_tilemap=1
        |  generate_heightmap=1
        |  # relative paths supported
        |  path_to_maps_folder=/path/to/folder
        |  path_to_textures_folder=/path/to/folder
        |  path_to_output=/path/to/output
        |  path_to_terrain_ini=/path/to/terrain_ini
        |
        |Ensure all referenced paths exist and contain the expected files.
        """.trimMargin()
    )
}

fun createDefaultConfigFile(configPath: Path) {
    val defaultConfig = """
    |
    |# Map Exporter Configuration
    |# Usually you dont need to change that value.
    |tile_size = 32
    |
    |# Split Output Image to blocks? 0,1
    |# Each block is saved as a separate image file.
    |# Useful for very large maps to avoid huge image files.
    |split_image_by_blocks = 1
    |
    |# Block size in tiles. Used when splitting output images into blocks
    |block_size = 16
    |
    |# Used to scale down preview image and block images.
    |preview_tile_size = 32
    |
    |
    |# Generate Previews? 0,1
    |generate_preview = 1
    |
    |# Blend Tiles? 0,1
    |blend_tiles = 1
    |
    |# Generate TileMap? 0,1
    |generate_tilemap = 1
    | 
    |# Generate HeightMap? 0,1
    |generate_heightmap = 1
    |
    |# You can get maps list from AOTR game.
    |# Put all .map files you want export into root of that folder
    |# No subfolders supported
    |path_to_maps_folder = PortMaps/MapsToExport
    |
    |# You can get terrain textures by extracting Terrain.big files in Bfme2, RotWK, AOTR installation dirs
    |# Put all textures into root folder
    |# No subfolders supported
    |path_to_textures_folder = PortMaps/Textures
    |
    |# Output directory
    |path_to_output = PortMaps/Output
    |
    |# Terrain.ini essential. It maps tiles to texture files. This should be updated by yours.
    |# I provided one from AOTR 9.0
    |path_to_terrain_ini = terrain.ini
    |""".trimMargin()

    configPath.writeText(defaultConfig)
}