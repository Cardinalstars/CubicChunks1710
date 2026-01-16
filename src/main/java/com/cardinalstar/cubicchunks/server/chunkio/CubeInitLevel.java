package com.cardinalstar.cubicchunks.server.chunkio;

import com.cardinalstar.cubicchunks.world.api.ICubeProviderServer.Requirement;

public enum CubeInitLevel {

    /**
     * The cube has been created, but not generated.
     */
    None,
    /**
     * The cube has been generated (terrain gen). Corresponds to {@link Requirement#GENERATE}.
     */
    Generated,
    /**
     * The cube has been populated with structures. Corresponds to {@link Requirement#POPULATE}.
     */
    Populated,
    /**
     * The cube's lighting has been calculated. Corresponds to {@link Requirement#LIGHT}.
     */
    Lit;

    public static CubeInitLevel fromRequirement(Requirement effort) {
        return switch (effort) {
            case GET_CACHED, NBT, LOAD -> None;
            case GENERATE -> Generated;
            case POPULATE -> Populated;
            case LIGHT -> Lit;
        };
    }
}
