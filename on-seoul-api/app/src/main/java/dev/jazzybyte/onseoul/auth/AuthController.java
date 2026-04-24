package dev.jazzybyte.onseoul.auth;

import dev.jazzybyte.onseoul.auth.dto.RefreshRequest;
import dev.jazzybyte.onseoul.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    /**
     * Refresh Token을 받아 새 Access Token과 새 Refresh Token을 발급한다(Token Rotation).
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse tokenResponse = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * 현재 인증된 사용자를 로그아웃 처리한다.
     * JWT 필터가 request attribute "userId"에 Long을 주입한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(required = false) Long userId) {
        if (userId != null) {
            authService.logout(userId);
        }
        return ResponseEntity.noContent().build();
    }
}
