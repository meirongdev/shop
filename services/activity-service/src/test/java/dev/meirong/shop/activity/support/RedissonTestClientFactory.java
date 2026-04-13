package dev.meirong.shop.activity.support;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public final class RedissonTestClientFactory {

    private RedissonTestClientFactory() {
    }

    public static RedissonClient create(String host, int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setTimeout(10_000)
                .setRetryAttempts(5)
                .setRetryInterval(500)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(4);
        return Redisson.create(config);
    }
}
