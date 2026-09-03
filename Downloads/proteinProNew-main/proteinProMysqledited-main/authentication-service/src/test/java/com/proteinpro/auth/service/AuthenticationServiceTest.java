package com.proteinpro.auth.service;

import com.proteinpro.auth.dto.AuthDtos.LoginRequest;
import com.proteinpro.auth.kafka.AuthenticationEventPublisher;
import com.proteinpro.auth.model.Credential;
import com.proteinpro.auth.repository.CredentialRepository;
import com.proteinpro.auth.security.JwtTokenService;
import com.proteinpro.auth.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationServiceTest {
    private final CredentialRepository repository = mock(CredentialRepository.class);
    private final AuthenticationEventPublisher publisher = mock(AuthenticationEventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtTokenService tokens = new JwtTokenService(
            "01234567890123456789012345678901", 1800);
    private final AuthenticationService service =
            new AuthenticationService(repository, encoder, tokens, publisher);

    @Test
    void logsInActiveCredentialWithoutExposingPassword() {
        Credential credential = new Credential("user-1", "learner@example.com", encoder.encode("password123"));
        credential.setActive(true);
        when(repository.findByEmail("learner@example.com")).thenReturn(Optional.of(credential));

        var response = service.login(new LoginRequest("LEARNER@example.com", "password123"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(publisher).publishLogin(credential);
    }

    @Test
    void rejectsWrongPassword() {
        Credential credential = new Credential("user-1", "learner@example.com", encoder.encode("password123"));
        credential.setActive(true);
        when(repository.findByEmail("learner@example.com")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.login(new LoginRequest("learner@example.com", "wrong")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid email or password");
    }
}
