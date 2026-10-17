package com.agent.agentplatform.service;
import com.agent.agentplatform.dto.*;
import com.agent.agentplatform.entity.*;
import com.agent.agentplatform.repository.*;
import com.agent.agentplatform.exception.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class SkillService {
    private final SkillRepository skills;
    private final AgentRepository agents;
    private final UserService users;
    private final AuditService audit;
    public SkillService(SkillRepository skills, AgentRepository agents, UserService users, AuditService audit) {
        this.skills = skills; this.agents = agents; this.users = users; this.audit = audit;
    }
    public List<SkillResponse> mine(String username) {
        return skills.findByOwnerUsernameOrderByUpdatedAtDesc(username).stream().map(SkillResponse::from).toList();
    }
    public PageResult<SkillResponse> page(String username, String q, int page, int size) {
        return PageResult.from(skills.search(username, PageResult.query(q), PageResult.request(page,size)), SkillResponse::from);
    }
    public PageResult<SkillResponse> availablePage(Long agentId, Authentication auth, String q, int page, int size) {
        Agent agent = agent(agentId); requireOwner(agent.getOwner(), auth);
        return page(agent.getOwner().getUsername(), q, page, size);
    }
    public record BindingView(Long version, List<SkillResponse> skills) {}
    @Transactional public BindingView bindingSnapshot(Long agentId, Authentication auth) {
        Agent agent = agents.findForUpdate(agentId).orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        requireOwner(agent.getOwner(), auth);
        return new BindingView(agent.getVersion(), agent.getSkills().stream().map(SkillResponse::from).toList());
    }
    @Transactional public SkillResponse create(SkillRequest request, String username) {
        Skill skill = new Skill(); skill.setOwner(users.findByUsername(username)); apply(skill, request);
        skills.saveAndFlush(skill); audit.record(username, "SKILL_CREATED", "SKILL", skill.getId());
        return SkillResponse.from(skill);
    }
    @Transactional public SkillResponse update(Long id, SkillRequest request, Authentication auth) {
        Skill skill = find(id); requireOwner(skill.getOwner(), auth);
        if (!Objects.equals(request.version(), skill.getVersion())) {
            throw new ConflictException("Skill changed; refresh and try again");
        }
        apply(skill, request); skills.flush(); audit.record(auth.getName(), "SKILL_UPDATED", "SKILL", id);
        return SkillResponse.from(skill);
    }
    @Transactional public void delete(Long id, Authentication auth) {
        Skill skill = find(id); requireOwner(skill.getOwner(), auth);
        if (agents.existsBySkillsId(id)) { throw new ConflictException("Skill is bound to agents; unbind it or disable it first"); }
        skills.delete(skill); audit.record(auth.getName(), "SKILL_DELETED", "SKILL", id);
    }
    public List<SkillResponse> bindings(Long agentId, Authentication auth) {
        Agent agent = agent(agentId); requireOwner(agent.getOwner(), auth);
        return agent.getSkills().stream().map(SkillResponse::from).toList();
    }
    public List<SkillResponse> available(Long agentId, Authentication auth) {
        Agent agent = agent(agentId); requireOwner(agent.getOwner(), auth);
        return mine(agent.getOwner().getUsername());
    }
    @Transactional public List<SkillResponse> bind(Long agentId, SkillBindingRequest request, Authentication auth) {
        Agent agent = agents.findForUpdate(agentId).orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        requireOwner(agent.getOwner(), auth);
        if (new HashSet<>(request.skillIds()).size() != request.skillIds().size()) {
            throw new IllegalArgumentException("Duplicate skill IDs are not allowed");
        }
        if (!Objects.equals(request.version(), agent.getVersion())) {
            throw new ConflictException("Agent changed; refresh and try again");
        }
        List<Skill> selected = request.skillIds().stream().map(this::find).toList();
        for (Skill skill : selected) {
            if (!skill.getOwner().getId().equals(agent.getOwner().getId())) {
                throw new ForbiddenException("Agents can only use skills belonging to their creator");
            }
        }
        agent.getSkills().clear(); agent.getSkills().addAll(selected); agents.flush();
        audit.record(auth.getName(), "AGENT_SKILLS_UPDATED", "AGENT", agentId);
        return selected.stream().map(SkillResponse::from).toList();
    }
    private Skill find(Long id) { return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill not found")); }
    private Agent agent(Long id) { return agents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Agent not found")); }
    private void requireOwner(User owner, Authentication auth) {
        if (!owner.getUsername().equals(auth.getName()) && auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) { throw new ForbiddenException("You cannot manage these skills"); }
    }
    private void apply(Skill skill, SkillRequest request) {
        if(request.kind()!=null)skill.setKind(request.kind());
        if("PYTHON".equals(skill.getKind())){if(request.code()!=null)skill.setCode(request.code());if(skill.getCode()==null||skill.getCode().isBlank())throw new IllegalArgumentException("Python Skill 需要代码");}
        else skill.setCode(null);
        skill.setName(request.name().strip()); skill.setDescription(request.description() == null ? "" : request.description().strip());
        skill.setInstructions(request.instructions().strip()); skill.setEnabled(request.enabled());
    }
}
