package com.proteinpro.profile.service;

import com.proteinpro.profile.client.AuthenticationClient;
import com.proteinpro.profile.dto.ProfileDtos.RegistrationRequest;
import com.proteinpro.profile.kafka.UserProfileEventPublisher;
import com.proteinpro.profile.model.UserProfile;
import com.proteinpro.profile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {
    private final UserProfileRepository repository = mock(UserProfileRepository.class);
    private final AuthenticationClient client = mock(AuthenticationClient.class);
    private final UserProfileEventPublisher publisher = mock(UserProfileEventPublisher.class);
    private final UserProfileService service = new UserProfileService(repository, client, publisher, "internal-key");

    @Test
    void registrationStoresProfileWithoutPasswordAndPublishesIdentityEvent() {
        when(repository.existsByEmail("learner@example.com")).thenReturn(false);
        when(client.createCredential(eq("internal-key"), any())).thenReturn(ResponseEntity.status(201).build());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.register(new RegistrationRequest(
                "Ada", "Lovelace", "LEARNER@example.com", "password123"));

        assertThat(response.email()).isEqualTo("learner@example.com");
        assertThat(response.firstName()).isEqualTo("Ada");
        ArgumentCaptor<UserProfile> profile = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(profile.capture());
        verify(publisher).publishCreated(profile.getValue());
        assertThat(profile.getValue().getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("password"));
    }
}
