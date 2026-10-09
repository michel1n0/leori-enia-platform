package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.governance.domain.AISystemId;
import com.leori.enia.registry.application.AssociateDatasetWithAISystemCommand;
import com.leori.enia.registry.application.AssociateDatasetWithAISystemUseCase;
import com.leori.enia.registry.application.GetDatasetsByAISystemUseCase;
import com.leori.enia.registry.domain.DatasetId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-systems/{aiSystemId}/datasets")
public class AISystemDatasetController {

    private final AssociateDatasetWithAISystemUseCase associateDataset;
    private final GetDatasetsByAISystemUseCase getDatasets;

    public AISystemDatasetController(
            AssociateDatasetWithAISystemUseCase associateDataset,
            GetDatasetsByAISystemUseCase getDatasets
    ) {
        this.associateDataset = associateDataset;
        this.getDatasets = getDatasets;
    }

    @PostMapping("/{datasetId}")
    public ResponseEntity<AISystemDatasetResponse> associate(
            @PathVariable("aiSystemId") UUID aiSystemId,
            @PathVariable("datasetId") UUID datasetId
    ) {
        var association = associateDataset.execute(new AssociateDatasetWithAISystemCommand(
                new AISystemId(aiSystemId),
                new DatasetId(datasetId)
        ));
        URI location = URI.create("/api/v1/ai-systems/" + aiSystemId + "/datasets/" + datasetId);
        return ResponseEntity.created(location).body(AISystemDatasetResponse.from(association));
    }

    @GetMapping
    public ResponseEntity<List<DatasetResponse>> list(@PathVariable("aiSystemId") UUID aiSystemId) {
        List<DatasetResponse> datasets = getDatasets.execute(new AISystemId(aiSystemId)).stream()
                .map(DatasetResponse::from)
                .toList();
        return ResponseEntity.ok(datasets);
    }
}
