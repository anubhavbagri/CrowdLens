package com.crowdlens.service;

import java.io.Serializable;
import java.util.UUID;

/**
 * Message payload stored in Redis by rqueue.
 * Only the jobId travels over the wire — the listener reads full parameters from SQLite.
 */
public record SearchJobMessage(UUID jobId) implements Serializable {}
