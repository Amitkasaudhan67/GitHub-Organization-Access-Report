package com.github.accessreport.controller;

import com.github.accessreport.dto.AccessReportResponse;
import com.github.accessreport.service.GithubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** REST endpoint for the aggregated GitHub access report. */
@RestController
@RequestMapping("/api")
public class AccessReportController {

    private final GithubService githubService;

    public AccessReportController(GithubService githubService) {
        this.githubService = githubService;
    }

    @Operation(summary = "Get the configured organization's repository access report")
    @ApiResponse(responseCode = "200", description = "The access report was generated or returned from cache")
    @GetMapping("/report")
    public Mono<ResponseEntity<List<AccessReportResponse>>> getReport() {
        return githubService.getAccessReport().map(ResponseEntity::ok);
    }
}
