package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.application.RegisterDatasetCommand;
import com.leori.enia.registry.application.RegisterDatasetUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final RegisterDatasetUseCase registerDataset;

    public DatasetController(RegisterDatasetUseCase registerDataset) {
        this.registerDataset = registerDataset;
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
}
