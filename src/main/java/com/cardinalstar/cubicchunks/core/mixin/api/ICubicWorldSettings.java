package com.cardinalstar.cubicchunks.core.mixin.api;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Interface for WorldSettings and WorldInfo allowing to store custom data into the world.
 * The data is will be stored on disk, but will not be sent to client.
 */
@ParametersAreNonnullByDefault
public interface ICubicWorldSettings {

    boolean isCubic();

    void setCubic(boolean cubic);
}
