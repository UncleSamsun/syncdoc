package io.github.unclesamsun.syncdoc.crypto;

public class TokenCipherNotConfiguredException extends IllegalStateException {

    public TokenCipherNotConfiguredException() {
        super("토큰 암호화키가 없다. SYNCDOC_TOKEN_KEY에 base64로 인코딩한 32바이트 키를 설정해야 한다.");
    }
}
