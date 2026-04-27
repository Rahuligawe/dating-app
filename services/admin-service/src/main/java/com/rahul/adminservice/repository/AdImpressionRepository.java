package com.rahul.adminservice.repository;

import com.rahul.adminservice.entity.AdImpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AdImpressionRepository extends JpaRepository<AdImpression, Long> {

    @Query("SELECT COUNT(a) FROM AdImpression a WHERE a.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(AVG(a.watchTimeSeconds), 0) FROM AdImpression a")
    double avgWatchTime();

    @Query("SELECT COALESCE(SUM(a.watchTimeSeconds), 0) FROM AdImpression a")
    long totalWatchTimeSeconds();

    @Query("SELECT a.region, COUNT(a) FROM AdImpression a GROUP BY a.region ORDER BY COUNT(a) DESC")
    List<Object[]> countByRegion();

    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM AdImpression a " +
           "WHERE a.createdAt >= :since GROUP BY DATE(a.createdAt) ORDER BY DATE(a.createdAt)")
    List<Object[]> dailyCountSince(@Param("since") LocalDateTime since);
}
