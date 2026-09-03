package com.proteinpro.protein.service;

import com.proteinpro.protein.client.ExternalProteinApiClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProteinServiceTest {
    @Test
    void forwardsCaseSensitiveFiltersToConfiguredApi() {
        ExternalProteinApiClient client = mock(ExternalProteinApiClient.class);
        Map<String, String> filters = Map.of("source", "whey", "vegetarian", "true");
        List<Map<String, Object>> expected = List.of(Map.of("id", 1, "source", "whey"));
        when(client.getProteinData(filters)).thenReturn(expected);

        assertThat(new ProteinService(client).find(filters)).isEqualTo(expected);
        verify(client).getProteinData(filters);
    }
}
