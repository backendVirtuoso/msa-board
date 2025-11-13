# 🚀 MSA 게시판 시스템 (Microservice Architecture Board)

> **대규모 트래픽을 처리하는 이벤트 기반 마이크로서비스 게시판 플랫폼**

## 📋 프로젝트 개요

이 프로젝트는 **마이크로서비스 아키텍처(MSA)** 기반의 확장 가능한 게시판 시스템입니다. 
이벤트 기반 아키텍처, CQRS 패턴, Outbox 패턴 등 **대규모 서비스에 필수적인 분산 시스템 패턴**을 적용하여 개발되었습니다.

### 핵심 기술 스택
- **Language & Framework**: Java 21, Spring Boot 3.4.2
- **Build Tool**: Gradle (Multi-Module)
- **Database**: MySQL, Redis
- **Message Queue**: Apache Kafka
- **Architecture Pattern**: MSA, Event-Driven, CQRS, Outbox Pattern

---

## 🏗️ 아키텍처 설계

### 1. 마이크로서비스 구성

```
📦 msa-board
├── 🔧 common/                     # 공통 모듈
│   ├── snowflake                  # 분산 ID 생성기 (Snowflake Algorithm)
│   ├── event                      # 도메인 이벤트 정의
│   ├── outbox-message-relay       # Outbox Pattern 구현
│   └── data-serializer            # 데이터 직렬화 유틸
│
└── 🚀 service/                    # 마이크로서비스들
    ├── article                    # [쓰기] 게시글 Command
    ├── article-read               # [읽기] 게시글 Query (CQRS)
    ├── comment                    # 댓글 관리
    ├── hot-article                # 인기 게시글 집계
    ├── like                       # 좋아요 관리
    └── view                       # 조회수 관리
```

### 2. 핵심 아키텍처 패턴

#### ✅ CQRS (Command Query Responsibility Segregation)
- **Command**: `article` 서비스 - 게시글 생성/수정/삭제
- **Query**: `article-read` 서비스 - 게시글 조회 (캐싱 최적화)
- **효과**: 읽기/쓰기 워크로드 분리로 성능 최적화 및 확장성 확보

#### ✅ Outbox Pattern (트랜잭션 보장)
- DB 트랜잭션과 메시지 발행의 원자성 보장
- `OutboxEventPublisher`를 통한 이벤트 발행
- Kafka로 안정적인 이벤트 전달

#### ✅ Event-Driven Architecture
- 서비스 간 Kafka를 통한 비동기 통신
- 느슨한 결합(Loose Coupling)으로 서비스 독립성 확보
- 8가지 도메인 이벤트:
  - `ARTICLE_CREATED`, `ARTICLE_UPDATED`, `ARTICLE_DELETED`
  - `COMMENT_CREATED`, `COMMENT_DELETED`
  - `ARTICLE_LIKED`, `ARTICLE_UNLIKED`
  - `ARTICLE_VIEWED`

#### ✅ Snowflake ID 생성 알고리즘
- 분산 환경에서 충돌 없는 고유 ID 생성
- 64bit: 41bit(timestamp) + 10bit(nodeId) + 12bit(sequence)
- DB 의존 없이 ID 생성 가능 → 성능 향상

---

## 🎯 주요 기능 상세

### 📝 1. Article Service (게시글 Command)
**담당**: 게시글 생성, 수정, 삭제

**핵심 구현**:
```java
@Transactional
public ArticleResponse create(ArticleCreateRequest request) {
    // 1. Snowflake로 고유 ID 생성
    Article article = articleRepository.save(
        Article.create(snowflake.nextId(), ...)
    );
    
    // 2. Outbox Pattern으로 이벤트 발행
    outboxEventPublisher.publish(
        EventType.ARTICLE_CREATED,
        ArticleCreatedEventPayload.builder()...build(),
        article.getBoardId()
    );
    
    return ArticleResponse.from(article);
}
```

**기술적 포인트**:
- JPA를 이용한 MySQL 데이터 영속화
- Outbox 패턴으로 트랜잭션 보장
- 게시판별 게시글 카운트 관리 (`BoardArticleCount`)

---

### 🔍 2. Article-Read Service (게시글 Query)
**담당**: 게시글 조회 최적화 (CQRS Query Side)

