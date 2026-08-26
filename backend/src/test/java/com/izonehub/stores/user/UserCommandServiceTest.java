package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordPolicy;
import com.izonehub.stores.auth.PasswordResetService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserCommandServiceTest {
    @Test
    void refusesCreatingSystemAdministrator() {
        UserRepository repo = mock(UserRepository.class);
        PasswordResetService resetService = mock(PasswordResetService.class);
        UserCommandService svc = new UserCommandService(repo, new BCryptPasswordEncoder(), new PasswordPolicy(), resetService);

        assertThatThrownBy(() -> svc.createUser("Admin", "a@example.com", "Password1!", java.util.Set.of(Role.SYSTEM_ADMINISTRATOR), null, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repo, never()).save(any());
        // resetService.initiateReset() must never be called since user creation
        // is rejected before it reaches the email step
        verify(resetService, never()).initiateReset(any());
    }
}
