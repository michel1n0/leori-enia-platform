package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.application.GetDatasetUseCase;
import com.leori.enia.registry.application.RegisterDatasetCommand;
import com.leori.enia.registry.application.RegisterDatasetUseCase;
import com.leori.enia.registry.domain.DatasetId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final RegisterDatasetUseCase registerDataset;
    private final GetDatasetUseCase getDataset;

    public DatasetController(RegisterDatasetUseCase registerDataset, GetDatasetUseCase getDataset) {
        this.registerDataset = registerDataset;
        this.getDataset = getDataset;
    }

    @PostMapping
    public ResponseEntity<DatasetResponse> register(@Valid @RequestBody RegisterDatasetRequest request) {
        var registered = registerDataset.execute(new RegisterDatasetCommand(
                request.name(),
                request.description()
        ));
        URI location = URI.create("/api/v1/datasets/" + registered.id().value());
        return ResponseEntity.created(location).body(DatasetResponse.from(registered));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetResponse> get(@PathVariable UUID id) {
        var dataset = getDataset.execute(new DatasetId(id));
        return ResponseEntity.ok(DatasetResponse.from(dataset));
    }
}
