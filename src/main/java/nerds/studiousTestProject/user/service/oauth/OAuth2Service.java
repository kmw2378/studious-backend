package nerds.studiousTestProject.user.service.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nerds.studiousTestProject.user.dto.oauth.token.OAuth2TokenRequest;
import nerds.studiousTestProject.user.dto.oauth.token.OAuth2TokenResponse;
import nerds.studiousTestProject.user.dto.oauth.userinfo.OAuth2UserInfo;
import nerds.studiousTestProject.user.dto.oauth.userinfo.OAuth2UserInfoFactory;
import nerds.studiousTestProject.user.dto.general.MemberType;
import nerds.studiousTestProject.user.dto.general.token.JwtTokenResponse;
import nerds.studiousTestProject.user.dto.oauth.token.OAuth2LogoutResponse;
import nerds.studiousTestProject.user.entity.Member;
import nerds.studiousTestProject.user.entity.oauth.OAuth2Token;
import nerds.studiousTestProject.user.entity.token.LogoutAccessToken;
import nerds.studiousTestProject.user.entity.token.RefreshToken;
import nerds.studiousTestProject.user.exception.message.ExceptionMessage;
import nerds.studiousTestProject.user.exception.model.UserAuthException;
import nerds.studiousTestProject.user.repository.member.MemberRepository;
import nerds.studiousTestProject.user.repository.oauth.OAuth2TokenRepository;
import nerds.studiousTestProject.user.service.token.LogoutAccessTokenService;
import nerds.studiousTestProject.user.service.token.RefreshTokenService;
import nerds.studiousTestProject.user.util.DateConverter;
import nerds.studiousTestProject.user.util.JwtTokenProvider;
import nerds.studiousTestProject.user.util.JwtTokenUtil;
import nerds.studiousTestProject.user.util.MultiValueMapConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OAuth2Service {
    private final OAuth2TokenRepository oAuth2TokenRepository;
    private final InMemoryClientRegistrationRepository inMemoryClientRegistrationRepository;
    private final LogoutAccessTokenService logoutAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public JwtTokenResponse authorize(String providerName, String code) {
        ClientRegistration provider = inMemoryClientRegistrationRepository.findByRegistrationId(providerName);
        log.info("provider = {}", provider.toString());

        // 토큰 받아오기
        // 이는 실제 사용할 토큰이 아닌 유저 정보를 가져오기 위한 토큰 정보이다.
        OAuth2TokenResponse oAuth2TokenResponse = getSocialToken(code, provider);
        log.debug("token = {}", oAuth2TokenResponse.toString());

        // 소셜 엑세스 토큰을 통해 사용자 정보 받아오기
        Map<String, Object> attributes = getUserAttributes(provider, oAuth2TokenResponse);
        log.info("attributes = {}", attributes);

        // 팩토리 클래스를 통해 구글, 네이버, 카카오 중 알맞는 소셜 사용자 정보를 가져온다.
        OAuth2UserInfo oAuth2UserInfo = getOAuth2UserInfo(providerName, attributes);
        log.info("userInfo = {}", oAuth2UserInfo.toString());

        // 유저 정보를 통해 이메일, 비밀번호 생성 (이 때, 비밀번호는 UUID 를 통해 랜덤으로 생성)
        // 이메일이 안넘어오는 경우 (사용자가 동의 X) 는 UUID를 사용하여 이메일 생성 => 이러면 하나의 사용자에 대해 이메일이 여러 개 생성되므로,,, 필수 제공 정보(이름)을 가지고 고유 이메일 생성?
        String email = oAuth2UserInfo.getEmail();
        String password = UUID.randomUUID().toString();

        // 토큰을 만들기 전 Repository 에 있는지 여부를 확인 후 이를 완료하고 진행하자.
        // 별도로 DB를 두지 말고 Member 로 통합해도 괜찮을 것 같음 (컬럼만 추가하자)
        if (memberRepository.existsByEmail(email)) {
            throw new UserAuthException(ExceptionMessage.ALREADY_EXIST_USER);
        }

        String encode = passwordEncoder.encode(password);   // 생성한 비밀번호를 인코딩
        List<String> roles = Collections.singletonList("USER"); // ROLE 주입 (이는 추후 페이지로 구분하여 자동으로 주입되도록 바꿀 예정)

        // Member 정보 생성
        Member member = Member.builder()
                .email(email)
                .password(encode)
                .roles(roles)
                .type(MemberType.OAUTH)
                .build();

        // 기존 회원 정보에 없다면 저장
        if (!memberRepository.existsByEmail(email)) {
            memberRepository.save(member);
        }

        OAuth2Token oAuth2Token = OAuth2Token.builder()
                .email(email)
                .accessToken(oAuth2TokenResponse.getAccess_token())
                .refreshToken(oAuth2TokenResponse.getRefresh_token())
                .expiredAt(DateConverter.toLocalDateTime(oAuth2TokenResponse.getExpires_in()))
                .build();
        log.info("oAuth2Token = {}", oAuth2Token);

        // 기존 소셜 토큰 정보를 DB에 저장 (추후 로그아웃을 위해)
        OAuth2Token save = oAuth2TokenRepository.save(oAuth2Token);
        log.info("save = {}", save);

        // 만든 이메일, 비밀번호와 소셜 서버로부터 받아온 만료 기간을 통해 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(email, password);
        log.info("accessToken = {}", accessToken);

        // Refresh 토큰 저장소에 만든 Refresh 토큰 저장
        RefreshToken refreshToken = refreshTokenService.saveRefreshToken(email);
        jwtTokenProvider.setRefreshTokenAtCookie(refreshToken);

        return JwtTokenResponse.from(accessToken);
    }

    public void logout(String providerName, String accessToken) {
        String resolvedAccessToken = jwtTokenProvider.resolveToken(accessToken);
        String email = jwtTokenProvider.parseToken(resolvedAccessToken);

        Optional<OAuth2Token> oAuth2TokenOptional = oAuth2TokenRepository.findByEmail(email);
        if (oAuth2TokenOptional.isEmpty()) {
            throw new RuntimeException("올바르지 않은 소셜 정보 입니다.");
        }
        OAuth2Token oAuth2Token = oAuth2TokenOptional.get();

        ClientRegistration provider = inMemoryClientRegistrationRepository.findByRegistrationId(providerName);
        OAuth2LogoutResponse oAuth2LogoutResponse = null;
        try {
            oAuth2LogoutResponse = WebClient.create()
                    .post()
                    .uri("https://kapi.kakao.com/v1/user/logout")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, JwtTokenUtil.TOKEN_PREFIX + " " + oAuth2Token.getAccessToken())
                    .retrieve()
                    .bodyToMono(OAuth2LogoutResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("msg = {}", e.getMessage());
            log.error("status = {}", e.getStatusCode());
            log.error("body = {}", e.getResponseBodyAsString());
        }

        Long remainTime = jwtTokenProvider.getRemainTime(resolvedAccessToken);
        refreshTokenService.deleteRefreshTokenByEmail(email);

        logoutAccessTokenService.saveLogoutAccessToken(LogoutAccessToken.from(email, resolvedAccessToken, remainTime));
    }

    /**
     * 소셜 이름(구글, 카카오, 네이버 중 하나) 와 유저 속성을 통해 알맞는 OAuth2UserInfo 객체를 반환하는 메소드
     * 팩토리 클래스 OAuth2UserInfoFactory 를 통해 소셜 이름에 알맞는 객체를 반환 (다형성 활용)
     * @param providerName 소셜 이름(구글, 카카오, 네이버 중 하나)
     * @param attributes 유저 속성
     * @return 소셜 이름에 알맞는 OAuth2UserInfo 객체
     */
    private OAuth2UserInfo getOAuth2UserInfo(String providerName, Map<String, Object> attributes) {
        OAuth2UserInfo oAuth2UserInfo;
        try {
           oAuth2UserInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(providerName, attributes);
        } catch (IllegalArgumentException e) {
            log.error("providerName = {}", providerName);
            log.error("attributes = {}", attributes);
            throw new RuntimeException("알맞는 소셜 서비스를 찾을 수 없습니다.");
        }

        return oAuth2UserInfo;
    }

    /**
     * 소셜 서버로 부터 토큰 요청을 하는 메소드
     * @param code 프론트로부터 넘겨받은 인가 코드
     * @param provider 소셜에 알맞는 Provider (redirect_uri, token_uri 등..)
     * @return 소셜 서버로 부터 넘겨 받은 토큰 값들을 담은 객체
     */
    private OAuth2TokenResponse getSocialToken(String code, ClientRegistration provider) {
        OAuth2TokenResponse oAuth2TokenResponse = null;
        try {
            oAuth2TokenResponse = WebClient.create()
                    .post()
                    .uri(provider.getProviderDetails().getTokenUri())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .bodyValue(getParams(code, provider))
                    .retrieve()
                    .bodyToMono(OAuth2TokenResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("msg = {}", e.getMessage());
            log.error("status = {}", e.getStatusText());
            log.error("body = {}", e.getResponseBodyAsString());
        }

        return oAuth2TokenResponse;
    }

    /**
     * 소셜 서버로 토큰 발급 요청 시 Body에 넣은 값을 반환하는 메소드
     * @param code 프론트로부터 넘겨받은 인가코드
     * @param provider 소셜에 알맞는 Provider (redirect_uri, token_uri 등..)
     * @return 소셜 서버로 토큰 발급 요청 시 Body에 넣은 값을 담은 객체
     */
    private MultiValueMap<String, String> getParams(String code, ClientRegistration provider) {
        return MultiValueMapConverter.convert(
                new ObjectMapper(),
                OAuth2TokenRequest.builder()
                        .code(code)
                        .grant_type("authorization_code")    // 추후 provider 관련 값으로 수정 예정
                        .redirect_uri(provider.getRedirectUri())
                        .client_id(provider.getClientId())
                        .client_secret(provider.getClientSecret())
                        .build()
        );
    }

    /**
     * 소셜 서버로 부터 발급받은 토큰 값을 통해 해당 유저 속성을 반환하는 메소드
     * @param provider            소셜에 알맞는 Provider (redirect_uri, token_uri 등..)
     * @param oAuth2TokenResponse 소셜 서버로 부터 발급받은 토큰 값들이 저장된 DTO
     * @return 소셜 서버로 부터 발급받은 토큰의 유저 속성
     */
    private Map<String, Object> getUserAttributes(ClientRegistration provider, OAuth2TokenResponse oAuth2TokenResponse) {
        return WebClient.create()
                .get()
                .uri(provider.getProviderDetails().getUserInfoEndpoint().getUri())
                .headers(header -> header.setBearerAuth(oAuth2TokenResponse.getAccess_token()))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}