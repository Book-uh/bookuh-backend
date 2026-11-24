# 북어 백엔드

북어는 말로 독서 기록을 남기고, 읽은 장면에 맞춰 책 속 인물과 편지와 채팅을 나누는 iOS 앱입니다. 이 저장소는 북어의 Spring Boot API 서버입니다.

## 주요 기능

- Google Books에서 책을 검색하고, 원하는 판본을 책장에 담습니다. 책장에서 뺐다가 같은 책을 다시 담으면 이전 진도와 기록, 편지와 채팅을 이어 볼 수 있습니다.
- 북어 비서가 대화에서 독서 시간, 페이지, 읽은 장면을 해석합니다. 기록에 기반해 이번 주와 이번 달 독서 통계를 제공합니다.
- Claude에 인물 설정과 독서 진도를 전달해 인물의 답장을 생성합니다. 필요한 편지 답장 수를 채우면 채팅이 열립니다. 채팅 답변에는 같은 진도에서 나눈 문답 중 참고 가능한 최근 10턴까지 전달합니다.

## 기술 스택

Java 17 · Spring Boot 3.5.16 · Gradle 8.14.3 · Spring Data JPA · MySQL · AWS EC2 · Nginx

## 로컬 실행

1. JDK 17과 MySQL을 준비합니다. 로컬 MySQL에 `bookuh_db` 데이터베이스와 해당 데이터베이스에 접근할 수 있는 `bookuh` 계정을 생성합니다. 접속 주소와 계정은 [`application.yml`](src/main/resources/application.yml)에서 확인할 수 있습니다.
2. IntelliJ 실행 구성 또는 서버를 실행할 터미널에 아래 환경 변수를 설정합니다.

   | 변수 | 값 |
   | --- | --- |
   | `DB_PASSWORD` | MySQL `bookuh` 계정 비밀번호 |
   | `GOOGLE_BOOKS_API_KEY` | Google Books API 키 |
   | `ANTHROPIC_API_KEY` | Claude API 키 |
   | `AUTH_TOKEN_SECRET` | Base64로 인코딩한 32바이트 이상의 난수. 로컬 개발용 값은 `openssl rand -base64 32`로 생성할 수 있습니다. |

3. 저장소 루트에서 서버를 실행합니다.

   ```sh
   ./gradlew bootRun
   ```

4. 별도 터미널에서 서버 응답을 확인합니다.

   ```sh
   curl -f http://localhost:8081/api/v1/health
   ```

AWS EC2 배포 환경에서는 앱의 HTTPS 요청을 Nginx가 Spring Boot 서버로 전달합니다.

## 주요 API

| 메서드 | 경로 | 기능 |
| --- | --- | --- |
| POST | `/api/v1/auth/anonymous` | 익명 사용자와 접속 토큰 발급 |
| GET | `/api/v1/books/search` | Google Books 도서 검색 |
| GET | `/api/v1/bookshelf` | 내 책장 조회 |
| PUT · DELETE | `/api/v1/bookshelf/{isbn}` | 책장에 담기 · 빼기 |
| PUT | `/api/v1/bookshelf/{isbn}/progress` | 읽은 지점 저장 |
| POST | `/api/v1/manager/interpret` | 북어 비서 메시지 처리 |
| GET | `/api/v1/reading-stats` | 독서 통계 조회 |
| POST | `/api/v1/letters` | 인물에게 편지 보내기 |
| POST | `/api/v1/roleplay/replies` | 인물 채팅 답변 생성 |

## 관련 저장소

[북어 iOS](https://github.com/Book-uh/bookuh-mobile)
