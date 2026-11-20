package com.ael.algoryqrservice.qrservice.console.service;

/** Published inside the finishing transaction; listeners act after it commits. */
public record ConsoleSessionFinishedEvent(Long sessionId) {
}
