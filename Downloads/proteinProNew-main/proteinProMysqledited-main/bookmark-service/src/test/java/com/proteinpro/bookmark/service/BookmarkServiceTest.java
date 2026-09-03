package com.proteinpro.bookmark.service;

import com.proteinpro.bookmark.dto.BookmarkDtos.CreateBookmarkRequest;
import com.proteinpro.bookmark.kafka.BookmarkEventPublisher;
import com.proteinpro.bookmark.model.Bookmark;
import com.proteinpro.bookmark.repository.BookmarkRepository;
import com.proteinpro.bookmark.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookmarkServiceTest {
    private final BookmarkRepository repository = mock(BookmarkRepository.class);
    private final BookmarkEventPublisher publisher = mock(BookmarkEventPublisher.class);
    private final BookmarkService service = new BookmarkService(repository, publisher);

    @Test
    void createsBookmarkForAuthenticatedOwner() {
        when(repository.existsByUserIdAndProteinId("user-1", "protein-1")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("user-1", new CreateBookmarkRequest(
                "protein-1", Map.of("id", "protein-1", "source", "whey"), "Compare later"));

        assertThat(response.proteinId()).isEqualTo("protein-1");
        verify(publisher).publishCreated(any(Bookmark.class));
    }

    @Test
    void doesNotRevealAnotherUsersBookmark() {
        when(repository.findByIdAndUserId("bookmark-1", "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("user-1", "bookmark-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Bookmark was not found");
    }
}