**핵심 구현**:
- **최적화된 캐싱 전략**: 커스텀 `@OptimizedCacheable` 어노테이션
- **AOP 기반 캐시 처리**: `OptimizedCacheAspect`
- **Redis 기반 캐싱**: 읽기 성능 극대화
- **이벤트 소싱**: Kafka 이벤트로 Query Model 업데이트

**데이터 구조**:
```
ArticleQueryModel (Redis)
  ├── articleId
  ├── title, content
  ├── likeCount    ← ArticleLikedEvent에서 업데이트
  ├── viewCount    ← ArticleViewedEvent에서 업데이트
  └── commentCount ← CommentCreatedEvent에서 업데이트
```

**기술적 포인트**:
- CQRS 읽기 모델로 조회 성능 최적화
- 무한 스크롤 지원
- 페이징 처리 최적화

---

### 💬 3. Comment Service (댓글)
**담당**: 댓글 생성, 조회, 삭제

**핵심 구현**:
- **계층형 댓글 구조**: `CommentPath`를 통한 대댓글 관리
- **V2 개선**: `CommentV2`로 성능 개선된 댓글 시스템
- **게시글별 댓글 수 집계**: `ArticleCommentCount`

**기술적 포인트**:
- 대댓글 depth 관리
- 무한 스크롤 지원
- Outbox 패턴 적용

---

### ❤️ 4. Like Service (좋아요)
**담당**: 게시글 좋아요/취소

**핵심 구현**:
```java
@Transactional
public void like(Long articleId, Long userId) {
    // 1. 좋아요 저장
    articleLikeRepository.save(...);
    
    // 2. 좋아요 수 증가
    articleLikeCountRepository.increase(articleId);
    
    // 3. 이벤트 발행
    outboxEventPublisher.publish(
        EventType.ARTICLE_LIKED,
        ArticleLikedEventPayload.builder()...build(),
        articleId
    );
}
```

**기술적 포인트**:
- 중복 좋아요 방지
- 좋아요 수 실시간 업데이트
- 이벤트 기반 비동기 처리

---

### 👁️ 5. View Service (조회수)
**담당**: 게시글 조회수 관리

**핵심 구현**:
- **Redis 기반 고성능 카운팅**
- **분산 락(Distributed Lock)** 처리
- **백업 프로세서**: Redis → MySQL 주기적 동기화

**기술적 포인트**:
- Redis Incr 명령으로 원자적 증가
- 조회수 중복 방지 로직
- 비동기 백업 처리

---

### 🔥 6. Hot-Article Service (인기 게시글)
**담당**: 실시간 인기 게시글 집계

**핵심 알고리즘**:
```java
HotScore = (ViewWeight × ViewCount) 
         + (LikeWeight × LikeCount) 
         + (CommentWeight × CommentCount) 
         × TimeDecay
```

**이벤트 기반 실시간 업데이트**:
- `ArticleCreatedEvent` → 신규 게시글 추가
- `ArticleViewedEvent` → 조회수 반영
- `ArticleLikedEvent` → 좋아요 수 반영
- `CommentCreatedEvent` → 댓글 수 반영

**기술적 포인트**:
- Redis Sorted Set으로 실시간 랭킹 관리
- 시간 감쇠 함수로 최신성 반영
- Event Sourcing 패턴 적용

---

## 🛠️ 공통 모듈 상세

### 🆔 Snowflake (분산 ID 생성기)
```
64bit ID 구조:
[1bit: unused] [41bit: timestamp] [10bit: nodeId] [12bit: sequence]
```

**장점**:
- DB 시퀀스 없이 ID 생성 가능
- 시간순 정렬 가능 (timestamp 기반)
- 초당 409만 개 ID 생성 가능 (노드당)

---

### 📡 Outbox Message Relay
**Outbox Pattern 완전 구현**:
1. `OutboxEventPublisher` - 이벤트 발행 인터페이스
2. `Outbox` Entity - DB에 이벤트 임시 저장
3. `MessageRelay` - Outbox → Kafka 전달
4. `MessageRelayCoordinator` - 샤딩 기반 분산 처리

