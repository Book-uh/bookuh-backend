# 북어 백엔드

Java 17 · Spring Boot 3.5.16 · Maven 기본 프로젝트.

`pom.xml`을 IntelliJ IDEA에서 열면 된다. 현재 Spring Web과 기본 테스트 의존성, 서버 시작 파일만 준비되어 있다.

## 실행

`backend` 폴더에서:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw spring-boot:run
```

서버는 기본 8081 포트에서 시작한다. 기능과 데이터베이스 연결은 직접 작성하며 진행한다.
