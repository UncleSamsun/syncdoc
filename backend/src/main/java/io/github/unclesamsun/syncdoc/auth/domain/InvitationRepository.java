package io.github.unclesamsun.syncdoc.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    Optional<InvitationEntity> findByGithubUserId(String githubUserId);

    Page<InvitationEntity> findAllByOrderByGrantedAtDesc(Pageable pageable);
}
