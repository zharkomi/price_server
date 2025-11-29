package com.price.service;

import com.price.common.Configuration;
import com.price.event.CandleEvent;
import com.price.market.Instrument;
import com.price.market.source.Source;
import com.price.storage.Repository;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {

    @Mock
    private Repository repository;

    private Configuration configuration;
    private HistoryService historyService;
    private HttpClient httpClient;
    private AutoCloseable mocks;
    private int testPort;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Use a random available port
        testPort = findAvailablePort();

        // Create a minimal Configuration using reflection
        configuration = createTestConfiguration(testPort);

        historyService = new HistoryService(repository, configuration);
        historyService.start();

        // Give the server a moment to start
        Thread.sleep(100);

        // Create HTTP client for testing
        httpClient = new HttpClient();
        httpClient.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (httpClient != null) {
            httpClient.stop();
        }
        if (historyService != null) {
            historyService.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    private Configuration createTestConfiguration(int port) throws Exception {
        // Create a bare instance without calling the constructor
        // using sun.misc.Unsafe (works on most JVMs)
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Configuration config = (Configuration) unsafe.allocateInstance(Configuration.class);

        // Set final fields using reflection
        setFinalField(config, "httpPort", port);
        setFinalField(config, "instruments", List.of(new Instrument("BTCUSDT", Source.BINANCE, new int[]{60000})));
        setFinalField(config, "sources", List.of(Source.BINANCE));
        setFinalField(config, "repositoryType", "CLICKHOUSE");
        setFinalField(config, "clickhouseUrl", "http://localhost:8123");
        setFinalField(config, "clickhouseUser", "default");
        setFinalField(config, "clickhousePassword", "");

        return config;
    }

    private void setFinalField(Object target, String fieldName, Object value) throws Exception {
        Field field = Configuration.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        // In Java 12+, we can't remove final modifier, but setAccessible allows us to set it anyway
        field.set(target, value);
    }

    private int findAvailablePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void testHealthEndpoint() throws Exception {
        ContentResponse response = httpClient.GET("http://localhost:" + testPort + "/health");

        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getMediaType());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("ok", json.getString("status"));
        assertEquals("HistoryService", json.getString("service"));
        assertTrue(json.has("timestamp"));
    }

    @Test
    void testHistoryEndpointSuccess() throws Exception {
        // Prepare mock data
        List<CandleEvent> mockCandles = new ArrayList<>();
        CandleEvent candle1 = new CandleEvent();
        candle1.instrument("BTCUSDT");
        candle1.timeframeMs(60000);
        candle1.time(1609459200000L); // 2021-01-01 00:00:00
        candle1.open(29000.0);
        candle1.high(29500.0);
        candle1.low(28800.0);
        candle1.close(29200.0);
        candle1.volume(1000000L);

        CandleEvent candle2 = new CandleEvent();
        candle2.instrument("BTCUSDT");
        candle2.timeframeMs(60000);
        candle2.time(1609459260000L); // 2021-01-01 00:01:00
        candle2.open(29200.0);
        candle2.high(29400.0);
        candle2.low(29100.0);
        candle2.close(29300.0);
        candle2.volume(950000L);

        mockCandles.add(candle1);
        mockCandles.add(candle2);

        when(repository.queryCandles(anyString(), anyInt(), anyLong(), anyLong()))
            .thenReturn(mockCandles);

        // Make request
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getMediaType());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("ok", json.getString("s"));

        assertEquals(2, json.getJSONArray("t").length());
        assertEquals(1609459200L, json.getJSONArray("t").getLong(0)); // Converted from ms to seconds
        assertEquals(1609459260L, json.getJSONArray("t").getLong(1));

        assertEquals(29000.0, json.getJSONArray("o").getDouble(0));
        assertEquals(29200.0, json.getJSONArray("o").getDouble(1));

        assertEquals(29500.0, json.getJSONArray("h").getDouble(0));
        assertEquals(29400.0, json.getJSONArray("h").getDouble(1));

        assertEquals(28800.0, json.getJSONArray("l").getDouble(0));
        assertEquals(29100.0, json.getJSONArray("l").getDouble(1));

        assertEquals(29200.0, json.getJSONArray("c").getDouble(0));
        assertEquals(29300.0, json.getJSONArray("c").getDouble(1));

        assertEquals(1000000L, json.getJSONArray("v").getLong(0));
        assertEquals(950000L, json.getJSONArray("v").getLong(1));

        // Verify repository was called with correct parameters (in milliseconds)
        verify(repository).queryCandles("BTCUSDT", 60000, 1609459200000L, 1609459300000L);
    }

    @Test
    void testHistoryEndpointEmptyResult() throws Exception {
        when(repository.queryCandles(anyString(), anyInt(), anyLong(), anyLong()))
            .thenReturn(new ArrayList<>());

        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(200, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("ok", json.getString("s"));
        assertEquals(0, json.getJSONArray("t").length());
    }

    @Test
    void testHistoryEndpointMissingSymbol() throws Exception {
        String url = "http://localhost:" + testPort + "/history?interval=1m&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Missing required parameters"));
    }

    @Test
    void testHistoryEndpointMissingInterval() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Missing required parameters"));
    }

    @Test
    void testHistoryEndpointMissingFrom() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Missing required parameters"));
    }

    @Test
    void testHistoryEndpointMissingTo() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459200";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Missing required parameters"));
    }

    @Test
    void testHistoryEndpointEmptySymbol() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=&interval=1m&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Symbol cannot be empty"));
    }

    @Test
    void testHistoryEndpointEmptyInterval() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Interval cannot be empty"));
    }

    @Test
    void testHistoryEndpointInvalidTimestampFormat() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=invalid&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Invalid timestamp format"));
    }

    @Test
    void testHistoryEndpointNegativeTimestamp() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=-1&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("From timestamp must be positive"));
    }

    @Test
    void testHistoryEndpointZeroTimestamp() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=0&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("From timestamp must be positive"));
    }

    @Test
    void testHistoryEndpointFromAfterTo() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459300&to=1609459200";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("From timestamp must be before to timestamp"));
    }

    @Test
    void testHistoryEndpointFromEqualsTo() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459200&to=1609459200";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("From timestamp must be before to timestamp"));
    }

    @Test
    void testHistoryEndpointInvalidInterval() throws Exception {
        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=invalid&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(400, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Invalid interval format"));
    }

    @Test
    void testHistoryEndpointVariousIntervals() throws Exception {
        when(repository.queryCandles(anyString(), anyInt(), anyLong(), anyLong()))
            .thenReturn(new ArrayList<>());

        // Test various valid interval formats
        String[] intervals = {"1s", "5s", "1m", "5m", "15m", "30m", "1h", "4h", "1d"};
        int[] expectedMs = {1000, 5000, 60000, 300000, 900000, 1800000, 3600000, 14400000, 86400000};

        for (int i = 0; i < intervals.length; i++) {
            String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=" + intervals[i] + "&from=1609459200&to=1609459300";
            ContentResponse response = httpClient.GET(url);

            assertEquals(200, response.getStatus(), "Failed for interval: " + intervals[i]);

            // Verify the timeframe was correctly parsed and passed to repository
            verify(repository).queryCandles("BTCUSDT", expectedMs[i], 1609459200000L, 1609459300000L);
        }
    }

    @Test
    void testHistoryEndpointRepositoryException() throws Exception {
        when(repository.queryCandles(anyString(), anyInt(), anyLong(), anyLong()))
            .thenThrow(new RuntimeException("Database connection failed"));

        String url = "http://localhost:" + testPort + "/history?symbol=BTCUSDT&interval=1m&from=1609459200&to=1609459300";
        ContentResponse response = httpClient.GET(url);

        assertEquals(500, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Internal server error"));
    }

    @Test
    void testNotFoundEndpoint() throws Exception {
        ContentResponse response = httpClient.GET("http://localhost:" + testPort + "/unknown");

        assertEquals(404, response.getStatus());

        JSONObject json = new JSONObject(response.getContentAsString());
        assertEquals("error", json.getString("s"));
        assertTrue(json.getString("errmsg").contains("Not found"));
    }

    @Test
    void testHistoryEndpointDifferentSymbols() throws Exception {
        when(repository.queryCandles(anyString(), anyInt(), anyLong(), anyLong()))
            .thenReturn(new ArrayList<>());

        String[] symbols = {"BTCUSDT", "ETHUSDT", "BNBUSDT", "ADAUSDT"};

        for (String symbol : symbols) {
            String url = "http://localhost:" + testPort + "/history?symbol=" + symbol + "&interval=1m&from=1609459200&to=1609459300";
            ContentResponse response = httpClient.GET(url);

            assertEquals(200, response.getStatus());
            verify(repository).queryCandles(symbol, 60000, 1609459200000L, 1609459300000L);
        }
    }
}
