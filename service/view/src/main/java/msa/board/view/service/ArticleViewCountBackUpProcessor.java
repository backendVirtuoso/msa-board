package msa.board.view.service;

import lombok.RequiredArgsConstructor;
import msa.board.common.event.EventType;
import msa.board.common.event.payload.ArticleViewedEventPayload;
import msa.board.common.outboxmessagerelay.OutboxEventPublisher;
import msa.board.view.entity.ArticleViewCount;
import msa.board.view.repository.ArticleViewCountBackUpRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ArticleViewCountBackUpProcessor {
    private final OutboxEventPublisher outboxEventPublisher;
    private final ArticleViewCountBackUpRepository articleViewCountBackUpRepository;

    @Transactional
    public void backUp(Long articleId, Long viewCount) {
        int result = articleViewCountBackUpRepository.updateViewCount(articleId, viewCount);
        if (result == 0) {
            articleViewCountBackUpRepository.findById(articleId)
                    .ifPresentOrElse(ignored -> { },
                            () -> articleViewCountBackUpRepository.save(ArticleViewCount.init(articleId, viewCount)));
        }

        outboxEventPublisher.publish(
                EventType.ARTICLE_VIEWED,
                ArticleViewedEventPayload.builder()
                        .articleId(articleId)
                        .articleViewCount(viewCount)
                        .build(),
                articleId
        );
    }
}
