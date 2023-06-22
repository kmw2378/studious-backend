package nerds.studiousTestProject.user.exception.message;

public enum ExceptionMessage {
    MISMATCH_EMAIL("이메일이 일치하지 않습니다."),
    MISMATCH_PASSWORD("비밀번호가 일치하지 않습니다."),
    ALREADY_EXIST_USER("이미 존재하는 회원입니다."),
    LOGOUT_USER("로그아웃된 회원입니다."),
    MISMATCH_USERNAME_TOKEN("아이디와 토큰값이 일치하지 않습니다."),
    NOT_AUTHORIZE_ACCESS("인증되지 않은 접근입니다."),
    FAIL_TOKEN_CHECK("토큰 검증에 실패했습니다."),
    INVALID_TOKEN("유효하지 않은 토큰입니다."),
    TOKEN_VALID_TIME_EXPIRED("토큰의 유효기간이 만료되었습니다."),
    TOKEN_NOT_FOUND("토큰을 찾을 수 없습니다."),
    USER_NOT_FOUND("유저를 찾을 수 없습니다."),
    MISMATCH_TOKEN("토큰명이 일치하지 않습니다."),
    NOT_SUPPORTED_JWT("JWT 토큰이 지원하지 않습니다."),
    NOT_EXPIRED_REFRESH_TOKEN("Refresh Token이 만료되지 않았습니다.");

    private final String message;

    ExceptionMessage(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
