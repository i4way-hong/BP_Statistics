# BP Sample 14

요구사항 기반 Spring Boot 3.5.5 (Java 17) 샘플.

## 실행
```
./gradlew bootRun
```

## 기능
- OAuth Token 발급 REST 호출 (WebClient)
- Thymeleaf + Bootstrap UI
- `/api/token` : JSON 응답
- `/token-view` : 토큰 화면 표시

## 환경변수
`OAUTH_CLIENT_SECRET` 로 클라이언트 시크릿 주입.

## 비밀 암호화(Jasypt)
1. 키 생성: 강한 패스프레이즈 선정 후 환경변수 설정
   Windows PowerShell: `$env:JASYPT_PASSWORD="my-strong-pass"`
2. 암호화 명령(임시 Java Snippet):
   `java -cp build\\classes\\java\\main;build\\libs\\bp-sample14-0.0.1-SNAPSHOT.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI input=password password=$env:JASYPT_PASSWORD algorithm=PBEWithHMACSHA512AndAES_256`
3. 결과 ENC(...) 문구를 `application.yml` 에 반영.

## Observability
- Micrometer Tracing (Brave) + Zipkin 호환 Export
- Prometheus 엔드포인트: `/actuator/prometheus` (추가로 actuator 의존성 필요 시 요청)

## 테스트
`./gradlew test` 실행. 예시: `RestSampleServiceTest` MockWebServer 사용.

## TODO
- Jasypt 실제 암호화 처리
- 예외 처리 / 로깅 고도화
