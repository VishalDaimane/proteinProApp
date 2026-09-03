package com.proteinpro.protein.service;

import com.proteinpro.protein.client.ExternalProteinApiClient;
import com.proteinpro.protein.web.ApiException;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProteinService {
    private final ExternalProteinApiClient externalApiClient;

    public ProteinService(ExternalProteinApiClient externalApiClient) {
        this.externalApiClient = externalApiClient;
    }

    public List<Map<String, Object>> find(Map<String, String> filters) {
        try {
            return externalApiClient.getProteinData(filters);
        } catch (FeignException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The configured external Protein API is unavailable");
        }
    }
}
