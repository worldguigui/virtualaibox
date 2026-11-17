package com.own.virtualaibox.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4 兼容：langchain4j-open-ai starter 的 HttpClientBuilder 自动配置
 * （openAiChatModelHttpClientBuilder）方法体引用了 Boot 3.5 的ClientHttpRequestFactoryBuilder，
 * 该类在 Boot 4.x 模块化中已移除，直接启动会 NoClassDefFoundError。
 *
 * 此处按 starter 同名 Bean（openAiChatModelHttpClientBuilder）显式声明 JDK HttpClient实现
 * 触发其 @ConditionalOnMissingBean(name=...) 回退，使不兼容的工厂方法不再执行
 * 待 langchain4j 官方发布 Boot 4 适配版 starter 后，可连同 pom 中的spring-restclient 排除项一并移除。
 */
@Configuration
public class LlmHttpClientConfig {

    // Bean 名必须与 starter 内工厂方法一致，@ConditionalOnMissingBean 按名匹配。
    @Bean
    public HttpClientBuilder openAiChatModelHttpClientBuilder() {
        return JdkHttpClient.builder();
    }
}
