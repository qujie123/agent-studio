import java.sql.*;
import java.nio.file.*;

// Run offline after a cold backup. The password is supplied only through the environment.
class ClearLocalModels {
    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]).toRealPath();
        if (!file.getFileName().toString().equals("agent-local.mv.db")) throw new IllegalArgumentException("Unexpected database");
        String path = file.toString().replace('\\', '/').replaceFirst("\\.mv\\.db$", "");
        try (var db = DriverManager.getConnection("jdbc:h2:file:" + path + ";IFEXISTS=TRUE;MODE=PostgreSQL", "sa", System.getenv("LOCAL_DB_PASSWORD"))) {
            db.setAutoCommit(false);
            try (var sql = db.createStatement()) {
                int count = sql.executeUpdate("UPDATE model_settings SET mode=CASE WHEN id=1 THEN 'DEMO' ELSE 'INHERIT' END, base_url='', encrypted_key=NULL, default_model=CASE WHEN id=1 THEN 'demo-model' ELSE '' END, version=version+1");
                sql.executeUpdate("INSERT INTO model_settings(id,version,mode,base_url,encrypted_key,default_model,timeout_seconds) SELECT 1,0,'DEMO','',NULL,'demo-model',60 WHERE NOT EXISTS (SELECT 1 FROM model_settings WHERE id=1)");
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM model_settings WHERE encrypted_key IS NOT NULL OR base_url<>'' OR mode='API'")) {
                    rows.next(); if (rows.getLong(1) != 0) throw new IllegalStateException("Reset verification failed");
                }
                sql.executeUpdate("INSERT INTO audit_events(actor,action,resource_type,resource_id,created_at) VALUES('local_maintenance','MODEL_CONNECTIONS_CLEARED','MODEL_SETTINGS',NULL,CURRENT_TIMESTAMP)");
                db.commit();
                System.out.println("MODEL_RESET_OK: " + count + " configurations cleared; other business tables unchanged.");
            } catch (Exception failure) { db.rollback(); throw failure; }
        }
    }
}
