package dev.meirong.shop.activity.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.meirong.shop.activity.domain.ActivityCollectCardDefinition;
import dev.meirong.shop.activity.domain.ActivityCollectCardDefinitionRepository;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityPlayerCard;
import dev.meirong.shop.activity.domain.ActivityPlayerCardRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CollectCardPlugin implements GamePlugin {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_RARITY = "COMMON";
    private static final String DROP_SOURCE = "DROP";

    private final ActivityCollectCardDefinitionRepository definitionRepository;
    private final ActivityPlayerCardRepository playerCardRepository;
    private final ObjectMapper objectMapper;

    public CollectCardPlugin(ActivityCollectCardDefinitionRepository definitionRepository,
                             ActivityPlayerCardRepository playerCardRepository,
                             ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.playerCardRepository = playerCardRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public GameType supportedType() {
        return GameType.COLLECT_CARD;
    }

    @Override
    public void initialize(ActivityGame game) {
        List<ActivityCollectCardDefinition> definitions = parseDefinitions(game);
        definitionRepository.deleteByGameId(game.getId());
        definitionRepository.saveAll(definitions);
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        if (ctx.playerId() == null || ctx.playerId().isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Collect card requires a playerId");
        }

        List<ActivityCollectCardDefinition> definitions = definitionRepository.findByGameIdOrderByCardNameAsc(ctx.gameId());
        if (definitions.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Collect card game has no cards configured");
        }

        ActivityCollectCardDefinition drawn = weightedRandom(definitions);
        long uniqueBefore = playerCardRepository.countDistinctCardIdByGameIdAndPlayerId(ctx.gameId(), ctx.playerId());
        boolean duplicate = playerCardRepository.countByGameIdAndPlayerIdAndCardId(
                ctx.gameId(), ctx.playerId(), drawn.getId()) > 0;

        playerCardRepository.save(new ActivityPlayerCard(
                UUID.randomUUID().toString(), ctx.gameId(), ctx.playerId(), drawn.getId(), DROP_SOURCE));

        long uniqueAfter = duplicate ? uniqueBefore : uniqueBefore + 1;
        boolean fullSet = uniqueAfter >= definitions.size();
        boolean justCompleted = !duplicate && uniqueBefore < definitions.size() && fullSet;
        String animationHint = buildAnimationHint(drawn, duplicate, fullSet, uniqueAfter, definitions.size());
        String message = buildMessage(drawn, duplicate, fullSet, justCompleted, uniqueAfter, definitions.size());

        return new ParticipateResult(true, drawn.getId(), drawn.getCardName(), PrizeType.CARD, null, animationHint, message);
    }

    @Override
    public Optional<String> extensionTablePrefix() {
        return Optional.of("collect_card");
    }

    private List<ActivityCollectCardDefinition> parseDefinitions(ActivityGame game) {
        if (game.getConfig() == null || game.getConfig().isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Collect card config must define cards");
        }
        try {
            JsonNode root = objectMapper.readTree(game.getConfig());
            JsonNode cards = root.path("cards");
            if (!cards.isArray() || cards.isEmpty()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Collect card config must define cards");
            }

            List<ActivityCollectCardDefinition> definitions = new ArrayList<>();
            for (JsonNode cardNode : cards) {
                String cardName = text(cardNode, "name", "card_name");
                String rarity = optionalText(cardNode, "rarity", "card_rarity");
                if (rarity == null || rarity.isBlank()) {
                    rarity = DEFAULT_RARITY;
                } else {
                    rarity = rarity.trim().toUpperCase();
                }
                BigDecimal probability = decimal(cardNode, "probability");
                if (probability.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                            "Collect card probability must be greater than or equal to zero");
                }

                String configuredId = optionalText(cardNode, "id", "card_id");
                String cardId = configuredId == null || configuredId.isBlank()
                        ? defaultCardId(game.getId(), cardName)
                        : configuredId.trim();
                definitions.add(new ActivityCollectCardDefinition(cardId, game.getId(), cardName, rarity, probability));
            }

            long distinctIds = definitions.stream().map(ActivityCollectCardDefinition::getId).distinct().count();
            if (distinctIds != definitions.size()) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "Collect card config must define unique card ids/names");
            }
            BigDecimal totalProbability = definitions.stream()
                    .map(ActivityCollectCardDefinition::getProbability)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalProbability.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "Collect card probabilities must sum to a positive value");
            }
            return definitions;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid collect card config", exception);
        }
    }

    private ActivityCollectCardDefinition weightedRandom(List<ActivityCollectCardDefinition> definitions) {
        BigDecimal total = definitions.stream()
                .map(ActivityCollectCardDefinition::getProbability)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double roll = RANDOM.nextDouble(total.doubleValue());
        double cumulative = 0.0d;
        for (ActivityCollectCardDefinition definition : definitions) {
            cumulative += definition.getProbability().doubleValue();
            if (roll < cumulative) {
                return definition;
            }
        }
        return definitions.getLast();
    }

    private String buildAnimationHint(ActivityCollectCardDefinition drawn, boolean duplicate,
                                      boolean fullSet, long uniqueCards, int totalCards) {
        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("cardId", drawn.getId());
        hint.put("cardName", drawn.getCardName());
        hint.put("rarity", drawn.getRarity());
        hint.put("duplicate", duplicate);
        hint.put("fullSet", fullSet);
        hint.put("uniqueCards", uniqueCards);
        hint.put("totalCards", totalCards);
        hint.put("drawnAt", Instant.now().toString());
        return hint.toString();
    }

    private String buildMessage(ActivityCollectCardDefinition drawn, boolean duplicate,
                                boolean fullSet, boolean justCompleted, long uniqueCards, int totalCards) {
        if (justCompleted) {
            return "Full set completed with card: %s".formatted(drawn.getCardName());
        }
        if (duplicate) {
            return "Collected duplicate card: %s".formatted(drawn.getCardName());
        }
        if (fullSet) {
            return "Full set already completed. You drew: %s".formatted(drawn.getCardName());
        }
        return "Collected new card: %s (%d/%d)".formatted(drawn.getCardName(), uniqueCards, totalCards);
    }

    private String text(JsonNode node, String primaryField, String fallbackField) {
        String value = optionalText(node, primaryField, fallbackField);
        if (value == null || value.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Collect card config field '%s' must be provided".formatted(primaryField));
        }
        return value.trim();
    }

    private String optionalText(JsonNode node, String primaryField, String fallbackField) {
        JsonNode primary = node.path(primaryField);
        if (!primary.isMissingNode() && !primary.isNull()) {
            return primary.asText();
        }
        JsonNode fallback = node.path(fallbackField);
        if (!fallback.isMissingNode() && !fallback.isNull()) {
            return fallback.asText();
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Collect card config field '%s' must be provided".formatted(fieldName));
        }
        return value.decimalValue();
    }

    private String defaultCardId(String gameId, String cardName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((gameId + ":" + cardName).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
