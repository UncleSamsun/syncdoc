# 제품 서체

- Pretendard Variable v1.3.9: 기존 화면 정의의 self-host 자산을 제품에도 제공한다. 원본은 [Pretendard](https://github.com/orioncactus/pretendard/tree/v1.3.9), 라이선스는 `Pretendard-LICENSE.txt`다.
- JetBrains Mono v2.304: [공식 webfonts](https://github.com/JetBrains/JetBrainsMono/tree/v2.304/fonts/webfonts)의 Regular(400)·Medium(500)·SemiBold(600) woff2. 라이선스는 `JetBrainsMono-OFL.txt`다.

앱이 같은 origin의 `/fonts/`에서 제공한다. 사용자 PC의 설치 서체나 외부 CDN에 의존하지 않는다. 일반 UI/한글은 Pretendard, 코드·실제 식별자만 JetBrains Mono이며 한글은 Pretendard로 대체한다. 라이선스 파일을 자산과 함께 배포한다.
