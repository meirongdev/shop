package dev.meirong.shop.promotion.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CompensationTaskRepository extends JpaRepository<CompensationTaskEntity, String> {

    @Query("SELECT t FROM CompensationTaskEntity t WHERE t.status = 'PENDING' AND t.nextRetryAt <= :now ORDER BY t.nextRetryAt ASC")
    List<CompensationTaskEntity> findDuePendingTasks(@Param("now") Instant now);
}
