package com.proteinpro.protein.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "external-protein-api", url = "${protein.api.base-url}")
public interface ExternalProteinApiClient {
    @GetMapping("/proteindata")
    List<Map<String, Object>> getProteinData(@RequestParam Map<String, String> filters);
}
