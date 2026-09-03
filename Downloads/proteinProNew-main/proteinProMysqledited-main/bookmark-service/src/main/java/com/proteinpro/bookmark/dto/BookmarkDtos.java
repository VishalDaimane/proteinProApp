package com.proteinpro.bookmark.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class BookmarkDtos {
    private BookmarkDtos() {
    }

    public record CreateBookmarkRequest(
            @NotBlank @Size(max = 120) String proteinId,
            @NotEmpty Map<String, Object> proteinData,
            @NotBlank @Size(max = 1000) String comment) {
    }

    public record UpdateCommentRequest(
            @NotBlank @Size(max = 1000) String comment) {
    }

    public record BookmarkResponse(
            String id,
            String proteinId,
            Map<String, Object> proteinData,
            String comment,
            Instant createdAt,
            Instant updatedAt) {
    }
}
