package com.agent.agentplatform.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class ProjectDeliveryTests {
    final ObjectMapper mapper = new ObjectMapper();
    final ProjectDelivery delivery = new ProjectDelivery(mapper);
    String project(List<ProjectDelivery.File> files, String entry) { return "```agent-project\n"+mapper.writeValueAsString(Map.of("name","my-project","entrypoint",entry,"files",files))+"\n```"; }
    @Test void zipPreservesAllPathsUtf8AndExactFileContents() throws Exception {
        var files=List.of(new ProjectDelivery.File("index.html","<h1>成品</h1>\n"),new ProjectDelivery.File("src/main.py","print('青松')\r\n"),new ProjectDelivery.File("requirements.txt","pygame==2.6.1\n"));
        var result=delivery.parse(project(files,"index.html"),"SUCCEEDED");assertTrue(result.complete());assertEquals(4,result.files().size());
        var extracted=new HashMap<String,String>();try(var zip=new ZipInputStream(new ByteArrayInputStream(delivery.archive(result)),StandardCharsets.UTF_8)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){extracted.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}}
        for(var file:files)assertEquals(file.content(),extracted.get("my-project/"+file.path()));assertTrue(extracted.containsKey("my-project/AGENT-DELIVERY.md"));
    }
    @Test void oldCodeBlocksGetRealExtensionsAndExplicitNames() {
        var result=delivery.parse("### main.py\n```python\nprint('hi')\n```\n```html filename=web/index.html\n<h1>Hi</h1>\n```","SUCCEEDED");
        assertEquals("main.py",result.files().get(0).path());assertEquals("web/index.html",result.files().get(1).path());assertEquals("print('hi')",result.files().get(0).content());assertFalse(result.warnings().isEmpty());
    }
    @Test void ordinaryTextDoesNotPretendToBeAProject() { var result=delivery.parse("任务已完成，但是只有回答文字","SUCCEEDED");assertFalse(result.complete());assertTrue(result.files().isEmpty());assertThrows(com.agent.agentplatform.exception.ConflictException.class,()->delivery.archive(result)); }
    @Test void incompleteAndFailedResponsesCannotBeExportedAsCompleteProjects() {
        var partial=delivery.parse("```python\nprint('partial')","SUCCEEDED");assertFalse(partial.complete());
        var failed=delivery.parse("```python\nprint('partial')\n```","FAILED");assertFalse(failed.complete());assertThrows(com.agent.agentplatform.exception.ConflictException.class,()->delivery.archive(failed));
    }
    @Test void unsafeWindowsPathsAreRejected() {
        for(String path:List.of("../x.py","/x.py","C:/x.py","a\\x.py","a//x.py","a/./x.py","CON.py","NUL","a/COM1.txt","a/x. ","a\nfile.py","a:file.py","e\u0301.py"))assertThrows(IllegalArgumentException.class,()->ProjectDelivery.validatePath(path),path);
    }
    @Test void duplicateCaseAndFileDirectoryCollisionsAreRejected() {
        for(var files:List.of(List.of(new ProjectDelivery.File("MAIN.py",""),new ProjectDelivery.File("main.py","")),List.of(new ProjectDelivery.File("src",""),new ProjectDelivery.File("src/main.py",""))))assertThrows(IllegalArgumentException.class,()->delivery.parse(project(files,""),"SUCCEEDED"));
    }
    @Test void malformedJsonMissingEntrypointAndOversizedFilesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->delivery.parse("```agent-project\n{broken\n```","SUCCEEDED"));
        assertThrows(IllegalArgumentException.class,()->delivery.parse(project(List.of(new ProjectDelivery.File("main.py","")),"missing.py"),"SUCCEEDED"));
        assertThrows(IllegalArgumentException.class,()->delivery.parse(project(List.of(new ProjectDelivery.File("main.py","x".repeat(1_048_577))),"main.py"),"SUCCEEDED"));
    }
    @Test void htmlPreviewInlinesLocalFilesAndNeverFetchesExternalResources() {
        var files=List.of(new ProjectDelivery.File("web/index.html","<link rel='stylesheet' href='../style.css'><img src='../icon.svg'><script src='../app.js'></script>"),new ProjectDelivery.File("style.css","body{color:red}"),new ProjectDelivery.File("app.js","document.body.dataset.ready='yes'"),new ProjectDelivery.File("icon.svg","<svg xmlns='http://www.w3.org/2000/svg'/>") );
        var preview=new ArtifactPreview(null,null);String html=preview.html(delivery.parse(project(files,"web/index.html"),"SUCCEEDED"),"web/index.html");
        assertTrue(html.contains("body{color:red}"));assertTrue(html.contains("dataset.ready"));assertTrue(html.contains("data:image/svg+xml;base64,"));assertFalse(html.contains("src=\"../"));assertTrue(ArtifactPreview.CSP.contains("sandbox allow-scripts"));assertFalse(ArtifactPreview.CSP.contains("allow-same-origin"));
    }
    @Test void missingFilesAndExternalSourcesProduceActionableErrors() {
        var preview=new ArtifactPreview(null,null);
        for(String ref:List.of("missing.js","https://example.com/app.js","//example.com/app.js","../../outside.js")){var result=delivery.parse(project(List.of(new ProjectDelivery.File("index.html","<script src='"+ref+"'></script>")),"index.html"),"SUCCEEDED");assertThrows(IllegalArgumentException.class,()->preview.html(result,"index.html"));}
    }
    @Test void demoContainsIndependentWebAndPythonDeliverables() { var result=delivery.parse(ProjectDelivery.demo(),"SUCCEEDED");assertTrue(result.complete());assertNotNull(ProjectDelivery.find(result.files(),"main.py"));assertNotNull(ProjectDelivery.find(result.files(),"app.js"));assertTrue(ProjectDelivery.demo().contains("未调用真实大模型")); }
}
