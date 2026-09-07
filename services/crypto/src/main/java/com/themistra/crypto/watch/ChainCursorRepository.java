package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ChainCursorRepository extends JpaRepository<ChainCursor, Long> {

    /** T16: {@code Watcher} looks up its own watch's cursor to advance {@code lastBlock} forward. */
    Optional<ChainCursor> findByWatchId(UUID watchId);
}
