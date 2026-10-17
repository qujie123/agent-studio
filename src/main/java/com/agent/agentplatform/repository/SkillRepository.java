package com.agent.agentplatform.repository;
import com.agent.agentplatform.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SkillRepository extends JpaRepository<Skill, Long> {
    @org.springframework.data.jpa.repository.Query("select s from Skill s where s.owner.username = :username and (lower(s.name) like concat('%',:q,'%') or lower(coalesce(s.description,'')) like concat('%',:q,'%')) order by s.updatedAt desc,s.id desc")
    org.springframework.data.domain.Page<Skill> search(String username, String q, org.springframework.data.domain.Pageable pageable);
    List<Skill> findByOwnerUsernameOrderByUpdatedAtDesc(String username);
    @org.springframework.data.jpa.repository.Query("select s from Skill s where s.owner.username=:username and s.kind='PYTHON' and s.enabled=true and lower(s.name) like concat('%',:q,'%') order by s.updatedAt desc,s.id desc")
    org.springframework.data.domain.Page<Skill> executable(String username,String q,org.springframework.data.domain.Pageable pageable);
}
