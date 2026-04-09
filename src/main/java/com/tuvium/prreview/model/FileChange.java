package com.tuvium.prreview.model;

import org.jspecify.annotations.Nullable;

/**
 * A file changed in a pull request, with diff metadata.
 *
 * @param filename file path relative to repo root
 * @param status one of: added, modified, removed, renamed
 * @param additions lines added
 * @param deletions lines deleted
 * @param patch unified diff patch (null for binary files or large diffs)
 */
public record FileChange(String filename, String status, int additions, int deletions, @Nullable String patch) {
}
