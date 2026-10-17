package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface KnowledgeRepository extends JpaRepository<KnowledgeDocument,Long> {
    Optional<KnowledgeDocument> findByIdAndOwnerUsername(Long id,String username);
    long countByOwnerUsername(String username);
    @Query("select d from KnowledgeDocument d where d.owner.username=:username and lower(d.title) like concat('%',:q,'%') order by d.updatedAt desc,d.id desc")
    Page<KnowledgeDocument> search(String username,String q,Pageable pageable);
    @Query(value="select count(*)>0 from agent_knowledge where document_id=:id",nativeQuery=true)
    boolean bound(Long id);
}
