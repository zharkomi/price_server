package com.price.query.controller;

import com.price.query.service.HistoryService;
import com.price.query.storage.Candle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HistoryService historyService;

    @Test
    void testGetHistory() throws Exception {
        Candle candle = new Candle("BTCUSDT", 60000, 1672531200000L, 40000, 41000, 39000, 40500, 100);
        List<Candle> candles = Collections.singletonList(candle);

        when(historyService.getCandles(anyString(), anyString(), anyLong(), anyLong())).thenReturn(candles);

        mockMvc.perform(get("/history")
                        .param("symbol", "BTCUSDT")
                        .param("interval", "1m")
                        .param("from", "1672531200")
                        .param("to", "1672534800"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetHistoryBadRequest() throws Exception {
        mockMvc.perform(get("/history")
                        .param("symbol", "BTCUSDT")
                        .param("interval", "1m")
                        .param("from", "1672531200"))
                .andExpect(status().isBadRequest());
    }
}
