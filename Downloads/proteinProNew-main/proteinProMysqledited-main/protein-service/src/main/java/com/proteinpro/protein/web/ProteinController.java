package com.proteinpro.protein.web;

import com.proteinpro.protein.service.ProteinService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proteins")
public class ProteinController {
    private final ProteinService service;

    public ProteinController(ProteinService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> find(@RequestParam Map<String, String> filters) {
        return service.find(filters);
    }
}
