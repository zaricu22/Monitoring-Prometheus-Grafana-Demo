package com.example.monitoring.chaos;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 
  * ChaosService is used to simulate failures and latency for testing purposes. 
  * Who calls it:
  *  - k6: about 7% of its traffic (k6/load.js, http.get(${BASE_URL}/api/orders/stats)).
  *  - You: manually, e.g. curl localhost:8080/api/orders/stats to see the order flow working (orders moving to SHIPPED).
*/
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