**핵심 코드**:
```java
@Component
public class OutboxEventPublisher {
    public void publish(EventType type, EventPayload payload, Long shardKey) {
        Outbox outbox = Outbox.create(
            outboxIdSnowflake.nextId(),
            type,
            Event.of(eventIdSnowflake.nextId(), type, payload).toJson(),
            shardKey % MessageRelayConstants.SHARD_COUNT
        );
        applicationEventPublisher.publishEvent(OutboxEvent.of(outbox));
    }
}
```

**기술적 포인트**:
- DB 트랜잭션 내에서 이벤트 저장
- 샤딩을 통한 처리 분산
- At-least-once 전달 보장

---

### 🎭 Event (도메인 이벤트)
**8가지 도메인 이벤트 정의**:
```java
public enum EventType {
    ARTICLE_CREATED(ArticleCreatedEventPayload.class, "msa-board-article"),
    ARTICLE_UPDATED(ArticleUpdatedEventPayload.class, "msa-board-article"),
    ARTICLE_DELETED(ArticleDeletedEventPayload.class, "msa-board-article"),
    COMMENT_CREATED(CommentCreatedEventPayload.class, "msa-board-comment"),
    COMMENT_DELETED(CommentDeletedEventPayload.class, "msa-board-comment"),
    ARTICLE_LIKED(ArticleLikedEventPayload.class, "msa-board-like"),
    ARTICLE_UNLIKED(ArticleUnlikedEventPayload.class, "msa-board-like"),
    ARTICLE_VIEWED(ArticleViewedEventPayload.class, "msa-board-view");
}
```

**Kafka Topic 구조**:
- `msa-board-article`: 게시글 이벤트
- `msa-board-comment`: 댓글 이벤트
- `msa-board-like`: 좋아요 이벤트
- `msa-board-view`: 조회수 이벤트

---

## 🎨 설계 철학 & 기술적 고려사항

### 1. 확장성 (Scalability)
- **수평 확장 가능**: 각 서비스 독립 스케일링
- **분산 ID 생성**: DB 병목 현상 제거
- **캐싱 전략**: Redis 기반 읽기 성능 최적화

### 2. 안정성 (Reliability)
- **Outbox Pattern**: 이벤트 발행 실패 방지
- **At-least-once 전달**: 메시지 손실 방지
- **분산 락**: 동시성 제어

### 3. 성능 (Performance)
- **CQRS 패턴**: 읽기/쓰기 최적화
- **비동기 처리**: Kafka 기반 논블로킹
- **인덱스 최적화**: 페이징 쿼리 성능 개선

### 4. 유지보수성 (Maintainability)
- **모듈화**: 공통 로직 재사용
- **이벤트 기반**: 서비스 간 낮은 결합도
- **명확한 책임 분리**: 각 서비스의 단일 책임

---

## 📊 데이터 흐름 예시

### 게시글 생성 Flow
```
1. [Client] POST /v1/articles
   ↓
2. [Article Service] 
   - Snowflake ID 생성
   - MySQL에 저장
   - Outbox 테이블에 이벤트 저장
   - 트랜잭션 커밋
   ↓
3. [MessageRelay]
   - Outbox 폴링
   - Kafka에 ARTICLE_CREATED 이벤트 발행
   ↓
4. [Article-Read Service]
   - 이벤트 소비
   - Redis에 QueryModel 생성
   ↓
5. [Hot-Article Service]
   - 이벤트 소비
   - 인기 게시글 목록에 추가
```

### 좋아요 Flow
```
1. [Client] POST /v1/likes
   ↓
2. [Like Service]
   - 좋아요 저장
   - 카운트 증가
   - ARTICLE_LIKED 이벤트 발행
   ↓
3. [Article-Read Service]
   - Redis QueryModel 업데이트 (likeCount++)
   ↓
4. [Hot-Article Service]
   - Hot Score 재계산
   - 랭킹 업데이트
```

---

## 🚀 실행 방법

### 필수 환경
- JDK 21+
- MySQL 8.0+
- Redis 7.0+
- Kafka 3.0+

### 빌드 및 실행
```bash
# 전체 빌드
./gradlew clean build

# 개별 서비스 실행
./gradlew :service:article:bootRun
./gradlew :service:article-read:bootRun
./gradlew :service:comment:bootRun
./gradlew :service:hot-article:bootRun
./gradlew :service:like:bootRun
./gradlew :service:view:bootRun
```

