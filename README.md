#Monew
Monew는 여러 뉴스 API를 통합하여 사용자 맞춤 뉴스를 제공하고, 사용자 활동 내역 및 의견을 기록/관리할 수 있는 플랫폼입니다.
PostgreSQL과 MongoDB 기반으로 데이터를 안전하게 저장하고, Spring Batch로 뉴스 백업/수집을 자동화하며, Spring Actuator와 Prometheus를 통한 모니터링을 지원합니다.

## ✨개요
사용자 및 뉴스 관련 핵심 도메인을 관리하는 Monew의 메인 서버로 REST 기반 서비스 로직을 제공하며 Batch 및 Actuator 모듈과 연동됩니다. 

## ⚙️기술스택
`Java 17` `Spring Boot 3.5.6` `MapStruct` `JPA + QueryDSL` `PostgreSQL` `MongoDB` `AWS ECS` `AWS RDS` `AWS S3`  

## 디렉토리 구조(예시)
```plaintext
monew-mvc/
 ┣ src/main/java/com/monew/monew_server/
 ┃  ┣ auth
 ┃  ┣ config
 ┃  ┣ domain
 ┃  ┃  ┣ article
 ┃  ┃  ┣ comment
 ┃  ┃  ┣ common
 ┃  ┃  ┣ interest
 ┃  ┃  ┣ notification
 ┃  ┃  ┣ user
 ┃  ┃  ┣ user_activity
 ┃  ┣ exception
 ┃  ┣ log
 ┃  ┣ MonewServerApplication
 ┃  ┗ test
 ┃     ┗ java/...
 ┣ config/
 ┣ build.gradle
 ┗ README.md
```
## 🔗ERD
<img width="1777" height="968" alt="image" src="https://github.com/user-attachments/assets/0029923e-3429-4049-8238-bf55468070cc" />

## 🏛️시스템 아키텍쳐
<img width="869" height="393" alt="image" src="https://github.com/user-attachments/assets/33481788-166e-4907-b6fd-96c7cc6fe172" />

## 🌐클라우드 아키텍쳐
<img width="2100" height="924" alt="MoNew" src="https://github.com/user-attachments/assets/c9be4b8f-f0e1-43a5-ae01-dc5a1a5f6c49" />

## 📌주요 기능
### 사용자 관리
- 사용자는 이메일, 닉네임, 비밀번호 정보를 가진다.
- 회원가입 시 이메일 중복 검증 및 유효성 검사를 실행한다.
- 닉네임 수정 기능 제공 (다른 항목은 수정 불가)
- 논리 삭제를 기본 원칙으로 하며, 삭제 후 1일이 지나면 물리 삭제를 수행한다.
- 로그인 성공 시 모든 요청 헤더에 `monew-Request-User-ID`를 포함한다.
- 로그인하지 않는 사용자는 어떤 화면도 접근할 수 없다.
### 관심사 관리
- 관심사는 이름, 키워드, 구독자 수 정보를 가진다.
- 기존 관심사와 80% 이상 유사한 이름은 등록 할 수 없다.
- 키워드는 여러 개 등록 가능하며, 뉴스 기사 검색에 활용된다.
- 키워드 수정 가능, 관심사 삭제 가능.
- 검색 및 정렬(이름 / 구독자 수)기능, 커서 기반 페이지네이션 지원.
- 다른 사용자는 관심사를 구독할 수 있으며, 관심사에 맞는 새 뉴스 기사를 검색할 수 있다.
### 뉴스 기사 관리
- 뉴스 기사는 출처, 원본 링크, 제목, 요약, 날짜, 조회수 등을 가진다.
- 조회수는 동일 사용자의 반복 조회 시 1회로 처리한다.
- Naver, 한국경제/조선일보/연합뉴스 등 다양한 출처에서 API 및 RSS 등으로 수집한다.
- 관심사 키워드를 포함하는 기사만 저장하며, 원본 링크는 중복 불가하다.
- 기사 조회는 관심사, 출처, 날짜 조건으로 가능하며, 다중 조건 검색을 지원한다.
- 정렬 및 커서페이지네이션을 지원한다.
### 댓글관리
- 댓글은 뉴스 기사, 사용자 내용, 날짜, 좋아요 수 정보를 가진다.
- 본인이 작성한 댓글만 수정 가능하다.
- 삭제 시 논리 삭제를 기본으로 하며, 이후 물리적으로 삭제 된다.
- 댓글 목록 조회 시 정렬 및 커서페이지네이션이 적용된다.
- 댓글에 좋아요 및 좋아요 취소 기능을 제공한다.
### 사용자 활동 내역 관리
- 사용자 활동 내역에는 다음 정보가 포함된다.
  - 사용자 정보 중 닉네임, 이메일 정보를 포함한다.
  - 구독 중인 관심사 정보를 포함한다
  - 사용자가 최근에 작성한 댓글 정보를 포함한다.
  - 사용자가 최근 좋아요를 한 댓글 정보를 포함한다.
  - 사용자가 최근 본 뉴스 기사를 포함한다.
- 사용자별 활동 내역 조회 기능 제공.
### 알림 관리
- 알림은 사용자 정보, 내용, 관련 리소스 정보, 확인 여부를 포함한다.
- 관심사 구독 또는 관련 활동 발생시 알림 생성 및 확인 가능하다.
- 사용자의 댓글에 다른 사용자가 좋아요를 클릭할 경우 사용자는 알림을 통해 확인 가능하다.


