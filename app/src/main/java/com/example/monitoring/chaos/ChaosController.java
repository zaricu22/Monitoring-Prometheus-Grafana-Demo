package com.example.monitoring.chaos;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chaos")
public class ChaosController {

    private final ChaosService chaos;

    public ChaosController(ChaosService chaos) {
        this.chaos = chaos;
    }

    @GetMapping
    public ChaosSettings get() {
        return chaos.get();
    }

    @PutMapping
    public ChaosSettings set(@Valid @RequestBody ChaosSettings settings) {
        chaos.set(settings);
        return settings;
    }

    @DeleteMapping
    public ChaosSettings reset() {
        chaos.set(ChaosSettings.NONE);
        return ChaosSettings.NONE;
    }
}
