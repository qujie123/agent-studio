package com.agent.agentplatform.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BalanceServiceTests {
    final ObjectMapper mapper=new ObjectMapper();
    final HttpClient client=mock(HttpClient.class);
    final BalanceService service=new BalanceService(mapper,client);
    ModelSettingsService.Connection config(String url){return new ModelSettingsService.Connection("API",url,"test-secret","model",5,1);}
    @Test void deepSeekKeepsCurrenciesAndNegativeBalancesSeparate(){
        var result=service.parse("DeepSeek",mapper.readTree("{\"is_available\":false,\"balance_infos\":[{\"currency\":\"CNY\",\"total_balance\":\"-0.25\",\"granted_balance\":\"0\",\"topped_up_balance\":\"-0.25\"},{\"currency\":\"USD\",\"total_balance\":\"2.5\",\"granted_balance\":\"1\",\"topped_up_balance\":\"1.5\"}]}"),"");
        assertEquals("ACCOUNT_BALANCE",result.kind());assertEquals(2,result.balances().size());assertEquals(new BigDecimal("-0.25"),result.balances().getFirst().total());assertFalse(result.available());
    }
    @Test void openRouterUnlimitedIsNotInfiniteWalletAndNumericValuesParse(){
        var unlimited=service.parse("OpenRouter",mapper.readTree("{\"data\":{\"usage\":2.3,\"limit\":null,\"limit_remaining\":null}}"),"");
        assertEquals("KEY_LIMIT",unlimited.kind());assertNull(unlimited.remaining());assertEquals(new BigDecimal("2.3"),unlimited.used());
        var limited=service.parse("OpenRouter",mapper.readTree("{\"data\":{\"usage\":2,\"limit\":10,\"limit_remaining\":8}}"),"");
        assertEquals(new BigDecimal("8"),limited.remaining());
        assertThrows(IllegalArgumentException.class,()->service.parse("DeepSeek",mapper.readTree("{\"is_available\":true,\"balance_infos\":[]}"),""));
    }
    @Test void unknownHostsAndNonstandardPortsNeverReceiveBalanceKeys(){
        for(String url:new String[]{"https://proxy.example/v1","https://api.deepseek.com.evil.example/v1","https://api.deepseek.com:8443/v1","https://api.openai.com/v1","https://openrouter.ai/custom"}){
            assertEquals("UNSUPPORTED",service.query(config(url)).status());
        }verifyNoInteractions(client);
    }
    @SuppressWarnings("unchecked")
    @Test void fixedEndpointAndErrorsDoNotExposeProviderBodyOrKey() throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(401);when(response.body()).thenReturn("test-secret private upstream error");
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var result=service.query(config("https://api.deepseek.com/v1"));assertEquals("ERROR",result.status());assertFalse(result.toString().contains("test-secret"));
        verify(client).send(argThat((HttpRequest r)->r.uri().toString().equals("https://api.deepseek.com/user/balance")&&r.headers().firstValue("Authorization").orElse("").equals("Bearer test-secret")),any(HttpResponse.BodyHandler.class));
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{}");
        assertEquals("ERROR",service.query(config("https://api.deepseek.com/v1")).status());
    }
}
