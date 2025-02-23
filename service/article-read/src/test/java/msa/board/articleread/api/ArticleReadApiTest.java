package msa.board.articleread.api;

import msa.board.articleread.service.response.ArticleReadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class ArticleReadApiTest {
    RestClient restClient = RestClient.create("http://localhost:9005");

    @Test
    void readTest() {
        ArticleReadResponse response = restClient.get()
                .uri("/v1/articles/{articleId}", 146878965155254272L)
                .retrieve()
                .body(ArticleReadResponse.class);

        System.out.println(response);
    }
}
