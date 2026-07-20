package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordPolicy;
import com.izonehub.stores.notification.EmailNotificationGateway;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserCommandServiceTest {
    @Test
    void refusesCreatingSystemAdministrator() {
        UserRepository repo = mock(UserRepository.class);
        EmailNotificationGateway emailGateway = mock(EmailNotificationGateway.class);
        UserCommandService svc = new UserCommandService(repo, new BCryptPasswordEncoder(), new PasswordPolicy(), emailGateway);
        
        assertThatThrownBy(() -> svc.createUser("Admin", "a@example.com", "Password1!", java.util.Set.of(Role.SYSTEM_ADMINISTRATOR), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        
        verify(repo, never()).save(any());
        verify(emailGateway, never()).send(any(), any(), any());
    }
}
