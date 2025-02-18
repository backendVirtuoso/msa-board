package msa.board.view.service;

import lombok.RequiredArgsConstructor;
import msa.board.view.repository.ArticleViewCountRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleViewService {
    private final ArticleViewCountRepository articleViewCountRepository;
    private final ArticleViewCountBackUpProcessor articleViewCountBackUpProcessor;
    private static final int BACK_UP_BATCH_SIZE = 100;

    public Long increase(Long articleId, Long userId) {
        Long count = articleViewCountRepository.increase(articleId);
        if (count % BACK_UP_BATCH_SIZE == 0) {
            articleViewCountBackUpProcessor.backUp(articleId, userId);
        }
        return count;
    }

    public Long count(Long articleId) {
        return articleViewCountRepository.read(articleId);
    }
}
