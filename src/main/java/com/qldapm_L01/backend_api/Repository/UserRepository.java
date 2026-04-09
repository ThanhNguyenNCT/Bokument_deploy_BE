package com.qldapm_L01.backend_api.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.qldapm_L01.backend_api.Entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") int id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET upload_count = upload_count + 1 WHERE id = :userId", nativeQuery = true)
    int incrementUploadCount(@Param("userId") int userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE users SET download_count = download_count + 1 WHERE id = :userId", nativeQuery = true)
    int incrementDownloadCount(@Param("userId") int userId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = "UPDATE users SET token_version = token_version + 1 WHERE id = :userId", nativeQuery = true)
        int incrementTokenVersion(@Param("userId") int userId);

        @Query(value = """
                        SELECT u.*
                        FROM users u
                        WHERE (CAST(:q AS text) IS NULL OR lower(u.username) LIKE '%' || lower(CAST(:q AS text)) || '%')
                            AND (CAST(:status AS text) IS NULL OR upper(u.status) = upper(CAST(:status AS text)))
                        ORDER BY u.id DESC
                        """,
                        countQuery = """
                        SELECT COUNT(u.id)
                        FROM users u
                        WHERE (CAST(:q AS text) IS NULL OR lower(u.username) LIKE '%' || lower(CAST(:q AS text)) || '%')
                            AND (CAST(:status AS text) IS NULL OR upper(u.status) = upper(CAST(:status AS text)))
                        """,
                        nativeQuery = true)
        Page<User> searchUsers(
                        @Param("q") String q,
                        @Param("status") String status,
                        Pageable pageable
        );

        long countByStatus(String status);

    boolean existsByUsername(String username);
}
