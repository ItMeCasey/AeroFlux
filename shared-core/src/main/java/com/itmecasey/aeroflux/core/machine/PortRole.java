package com.itmecasey.aeroflux.core.machine;

/** What a slot or tank of a machine is for, which decides what the outside world may do with it. */
public enum PortRole {
    /** Things go in here, to be worked on. Nothing may be pulled back out by automation. */
    INPUT,
    /** Finished products wait here. Automation may take them out but never put anything in. */
    OUTPUT,
    /** Holds a supply that may move both ways, like the Flux tank: the face mode alone decides. */
    BUFFER
}
