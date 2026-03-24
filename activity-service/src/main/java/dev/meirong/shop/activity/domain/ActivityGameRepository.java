package dev.meirong.shop.activity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ActivityGameRepository extends JpaRepository<ActivityGame, String> {

    List<ActivityGame> findByStatus(GameStatus status);

    @Query("SELECT g FROM ActivityGame g WHERE g.status = 'ACTIVE' AND (g.startAt IS NULL OR g.startAt <= :now) AND (g.endAt IS NULL OR g.endAt >= :now)")
    List<ActivityGame> findActiveGames(Instant now);

    @Query("SELECT g FROM ActivityGame g WHERE g.status = 'ACTIVE' AND g.endAt IS NOT NULL AND g.endAt < :now")
    List<ActivityGame> findExpiredActiveGames(Instant now);

    @Query("SELECT g FROM ActivityGame g WHERE g.status = 'SCHEDULED' AND g.startAt IS NOT NULL AND g.startAt <= :now")
    List<ActivityGame> findScheduledGamesReadyToActivate(Instant now);
}
