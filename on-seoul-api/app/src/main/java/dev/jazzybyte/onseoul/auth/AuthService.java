package dev.jazzybyte.onseoul.auth;

import dev.jazzybyte.onseoul.auth.dto.TokenResponse;
import dev.jazzybyte.onseoul.domain.User;
import dev.jazzybyte.onseoul.domain.UserStatus;
import dev.jazzybyte.onseoul.exception.ErrorCode;
import dev.jazzybyte.onseoul.exception.OnSeoulApiException;
import dev.jazzybyte.onseoul.repository.UserRepository;
import dev.jazzybyte.onseoul.security.jwt.JwtProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final long REFRESH_TOKEN_TTL_DAYS = 7L;

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    public AuthService(final JwtProvider jwtProvider,
                       final StringRedisTemplate redisTemplate,
                       final UserRepository userRepository) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
    }

    /**
     * Refresh Token을 검증하고 새 Access Token과 새 Refresh Token을 발급한다(Token Rotation).
     *
     * <ol>
     *   <li>Redis에서 {@code RT:{userId}} 존재 + 일치 확인</li>
     *   <li>기존 Refresh Token을 Redis에서 삭제</li>
     *   <li>사용자 status 확인 — ACTIVE가 아니면 FORBIDDEN 예외</li>
     *   <li>새 Refresh Token 생성 후 Redis에 저장 (TTL = 7일)</li>
     *   <li>새 Access Token + 새 Refresh Token 모두 반환</li>
     * </ol>
     *
     * @param refreshToken 클라이언트가 제출한 Refresh Token
     * @return 새로 발급된 AccessToken + RefreshToken 쌍
     */
    public TokenResponse refresh(String refreshToken) {
        Long userId = jwtProvider.extractUserIdFromRefreshToken(refreshToken);

        String redisKey = "RT:" + userId;
        String stored = redisTemplate.opsForValue().get(redisKey);

        if (stored == null || !stored.equals(refreshToken)) {
            throw new OnSeoulApiException(ErrorCode.INVALID_REFRESH_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
        }

        // Invalidate the old refresh token immediately (rotation)
        redisTemplate.delete(redisKey);

        // M-3: Block SUSPENDED/DELETED users at refresh time
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new OnSeoulApiException(ErrorCode.FORBIDDEN, "사용자를 찾을 수 없습니다."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new OnSeoulApiException(ErrorCode.FORBIDDEN, "비활성화된 계정입니다.");
        }

        // Issue new tokens
        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);
        redisTemplate.opsForValue().set(redisKey, newRefreshToken, REFRESH_TOKEN_TTL_DAYS, TimeUnit.DAYS);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    /**
     * 해당 사용자의 Refresh Token을 Redis에서 삭제한다(로그아웃).
     *
     * @param userId 로그아웃할 사용자 ID
     */
    public void logout(Long userId) {
        String redisKey = "RT:" + userId;
        redisTemplate.delete(redisKey);
    }
}
