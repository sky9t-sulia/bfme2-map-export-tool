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
  - Supports blending. Having visual bugs though.

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
# Usually you dont need to change that value
tile_size = 32

# Splits image by blocks.
split_image_by_blocks = 1

# Block size for splitting images.
block_size = 16

# Cell size for preview images (smaller = faster)
preview_tile_size = 32


# Export options (0 = disabled, 1 = enabled)

# Will generate a preview image of the heightmap.
# Note: image can be very large for big maps.
generate_preview = 1

# Blend tiles in the preview image using blend masks.
# Note: visual bugs may occur.
blend_tiles = 1

# If not enabled, only heightmap will be generated.
generate_tilemap = 1

# Heightmap export
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

### 4. Blend Masks

- To generate a preview with blending enabled, ensure the `blend-masks` folder is placed in the same directory as the executable.

## Output

For each processed map, the tool creates a subfolder with:

### Heightmap Output
- `heightmap.raw` - 16-bit little-endian RAW heightmap
- `heightmap.png` - Visual preview of the heightmap
- `map.json` - Metadata including dimensions and scale

### Tilemap Output
- `blocks.json` - blocks list
- `blocks/` - Folder containing individual tile images
- `tilemap.png` - Visual preview of the tilemap (optional)

### Example map.json Structure

```json
{
  "MapName": "Mission",
  "Description": "Description",
  "Width": 190,
  "Height": 160,
  "Border": 30,
  "Format": "RAW 16-bit, Little Endian"
}
```
### Example blocks.json Structure

```json
{
  "WorkDirectory": "WorkDir/Output/Mission",
  "TotalBlocks": 120,
  "SizeInPixels": 512,
  "SizeInTiles": 16,
  "Blocks": [
    {
      "Index": 0,
      "X": 0,
      "Y": 0,
      "RelativeFileNamePath": "blocks/tilemap_block_0_0.png"
    },
    {
      "Index": 1,
      "X": 1,
      "Y": 0,
      "RelativeFileNamePath": "blocks/tilemap_block_1_0.png"
    },
    ...
  ]
}
```

## Using with Unity

See the `UnityImporter` folder.

## Credits
- Special thanks to [DarkAtra](https://github.com/DarkAtra) for maintaining the bfme2-modding-utils library. Without it, this project would not be possible.
- Uses [bfme2-modding-utils](https://github.com/DarkAtra/bfme2-modding-utils/tree/main) for BFME2 map file parsing.
- Designed mainly for Unity engine compatibility. But can be used for other engines.
