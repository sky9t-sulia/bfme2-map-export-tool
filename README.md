# BFME2 Map Export Tool

A command-line tool that converts Battle for Middle-earth II (BFME2) `.map` files into heightmaps and tile position data. These exported files can be used to recreate BFME2 maps in different game engines, with Unity as the primary target.

## Features

- **Heightmap Export**: Extracts terrain elevation data as 16-bit RAW heightmaps
  - Automatic padding to Unity-compatible resolutions (power-of-2 + 1)
  - Configurable height scaling for optimal Unity terrain
  - Generates metadata JSON with map dimensions and settings

- **Tilemap Export**: Exports texture tile information as JSON
  - Maps terrain tiles to texture files
  - Includes tile positions and texture offsets
  - Optional preview image generation

- **Batch Processing**: Process multiple `.map` files at once
- **Configurable**: All settings controlled via `config.ini`
- **Flexible Paths**: Supports both relative and absolute file paths

## Requirements

- Java 17 or higher [Download](https://adoptium.net/temurin/releases/?version=17)
- Maven (for building from source)

### Dependencies

The tool uses the following key libraries:
- Kotlin 2.3.0
- [bfme2-modding-utils](https://github.com/DarkAtra/bfme2-modding-utils) - for reading `.map` files
- Gson - for JSON export
- TwelveMonkeys ImageIO - for TGA texture support

## Installation

### Building from Source

```bash
mvn clean package
```

This will create a JAR file in the `target/` directory.

## Configuration

On first run, the tool will generate a default `config.ini` file. Edit this file to configure your paths and export settings.

### Configuration Options

```ini
# Cell size for tile calculations (usually 32)
cell_size = 32

# Cell size for preview images (smaller = faster)
preview_cell_size = 8

# Heightmap scale factor (7 works well for Unity)
heightmap_scale_value = 7

# Export options (0 = disabled, 1 = enabled)
generate_previews = 1
generate_tilemap = 1
generate_heightmap = 1

# Directory containing .map files to export
path_to_maps_folder = PortMaps/MapsToExport

# Directory containing terrain texture files (.tga)
path_to_textures_folder = PortMaps/Textures

# Output directory for exported files
path_to_output = PortMaps/Output

# Path to terrain.ini mapping file
path_to_terrain_ini = terrain.ini
```

## Usage

### Basic Usage

```bash
java -jar bfme2-exporter.jar
```

The tool will:
1. Process all `.map` files in the specified maps folder
2. Export heightmaps and/or tilemaps to the output directory
3. Create a separate subfolder for each map

### Getting Help

```bash
java -jar bfme2-exporter.jar -h
```

## Preparing Files

### 1. Extract Map Files

- Obtain `.map` files from BFME2, ROTWK, or mods like AOTR
- Place all `.map` files in your `path_to_maps_folder` directory (no subfolders)

### 2. Extract Textures

- Extract `Terrain.big` files from your BFME2 installation directory
- Place all `.tga` texture files in your `path_to_textures_folder` (no subfolders)

### 3. Terrain.ini File

- The tool requires a `terrain.ini` file that maps terrain names to texture files
- A sample from AOTR 9.0 is included
- Update this file to match your game version if needed

## Output

For each processed map, the tool creates a subfolder with:

### Heightmap Output
- `heightmap.raw` - 16-bit little-endian RAW heightmap
- `heightmap.png` - Visual preview of the heightmap
- `heightmap_info.json` - Metadata including dimensions and scale

### Tilemap Output
- `tilemap.json` - Complete tile and texture information in JSON format
- `preview.png` - Visual preview of the tilemap (optional)

### Example tilemap.json Structure

```json
{
  "mapName": "ExampleMap",
  "dimensions": {
    "width": 256,
    "length": 256,
    "tileSize": 32
  },
  "tiles": [
    {
      "tileValue": 1,
      "textureIndex": 0,
      "cell": [0, 0],
      "textureOffset": [0, 0]
    }
  ],
  "textures": [
    {
      "index": 0,
      "size": 128,
      "name": "GrassType1",
      "fileName": "txgrass01a.tga",
      "normalMapFileName": "txgrass01a_nrm.tga"
    }
  ]
}
```

## Using with Unity

THERE WILL BE LINK TO A TOOL FOR UNITY IMPORT. ONCE IT IS READY.

1. Import the heightmap:
   - Create a new Terrain object
   - Use "Import Raw" and select `heightmap.raw`
   - Set resolution to match the values in `heightmap_info.json`
   - Set height to the scale value (typically 7)

2. Apply textures:
   - Import all texture files from your textures folder
   - Parse `tilemap.json` to apply textures at correct positions
   - Use the tile positions and texture indices to recreate the terrain appearance

## Project Structure

```
bfme2-exporter/
├── src/main/kotlin/
│   ├── MapExporter.kt          # Main entry point
│   ├── Helpers.kt              # Utility functions
│   ├── config/                 # Configuration loaders
│   ├── heightMap/              # Heightmap export logic
│   ├── tileMap/                # Tilemap export logic
│   └── help/                   # Help and default config
├── config.ini                  # User configuration
├── terrain.ini                 # Terrain-to-texture mappings
└── pom.xml                     # Maven build configuration
```

## Credits
- Special thanks to [DarkAtra](https://github.com/DarkAtra) for maintaining the bfme2-modding-utils library. Without it, this project would not be possible.
- Uses [bfme2-modding-utils](https://github.com/DarkAtra/bfme2-modding-utils/tree/main) for BFME2 map file parsing.
- Designed mainly for Unity engine compatibility. But can be used for other engines.