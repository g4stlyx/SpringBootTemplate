package  com.g4stly.templateApp.repos;

import  com.g4stly.templateApp.models.VerificationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUserIdAndUserType(Long userId, String userType);
    Optional<VerificationToken> findFirstByUserTypeOrderByCreatedDateDesc(String userType);
    
    // Delete methods
    void deleteByUserIdAndUserType(Long userId, String userType);
    
    // Admin panel queries
    Page<VerificationToken> findAllByOrderByCreatedDateDesc(Pageable pageable);
    Page<VerificationToken> findByUserTypeOrderByCreatedDateDesc(String userType, Pageable pageable);
    Page<VerificationToken> findByExpiryDateBeforeOrderByCreatedDateDesc(LocalDateTime dateTime, Pageable pageable);
}