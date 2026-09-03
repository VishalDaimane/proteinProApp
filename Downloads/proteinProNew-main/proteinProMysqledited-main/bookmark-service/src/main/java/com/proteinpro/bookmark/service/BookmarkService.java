package com.proteinpro.bookmark.service;

import com.proteinpro.bookmark.dto.BookmarkDtos.BookmarkResponse;
import com.proteinpro.bookmark.dto.BookmarkDtos.CreateBookmarkRequest;
import com.proteinpro.bookmark.kafka.BookmarkEventPublisher;
import com.proteinpro.bookmark.model.Bookmark;
import com.proteinpro.bookmark.repository.BookmarkRepository;
import com.proteinpro.bookmark.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class BookmarkService {
    private final BookmarkRepository repository;
    private final BookmarkEventPublisher eventPublisher;

    public BookmarkService(BookmarkRepository repository, BookmarkEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public BookmarkResponse create(String userId, CreateBookmarkRequest request) {
        if (repository.existsByUserIdAndProteinId(userId, request.proteinId())) {
            throw new ApiException(HttpStatus.CONFLICT, "This protein is already bookmarked");
        }
        Bookmark saved = repository.save(new Bookmark(userId, request.proteinId(),
                request.proteinData(), request.comment().trim()));
        eventPublisher.publishCreated(saved);
        return response(saved);
    }

    public List<BookmarkResponse> findMine(String userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::response).toList();
    }

    public BookmarkResponse updateComment(String userId, String bookmarkId, String comment) {
        Bookmark bookmark = owned(userId, bookmarkId);
        bookmark.setComment(comment.trim());
        bookmark.setUpdatedAt(Instant.now());
        Bookmark saved = repository.save(bookmark);
        eventPublisher.publishUpdated(saved);
        return response(saved);
    }

    public void delete(String userId, String bookmarkId) {
        Bookmark bookmark = owned(userId, bookmarkId);
        repository.delete(bookmark);
        eventPublisher.publishDeleted(bookmark);
    }

    private Bookmark owned(String userId, String bookmarkId) {
        return repository.findByIdAndUserId(bookmarkId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bookmark was not found"));
    }

    private BookmarkResponse response(Bookmark bookmark) {
        return new BookmarkResponse(bookmark.getId(), bookmark.getProteinId(), bookmark.getProteinData(),
                bookmark.getComment(), bookmark.getCreatedAt(), bookmark.getUpdatedAt());
    }
}
