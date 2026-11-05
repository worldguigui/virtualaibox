package com.own.virtualaibox;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
/** 验证应用上下文能够正常启动。 */
class VirtualAiBoxApplicationTests {

    /** 加载应用上下文：Bean 装配失败（如配置缺失、模板加载失败）时本测试失败。 */
    @Test
    void contextLoads() {
    }

}
