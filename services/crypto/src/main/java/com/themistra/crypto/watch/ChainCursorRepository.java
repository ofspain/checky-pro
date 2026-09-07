package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChainCursorRepository extends JpaRepository<ChainCursor, Long> {
}
