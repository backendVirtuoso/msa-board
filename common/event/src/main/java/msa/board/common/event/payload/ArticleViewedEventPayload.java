package msa.board.common.event.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import msa.board.common.event.EventPayload;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleViewedEventPayload implements EventPayload {
    private Long articleId;
    private Long articleViewCount;
}
