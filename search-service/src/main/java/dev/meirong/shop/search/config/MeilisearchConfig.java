package dev.meirong.shop.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class MeilisearchConfig {

    @Bean("meilisearchAdminClient")
    public Client meilisearchAdminClient(SearchProperties props) {
        return new Client(new Config(props.meilisearch().url(), props.meilisearch().adminKey()));
    }

    @Bean("meilisearchSearchClient")
    public Client meilisearchSearchClient(SearchProperties props) {
        return new Client(new Config(props.meilisearch().url(), props.meilisearch().searchKey()));
    }
}