---

## 📈 프로젝트 통계

- **총 서비스 수**: 6개 마이크로서비스
- **공통 모듈**: 4개
- **도메인 이벤트**: 8개
- **Kafka Topic**: 4개
- **Java 클래스**: 약 110개
- **테스트 코드**: 21개

---

## 🎯 핵심 기술 역량 (면접 포인트)

### ✅ 분산 시스템 설계
- MSA 아키텍처 설계 및 구현
- 서비스 간 통신 전략 (동기/비동기)
- 분산 트랜잭션 처리 (Saga, Outbox Pattern)

### ✅ 이벤트 기반 아키텍처
- Kafka 기반 Event-Driven 구현
- 이벤트 소싱 (Event Sourcing)
- CQRS 패턴 실전 적용

### ✅ 성능 최적화
- Redis 캐싱 전략
- 읽기/쓰기 분리
- N+1 문제 해결

### ✅ 데이터 정합성
- Outbox Pattern으로 트랜잭션 보장
- At-least-once 전달 보장
- 분산 락을 통한 동시성 제어

### ✅ 확장 가능한 설계
- Snowflake 알고리즘으로 분산 ID 생성
- 샤딩 기반 메시지 릴레이
- 수평 확장 가능한 아키텍처

---

## 📚 주요 기술 문서

### API 엔드포인트

#### Article Service
- `POST /v1/articles` - 게시글 작성
- `PUT /v1/articles/{id}` - 게시글 수정
- `DELETE /v1/articles/{id}` - 게시글 삭제
- `GET /v1/articles/{id}` - 게시글 조회
- `GET /v1/articles` - 게시글 목록 (페이징)
- `GET /v1/articles/infinite-scroll` - 무한 스크롤

#### Comment Service
- `POST /v1/comments` - 댓글 작성
- `DELETE /v1/comments/{id}` - 댓글 삭제
- `GET /v1/comments` - 댓글 목록 (페이징)

#### Like Service
- `POST /v1/likes` - 좋아요
- `DELETE /v1/likes` - 좋아요 취소

#### View Service
- `POST /v1/views` - 조회수 증가

#### Hot-Article Service
- `GET /v1/hot-articles/articles/date/{dateStr}` - 날짜별 인기 게시글

#### Article-Read Service
- `GET /v1/articles/{id}` - 게시글 조회 (캐싱)
- `GET /v1/articles` - 게시글 목록 (캐싱)
- `GET /v1/articles/infinite-scroll` - 무한 스크롤 (캐싱)

---

## 🤝 기술 스택 요약

| 카테고리 | 기술 |
|---------|-----|
| **Language** | Java 21 |
| **Framework** | Spring Boot 3.4.2, Spring Data JPA |
| **Database** | MySQL 8.0, Redis 7.0 |
| **Message Queue** | Apache Kafka 3.0 |
| **Build Tool** | Gradle 8.x (Multi-Module) |
| **Architecture** | MSA, Event-Driven, CQRS, Outbox Pattern |
| **ID Generation** | Snowflake Algorithm |
| **Caching** | Redis (Custom AOP-based Caching) |

---

## 💡 개선 가능한 부분 (향후 발전 방향)

1. **API Gateway**: Spring Cloud Gateway 도입
2. **Service Discovery**: Eureka 또는 Consul 적용
3. **Circuit Breaker**: Resilience4j로 장애 전파 방지
4. **분산 추적**: Zipkin/Jaeger 적용
5. **모니터링**: Prometheus + Grafana
6. **컨테이너화**: Docker + Kubernetes 배포
7. **인증/인가**: OAuth2 + JWT 적용
8. **Rate Limiting**: Redis 기반 API 제한

---

## 📝 라이선스

이 프로젝트는 학습 및 포트폴리오 목적으로 작성되었습니다.

---

## 👨‍💻 작성자

**프로젝트 분석 및 문서화**: AI Assistant  
**문의**: 프로젝트 담당자에게 직접 연락 바랍니다.

---

<div align="center">

**⭐ 이 프로젝트가 도움이 되었다면 Star를 눌러주세요! ⭐**

</div>
