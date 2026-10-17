import java.sql.*;
import java.security.*;
import java.nio.charset.*;
import java.util.*;

// Read-only cold verification. Supply DB URLs/password through environment, never arguments.
List<String> columns(Connection database,String table) throws Exception {
    var result=new ArrayList<String>();
    try(var statement=database.prepareStatement("select column_name from information_schema.columns where table_schema='PUBLIC' and table_name=? order by ordinal_position")) {
        statement.setString(1,table);try(var rows=statement.executeQuery()){while(rows.next()){var column=rows.getString(1);if(!table.equals("USERS")||!column.equals("LAST_LOGIN_AT"))result.add(column);}}
    }
    return result;
}
List<String> fingerprints(Connection database,String table,List<String> columns) throws Exception {
    if(!table.matches("[A-Z_]+")||columns.stream().anyMatch(c->!c.matches("[A-Z_]+")))throw new IllegalArgumentException("Unsupported identifier");
    var result=new ArrayList<String>();var quoted=columns.stream().map(c->"\""+c+"\"").toList();
    try(var statement=database.createStatement();var rows=statement.executeQuery("select "+String.join(",",quoted)+" from \""+table+"\"")) {
        while(rows.next()){
            var digest=MessageDigest.getInstance("SHA-256");
            for(int i=1;i<=columns.size();i++){String value=rows.getString(i);if(value==null){digest.update((byte)0);}else{digest.update((byte)1);byte[] bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}}
            result.add(HexFormat.of().formatHex(digest.digest()));
        }
    }
    Collections.sort(result);return result;
}
void verify() throws Exception {
    var before=System.getenv("VERIFY_BACKUP_DB");var after=System.getenv("VERIFY_LIVE_DB");var password=System.getenv("VERIFY_DB_PASSWORD");
    boolean modelReset="true".equals(System.getenv("VERIFY_MODEL_RESET"));
    if(before==null||after==null||password==null||!before.contains("ACCESS_MODE_DATA=r")||!after.contains("ACCESS_MODE_DATA=r"))throw new IllegalArgumentException("Read-only database configuration required");
    try(var oldDatabase=DriverManager.getConnection(before,"sa",password);var newDatabase=DriverManager.getConnection(after,"sa",password)){
        var tables=new ArrayList<String>();
        try(var statement=oldDatabase.createStatement();var rows=statement.executeQuery("select table_name from information_schema.tables where table_schema='PUBLIC' and table_type='BASE TABLE' order by table_name")){while(rows.next()){var name=rows.getString(1);if(!name.equalsIgnoreCase("flyway_schema_history"))tables.add(name);}}
        for(var table:tables){
            if(modelReset&&table.equals("MODEL_SETTINGS")){
                try(var statement=newDatabase.createStatement();var rows=statement.executeQuery("select count(*) from model_settings where encrypted_key is not null or base_url<>'' or mode='API' or (id=1 and (mode<>'DEMO' or default_model<>'demo-model')) or (id<>1 and (mode<>'INHERIT' or default_model<>''))")){rows.next();if(rows.getLong(1)!=0)throw new IllegalStateException("Model reset incomplete");}
                System.out.println("MODEL CONNECTIONS CLEARED: no saved credentials, addresses or real models");continue;
            }
            var names=columns(oldDatabase,table);var oldRows=fingerprints(oldDatabase,table,names);var newRows=fingerprints(newDatabase,table,names);
            if(modelReset&&table.equals("AUDIT_EVENTS")){
                if(newRows.size()!=oldRows.size()+1||!newRows.containsAll(oldRows))throw new IllegalStateException("Unexpected audit changes");
                System.out.println("AUDIT PRESERVED: one maintenance event added");continue;
            }
            if(!oldRows.equals(newRows))throw new IllegalStateException("Business rows changed in "+table);System.out.println("DATA PRESERVED "+table+" records="+oldRows.size());
        }
        try(var statement=newDatabase.createStatement();var rows=statement.executeQuery("select count(*) from \"flyway_schema_history\" where \"version\"='6' and \"success\"=true")){if(!rows.next()||rows.getLong(1)!=1)throw new IllegalStateException("Local V6 migration is not recorded exactly once");}
        System.out.println("LOCAL_DATA_VERIFY_OK: "+(modelReset?"model reset verified; other business rows preserved":"original columns/rows preserved")+"; login timestamp excluded; H2 V6 applied once.");
    }
}
try { verify(); } catch (Throwable failure) { System.out.println("LOCAL_DATA_VERIFY_FAILED: "+failure.getClass().getSimpleName()); }
/exit
