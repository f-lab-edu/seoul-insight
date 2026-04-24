package dev.jazzybyte.onseoul.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jazzybyte.onseoul.auth.dto.TokenResponse;
import dev.jazzybyte.onseoul.domain.User;
import dev.jazzybyte.onseoul.domain.UserStatus;
import dev.jazzybyte.onseoul.repository.UserRepository;
import dev.jazzybyte.onseoul.security.jwt.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final long REFRESH_TOKEN_TTL_DAYS = 7L;

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OAuth2LoginSuccessHandler(final UserRepository userRepository,
                                     final JwtProvider jwtProvider,
                                     final StringRedisTemplate redisTemplate,
                                     final ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        Object idAttr = oauth2User.getAttribute("sub") != null
                ? oauth2User.getAttribute("sub")
                : oauth2User.getAttribute("id");
        String providerId = idAttr != null ? idAttr.toString() : null;

        String email;
        String nickname;
        if ("kakao".equals(provider)) {
            Map<String, Object> kakaoAccount = oauth2User.getAttribute("kakao_account");
            Map<String, Object> properties = oauth2User.getAttribute("properties");
            email    = kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
            nickname = properties   != null ? (String) properties.get("nickname") : null;
        } else {
            email    = oauth2User.getAttribute("email");
            nickname = oauth2User.getAttribute("name");
        }

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .map(existing -> {
                    existing.updateProfile(email, nickname);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(provider)
                                .providerId(providerId)
                                .email(email)
                                .nickname(nickname)
                                .build()
                ));

        // M-3: Block SUSPENDED/DELETED users from receiving tokens
        if (user.getStatus() != UserStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    Map.of("code", "FORBIDDEN", "message", "비활성화된 계정입니다."));
            return;
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        // TODO(보안): Refresh Token을 HttpOnly; Secure; SameSite=Strict 쿠키로 이동 권장.
        // 현재는 SPA 편의를 위해 JSON body로 반환하지만 XSS 노출 위험이 있음.
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        String redisKey = "RT:" + user.getId();
        redisTemplate.opsForValue().set(redisKey, refreshToken, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);

        TokenResponse tokenResponse = new TokenResponse(accessToken, refreshToken);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getWriter(), tokenResponse);
    }
}
