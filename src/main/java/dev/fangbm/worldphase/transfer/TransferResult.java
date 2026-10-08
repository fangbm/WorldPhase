package dev.fangbm.worldphase.transfer;

/** Normal outcomes. A native teleport exception/inconsistent commit is propagated, not a retryable result. */
public enum TransferResult {
    SUCCESS,
    SAME_PHASE,
    TARGET_LEVEL_MISSING,
    NO_SAFE_POSITION,
    CHUNK_NOT_READY,
    CHUNK_FAILURE,
    OUT_OF_WORLD,
    INVALID_SOURCE,
    ERROR
}
