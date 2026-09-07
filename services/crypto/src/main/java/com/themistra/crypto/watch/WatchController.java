package com.themistra.crypto.watch;

import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import com.themistra.crypto.watch.dto.RegisterWatchResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** {@code POST}/{@code DELETE /internal/v1/watches} (R18/R19) - VERBATIM shape, `design.md` §4c.
 * Scope enforcement ({@code internal.crypto:write}) is already fully handled by {@code
 * ResourceServerConfig} (T03); this controller adds no security logic of its own. */
@RestController
@RequestMapping("/internal/v1/watches")
public class WatchController {

    private final WatchService watchService;

    public WatchController(WatchService watchService) {
        this.watchService = watchService;
    }

    @PostMapping
    public ResponseEntity<RegisterWatchResponse> register(@Valid @RequestBody RegisterWatchRequest request) {
        Watch watch = watchService.register(request);
        return ResponseEntity.ok(new RegisterWatchResponse(watch.watchId(), watch.status().name()));
    }

    @DeleteMapping("/{watchId}")
    public ResponseEntity<Void> unregister(@PathVariable UUID watchId) {
        watchService.unregister(watchId);
        return ResponseEntity.noContent().build();
    }
}
