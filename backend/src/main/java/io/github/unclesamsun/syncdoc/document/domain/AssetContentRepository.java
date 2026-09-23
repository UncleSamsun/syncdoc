package io.github.unclesamsun.syncdoc.document.domain;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetContentRepository extends JpaRepository<AssetContentEntity, String> {

    /**
     * 가리키는 첨부가 하나도 남지 않은 내용을 지운다.
     *
     * <p>내용 해시가 열쇠라 여러 게시본이 같은 행을 가리킨다. 프로젝트 하나를 끊는다고 함께
     * 지우면 다른 프로젝트의 그림이 깨진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AssetContentEntity content where not exists "
            + "(select 1 from AssetEntity asset where asset.storageKey = content.storageKey)")
    @Transactional
    void deleteOrphans();
}
