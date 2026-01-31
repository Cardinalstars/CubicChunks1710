package com.cardinalstar.cubicchunks.util.biome3d;

/// This is thrown when a [PalettizedBiomeArray]'s palette is full, which indicates that it must be converted to a
/// [ReferenceBiomeArray]. Exceptions are used here to avoid object allocations in a hot path - it's not ideal but it's
/// the cleanest solution that I can think of.
public class PaletteFullError extends RuntimeException {

    public PaletteFullError() {
        super(null, null, false, false);
    }
}
