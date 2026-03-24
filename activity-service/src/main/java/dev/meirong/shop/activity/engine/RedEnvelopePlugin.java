package dev.meirong.shop.activity.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityParticipationRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedEnvelopePlugin implements GamePlugin {

    private static final Logger log = LoggerFactory.getLogger(RedEnvelopePlugin.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_PACKET_CENTS = 1;
    private static final RedisScript<List> GRAB_PACKET_SCRIPT = createGrabPacketScript();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ActivityParticipationRepository participationRepository;

    public RedEnvelopePlugin(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             ActivityParticipationRepository participationRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.participationRepository = participationRepository;
    }

    @Override
    public GameType supportedType() {
        return GameType.RED_ENVELOPE;
    }

    @Override
    public void initialize(ActivityGame game) {
        EnvelopeConfig config = parseConfig(game.getConfig());
        List<String> packetValues = generatePackets(config.totalAmount(), config.packetCount()).stream()
                .map(amount -> amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString())
                .toList();
        redisTemplate.delete(List.of(packetsKey(game.getId()), claimsKey(game.getId())));
        if (!packetValues.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(packetsKey(game.getId()), packetValues);
        }
        log.info("Initialized red envelope game {} with {} packets totaling {}",
                game.getId(), config.packetCount(), config.totalAmount());
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<Object> result = redisTemplate.execute(
                GRAB_PACKET_SCRIPT,
                List.of(packetsKey(ctx.gameId()), claimsKey(ctx.gameId())),
                ctx.playerId());
        if (result == null || result.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Red envelope claim returned empty result");
        }
        int code = toInt(result.get(0));
        return switch (code) {
            case -1 -> ParticipateResult.miss("You have already claimed this red envelope");
            case -2 -> ParticipateResult.miss("All red envelopes have been claimed");
            case 1 -> {
                if (result.size() < 2) {
                    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                            "Red envelope claim succeeded without amount");
                }
                BigDecimal amount = toBigDecimal(result.get(1));
                String animationHint = "{\"amount\":\"%s\"}".formatted(amount.toPlainString());
                yield new ParticipateResult(true, null, "Red Envelope", PrizeType.POINTS, amount,
                        animationHint, "You claimed %s points".formatted(amount.toPlainString()));
            }
            default -> throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected red envelope result code: " + code);
        };
    }

    @Override
    public void settle(ActivityGame game) {
        long redisClaimed = redisTemplate.opsForHash().size(claimsKey(game.getId()));
        long persistedWins = participationRepository.countWinningParticipationsByGameId(game.getId());
        if (redisClaimed != persistedWins) {
            log.warn("Red envelope reconcile mismatch: game={}, redisClaimed={}, persistedWins={}",
                    game.getId(), redisClaimed, persistedWins);
        }
        redisTemplate.delete(List.of(packetsKey(game.getId()), claimsKey(game.getId())));
    }

    private EnvelopeConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Red envelope config must define packet_count and total_amount");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            int packetCount = positiveInt(config, "packet_count", "packetCount");
            BigDecimal totalAmount = positiveAmount(config, "total_amount", "totalAmount");
            return new EnvelopeConfig(packetCount, totalAmount);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid red envelope config", exception);
        }
    }

    private int positiveInt(JsonNode config, String primaryField, String fallbackField) {
        JsonNode node = config.path(primaryField);
        if (node.isMissingNode()) {
            node = config.path(fallbackField);
        }
        int value = node.asInt(0);
        if (value <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal positiveAmount(JsonNode config, String primaryField, String fallbackField) {
        JsonNode node = config.path(primaryField);
        if (node.isMissingNode()) {
            node = config.path(fallbackField);
        }
        if (node.isMissingNode() || node.isNull()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be provided");
        }
        BigDecimal value = node.decimalValue();
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must use at most 2 decimal places", exception);
        }
    }

    private List<BigDecimal> generatePackets(BigDecimal totalAmount, int packetCount) {
        int remainingCents = totalAmount.movePointRight(2).intValueExact();
        if (remainingCents < packetCount * MIN_PACKET_CENTS) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "total_amount must be at least 0.01 per packet");
        }
        List<BigDecimal> packets = new ArrayList<>(packetCount);
        for (int remainingPackets = packetCount; remainingPackets > 1; remainingPackets--) {
            int maxByAverage = Math.max(MIN_PACKET_CENTS, (remainingCents * 2) / remainingPackets);
            int maxAllowed = remainingCents - (remainingPackets - 1) * MIN_PACKET_CENTS;
            int upperBound = Math.max(MIN_PACKET_CENTS, Math.min(maxByAverage, maxAllowed));
            int packetCents = upperBound == MIN_PACKET_CENTS
                    ? MIN_PACKET_CENTS
                    : MIN_PACKET_CENTS + RANDOM.nextInt(upperBound - MIN_PACKET_CENTS + 1);
            packets.add(BigDecimal.valueOf(packetCents, 2));
            remainingCents -= packetCents;
        }
        packets.add(BigDecimal.valueOf(remainingCents, 2));
        Collections.shuffle(packets, RANDOM);
        return packets;
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(asString(value));
    }

    private static BigDecimal toBigDecimal(Object value) {
        return new BigDecimal(asString(value));
    }

    private static String asString(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static String packetsKey(String gameId) {
        return "re:packets:" + gameId;
    }

    private static String claimsKey(String gameId) {
        return "re:claims:" + gameId;
    }

    private static RedisScript<List> createGrabPacketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setResultType(List.class);
        script.setScriptText("""
                if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
                  return {-1, redis.call('HGET', KEYS[2], ARGV[1])}
                end
                local amount = redis.call('LPOP', KEYS[1])
                if not amount then
                  return {-2}
                end
                redis.call('HSET', KEYS[2], ARGV[1], amount)
                return {1, amount}
                """);
        return script;
    }

    private record EnvelopeConfig(int packetCount, BigDecimal totalAmount) {}
}
