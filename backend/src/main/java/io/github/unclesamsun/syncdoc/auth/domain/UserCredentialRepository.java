package io.github.unclesamsun.syncdoc.auth.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredentialEntity, UUID> {

    void deleteByUserId(UUID userId);
}
