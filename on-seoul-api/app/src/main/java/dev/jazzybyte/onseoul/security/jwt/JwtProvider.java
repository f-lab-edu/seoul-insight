package dev.jazzybyte.onseoul.security.jwt;

import dev.jazzybyte.onseoul.exception.ErrorCode;
import dev.jazzybyte.onseoul.exception.OnSeoulApiException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {

    private final SecretKey signingKey;
    private final long accessTokenMinutes;
    private final long refreshTokenMinutes;

    public JwtProvider(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.access-token-minutes:15}") long accessTokenMinutes,
            @Value("${jwt.refresh-token-minutes:10080}") long refreshTokenMinutes
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenMinutes = refreshTokenMinutes;
    }

    public String generateAccessToken(long userId) {
        return buildToken(userId, accessTokenMinutes);
    }

    public String generateRefreshToken(long userId) {
        return buildToken(userId, refreshTokenMinutes);
    }

    private String buildToken(long userId, long ttlMinutes) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMinutes * 60_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public boolean validateToken(String token) {
        parseToken(token);
        return true;
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰을 1회 파싱하여 검증과 userId 추출을 동시에 수행한다.
     *
     * <p>유효하지 않거나 만료된 토큰은 {@link Optional#empty()}를 반환한다.
     * 예외를 던지지 않으므로 필터에서 안전하게 사용할 수 있다.</p>
     *
     * @param token JWT 토큰 문자열
     * @return 유효한 토큰이면 userId를 담은 Optional, 그렇지 않으면 empty
     */
    public Optional<Long> extractUserIdSafely(String token) {
        try {
            Claims claims = parseToken(token);
            return Optional.of(Long.parseLong(claims.getSubject()));
        } catch (OnSeoulApiException e) {
            return Optional.empty();
        }
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new OnSeoulApiException(ErrorCode.EXPIRED_TOKEN, "만료된 토큰입니다.", e);
        } catch (SignatureException | MalformedJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            throw new OnSeoulApiException(ErrorCode.INVALID_TOKEN, "유효하지 않은 토큰입니다.", e);
        }
    }
}
