package br.com.conectabyte.knowly.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginRequestListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private LoginCodeService loginCodeService;
    @Mock private MailService mailService;

    @Test
    void generatesAndSendsACodeWhenTheEmailExists() {
        User user = new User("known@example.com");
        when(userRepository.findByEmailIgnoreCase("known@example.com"))
                .thenReturn(Optional.of(user));
        when(loginCodeService.generate("known@example.com")).thenReturn("123456");

        var listener = new LoginRequestListener(userRepository, loginCodeService, mailService);
        listener.handle(new LoginRequestedEvent("known@example.com"));

        verify(mailService).sendLoginCode("known@example.com", "123456");
    }

    @Test
    void doesNothingWhenTheEmailDoesNotExist() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        var listener = new LoginRequestListener(userRepository, loginCodeService, mailService);
        listener.handle(new LoginRequestedEvent("nobody@example.com"));

        verify(loginCodeService, never()).generate(any());
        verifyNoInteractions(mailService);
    }
}
