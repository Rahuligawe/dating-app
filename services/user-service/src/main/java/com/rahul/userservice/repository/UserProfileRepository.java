package com.rahul.userservice.repository;

import com.rahul.userservice.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, String> {

    /*@Query("""
                SELECT u FROM UserProfile u
                WHERE u.id != :userId
                  AND u.isActive = true
                  AND u.isProfileComplete = true
                  AND (
                      :pref = 'BOTH'
                      OR (u.gender = 'MALE'   AND :pref = 'MALE')
                      OR (u.gender = 'FEMALE' AND :pref = 'FEMALE')
                  )
                  AND u.age >= :minAge
                  AND u.age <= :maxAge
                ORDER BY u.isVerified DESC, u.createdAt DESC
            """)
    Page<UserProfile> findDiscoverProfiles(
            @Param("userId") String userId,
            @Param("pref") String genderPreference,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            Pageable pageable
    );*/

    @Query("""
                SELECT u FROM UserProfile u
                WHERE u.id != :userId
                  AND u.isActive = true
                  AND (
                      :pref = 'BOTH'
                      OR u.gender = :pref
                  )
                  AND u.age BETWEEN :minAge AND :maxAge
                ORDER BY u.isVerified DESC, u.createdAt DESC
            """)
    Page<UserProfile> findDiscoverProfiles(
            @Param("userId") String userId,
            @Param("pref") UserProfile.Gender genderPreference,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            Pageable pageable
    );
}