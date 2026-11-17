package com.fabric.fabricaiadvisor;

import com.fabric.fabricaiadvisor.controller.FabricAdviceController;
import com.fabric.fabricaiadvisor.controller.FabricAiController;
import com.fabric.fabricaiadvisor.service.FabricAdviceService;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FabricAiControllerTest {

    @Test
    void bothEndpointsUseTheSameService() {
        FabricAdviceService service = mock(FabricAdviceService.class);

        FabricAdviceController primary =
                new FabricAdviceController(service);
        FabricAiController legacy =
                new FabricAiController(service);

        String query = "Sơ mi dưới 200000 đồng/m";
        Map<String, Object> preferences = Map.of("language", "vi");

        Map<String, Object> result = Map.of(
                "status", "no_results",
                "recommendations", List.of(),
                "explanation", "Chưa tìm thấy vải phù hợp."
        );

        when(service.advise(query, preferences)).thenReturn(result);

        var a = primary.advise(Map.of(
                "query", query,
                "userPreferences", preferences
        ));

        var b = legacy.ask(Map.of(
                "question", query,
                "userPreferences", preferences
        ));

        assertEquals(result, a.getBody());
        assertEquals(result, b.getBody());

        verify(service, times(2)).advise(query, preferences);
    }

    @Test
    void invalidRequestDoesNotInvokeService() {
        FabricAdviceService service = mock(FabricAdviceService.class);
        FabricAiController controller = new FabricAiController(service);

        assertEquals(
                400,
                controller.ask(null).getStatusCode().value()
        );

        verifyNoInteractions(service);
    }
}