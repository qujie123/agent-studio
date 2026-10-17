package com.agent.agentplatform.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class UsageService {
    private final JdbcTemplate jdbc;
    private final UserService users;
    public UsageService(JdbcTemplate jdbc, UserService users) { this.jdbc = jdbc; this.users = users; }
    public record Entry(String type, Long id, String model, String mode, String status, Long inputTokens, Long outputTokens, Instant createdAt) {}
    public record Summary(int days, long apiCalls, long succeeded, long failed, long simulated, long inputTokens,
                          long outputTokens, long missingUsage, List<Entry> recent) {}
    public Summary mine(String username) {
        long id = users.findByUsername(username).getId();
        String source = "(select kind,coalesce(source_id,id) as id,model,mode,status,input_tokens,output_tokens,recorded_at from usage_records where user_id=?) records";
        Timestamp since = Timestamp.from(Instant.now().minusSeconds(30L * 86400));
        var totals = jdbc.queryForMap("select "
                + "count(case when mode='API' and status in ('RUNNING','SUCCEEDED','FAILED') then 1 end) as api_calls, "
                + "count(case when mode='API' and status='SUCCEEDED' then 1 end) as succeeded, "
                + "count(case when mode='API' and status='FAILED' then 1 end) as failed, "
                + "count(case when mode='DEMO' then 1 end) as simulated, "
                + "coalesce(sum(case when mode='API' then input_tokens end),0) as input_tokens, "
                + "coalesce(sum(case when mode='API' then output_tokens end),0) as output_tokens, "
                + "count(case when mode='API' and status in ('SUCCEEDED','FAILED') and (input_tokens is null or output_tokens is null) then 1 end) as missing_usage "
                + "from " + source + " where recorded_at>=?", id, since);
        var recent = jdbc.query("select * from " + source + " where recorded_at>=? order by recorded_at desc,id desc limit 50",
                (rs, row) -> new Entry(rs.getString("kind"), rs.getLong("id"), rs.getString("model"), rs.getString("mode"), rs.getString("status"),
                        rs.getObject("input_tokens", Long.class), rs.getObject("output_tokens", Long.class), rs.getTimestamp("recorded_at").toInstant()), id, since);
        return new Summary(30, number(totals,"api_calls"), number(totals,"succeeded"), number(totals,"failed"), number(totals,"simulated"),
                number(totals,"input_tokens"), number(totals,"output_tokens"), number(totals,"missing_usage"), recent);
    }
    private long number(Map<String,Object> data, String key) { return ((Number)data.get(key)).longValue(); }
    public Map<String,Long> dashboard(String username) {
        Long id = users.findByUsername(username).getId();
        return Map.of("agents", jdbc.queryForObject("select count(*) from agents where status='ACTIVE'",Long.class),
                "tasks", count("select count(*) from tasks where user_id=?", id),
                "running", count("select count(*) from tasks where user_id=? and status in ('QUEUED','RUNNING')", id),
                "succeeded", count("select count(*) from tasks where user_id=? and status='SUCCEEDED'", id));
    }
    public Map<String,Long> resources(Long id) {
        users.getUserById(id);
        return Map.of("agents", count("select count(*) from agents where creator_id=?", id),
                "tasks", count("select count(*) from tasks where user_id=?", id),
                "skills", count("select count(*) from skills where owner_id=?", id),
                "conversations", count("select count(*) from chat_sessions where owner_id=?", id));
    }
    private long count(String sql, Long id) { return jdbc.queryForObject(sql, Long.class, id); }
}
