package com.rahul.authservice.repository;

import com.rahul.authservice.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findByMobileAndCodeAndUsedFalse(
            String mobile, String code);

    @Modifying
    @Query("UPDATE OtpCode o SET o.used = true WHERE o.mobile = :mobile")
    void invalidateAllForMobile(@Param("mobile") String mobile);
}