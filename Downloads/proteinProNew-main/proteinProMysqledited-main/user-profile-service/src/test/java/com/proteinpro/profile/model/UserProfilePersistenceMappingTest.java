package com.proteinpro.profile.model;

import com.proteinpro.profile.repository.UserProfileRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfilePersistenceMappingTest {
    @Test
    void usesJpaWithoutChangingTheExistingUserId() {
        String userId = "1eaa3f3f-2f42-4b91-88eb-b2aaf438ac46";
        UserProfile profile = new UserProfile(userId, "Ada", "Lovelace", "learner@example.com");

        assertThat(UserProfile.class).hasAnnotation(Entity.class);
        assertThat(UserProfile.class.getAnnotation(Table.class).name()).isEqualTo("user_profiles");
        assertThat(JpaRepository.class).isAssignableFrom(UserProfileRepository.class);
        assertThat(profile.getId()).isEqualTo(userId);
    }
}
