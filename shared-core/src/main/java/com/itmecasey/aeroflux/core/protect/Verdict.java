package com.itmecasey.aeroflux.core.protect;

/** One protection source's answer to "may this happen here?". */
public enum Verdict {
    /** The source knows the place and allows it. */
    ALLOW,
    /** The source forbids it. */
    DENY,
    /** The source has nothing to say about the place (not claimed, not installed). */
    ABSTAIN
}
