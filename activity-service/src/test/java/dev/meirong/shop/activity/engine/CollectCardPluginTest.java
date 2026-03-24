package dev.meirong.shop.activity.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityCollectCardDefinition;
import dev.meirong.shop.activity.domain.ActivityCollectCardDefinitionRepository;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityPlayerCard;
import dev.meirong.shop.activity.domain.ActivityPlayerCardRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CollectCardPluginTest {

    @Test
    void initializeAndParticipate_trackNewCardAndDuplicateDraw() {
        ActivityCollectCardDefinitionRepository definitionRepository = mock(ActivityCollectCardDefinitionRepository.class);
        ActivityPlayerCardRepository playerCardRepository = mock(ActivityPlayerCardRepository.class);
        List<ActivityCollectCardDefinition> storedDefinitions = new ArrayList<>();
        List<ActivityPlayerCard> storedCards = new ArrayList<>();
        wireDefinitionRepository(definitionRepository, storedDefinitions);
        wirePlayerCardRepository(playerCardRepository, storedCards);

        CollectCardPlugin plugin = new CollectCardPlugin(definitionRepository, playerCardRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("game-card-1", GameType.COLLECT_CARD, "Collect Cards");
        game.setConfig("""
                {
                  "cards": [
                    {"id":"card-dragon","name":"Dragon","rarity":"RARE","probability":1.0},
                    {"id":"card-phoenix","name":"Phoenix","rarity":"LEGENDARY","probability":0.0}
                  ]
                }
                """);

        plugin.initialize(game);

        ParticipateContext ctx = new ParticipateContext(game.getId(), GameType.COLLECT_CARD,
                "player-1001", null, game.getConfig(), null, null);
        ParticipateResult first = plugin.participate(ctx);
        ParticipateResult second = plugin.participate(ctx);

        assertThat(storedDefinitions).hasSize(2);
        assertThat(storedCards).hasSize(2);
        assertThat(first.win()).isTrue();
        assertThat(first.prizeType()).isEqualTo(PrizeType.CARD);
        assertThat(first.prizeName()).isEqualTo("Dragon");
        assertThat(first.message()).isEqualTo("Collected new card: Dragon (1/2)");
        assertThat(first.animationHint()).contains("\"duplicate\":false");
        assertThat(first.animationHint()).contains("\"fullSet\":false");
        assertThat(second.win()).isTrue();
        assertThat(second.message()).isEqualTo("Collected duplicate card: Dragon");
        assertThat(second.animationHint()).contains("\"duplicate\":true");
        assertThat(second.animationHint()).contains("\"fullSet\":false");
        assertThat(plugin.extensionTablePrefix()).contains("collect_card");
    }

    @Test
    void participate_withSingleCardCompletesFullSet() {
        ActivityCollectCardDefinitionRepository definitionRepository = mock(ActivityCollectCardDefinitionRepository.class);
        ActivityPlayerCardRepository playerCardRepository = mock(ActivityPlayerCardRepository.class);
        List<ActivityCollectCardDefinition> storedDefinitions = new ArrayList<>();
        List<ActivityPlayerCard> storedCards = new ArrayList<>();
        wireDefinitionRepository(definitionRepository, storedDefinitions);
        wirePlayerCardRepository(playerCardRepository, storedCards);

        CollectCardPlugin plugin = new CollectCardPlugin(definitionRepository, playerCardRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("game-card-2", GameType.COLLECT_CARD, "Single Card");
        game.setConfig("""
                {
                  "cards": [
                    {"name":"Mascot","probability":1.0}
                  ]
                }
                """);

        plugin.initialize(game);

        ParticipateResult result = plugin.participate(new ParticipateContext(
                game.getId(), GameType.COLLECT_CARD, "player-2001", null, game.getConfig(), null, null));

        assertThat(result.win()).isTrue();
        assertThat(result.prizeType()).isEqualTo(PrizeType.CARD);
        assertThat(result.message()).isEqualTo("Full set completed with card: Mascot");
        assertThat(result.animationHint()).contains("\"fullSet\":true");
        assertThat(result.animationHint()).contains("\"uniqueCards\":1");
        assertThat(storedCards).hasSize(1);
    }

    @Test
    void initialize_withInvalidConfig_throwsValidationError() {
        ActivityCollectCardDefinitionRepository definitionRepository = mock(ActivityCollectCardDefinitionRepository.class);
        ActivityPlayerCardRepository playerCardRepository = mock(ActivityPlayerCardRepository.class);
        CollectCardPlugin plugin = new CollectCardPlugin(definitionRepository, playerCardRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("game-card-3", GameType.COLLECT_CARD, "Broken Config");
        game.setConfig("{\"cards\":[]}");

        assertThatThrownBy(() -> plugin.initialize(game))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Collect card config must define cards");
    }

    private void wireDefinitionRepository(ActivityCollectCardDefinitionRepository definitionRepository,
                                          List<ActivityCollectCardDefinition> storedDefinitions) {
        doAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            storedDefinitions.removeIf(definition -> definition.getGameId().equals(gameId));
            return null;
        }).when(definitionRepository).deleteByGameId(anyString());

        when(definitionRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterable<ActivityCollectCardDefinition> iterable = invocation.getArgument(0, Iterable.class);
            storedDefinitions.clear();
            iterable.forEach(storedDefinitions::add);
            return storedDefinitions;
        });

        when(definitionRepository.findByGameIdOrderByCardNameAsc(anyString())).thenAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            return storedDefinitions.stream()
                    .filter(definition -> definition.getGameId().equals(gameId))
                    .sorted(Comparator.comparing(ActivityCollectCardDefinition::getCardName))
                    .toList();
        });
    }

    private void wirePlayerCardRepository(ActivityPlayerCardRepository playerCardRepository,
                                          List<ActivityPlayerCard> storedCards) {
        when(playerCardRepository.save(any(ActivityPlayerCard.class))).thenAnswer(invocation -> {
            ActivityPlayerCard card = invocation.getArgument(0, ActivityPlayerCard.class);
            storedCards.add(card);
            return card;
        });

        when(playerCardRepository.countDistinctCardIdByGameIdAndPlayerId(anyString(), anyString())).thenAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            String buyerId = invocation.getArgument(1, String.class);
            return storedCards.stream()
                    .filter(card -> card.getGameId().equals(gameId) && card.getBuyerId().equals(buyerId))
                    .map(ActivityPlayerCard::getCardId)
                    .distinct()
                    .count();
        });

        when(playerCardRepository.countByGameIdAndPlayerIdAndCardId(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            String buyerId = invocation.getArgument(1, String.class);
            String cardId = invocation.getArgument(2, String.class);
            return storedCards.stream()
                    .filter(card -> card.getGameId().equals(gameId)
                            && card.getBuyerId().equals(buyerId)
                            && card.getCardId().equals(cardId))
                    .count();
        });
    }
}
