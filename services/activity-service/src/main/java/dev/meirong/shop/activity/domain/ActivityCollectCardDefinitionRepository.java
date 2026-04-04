package dev.meirong.shop.activity.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCollectCardDefinitionRepository extends JpaRepository<ActivityCollectCardDefinition, String> {

    List<ActivityCollectCardDefinition> findByGameIdOrderByCardNameAsc(String gameId);

    void deleteByGameId(String gameId);
}
