package io.github.markpollack.prreview.model;

/**
 * A single file with a merge conflict, classified by complexity.
 *
 * @param path file path relative to repo root
 * @param classification SIMPLE or COMPLEX
 * @param description what kind of conflict
 */
public record ConflictFile(String path, Classification classification, String description) {
}
